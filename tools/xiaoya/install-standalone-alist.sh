#!/usr/bin/env bash
# Install an isolated Xiaoya AList instance without touching existing services.
set -Eeuo pipefail
IFS=$'\n\t'

CONTAINER_NAME="${XIAOYA_CONTAINER_NAME:-xiaoya-alist-test}"
CONFIG_DIR="${XIAOYA_CONFIG_DIR:-/home/elicc/tvbox/xiaoya-alist-standalone}"
ALIST_PORT="${XIAOYA_ALIST_PORT:-5678}"
WEBDAV_PORT="${XIAOYA_WEBDAV_PORT:-2345}"
EXTRA_PORT_1="${XIAOYA_EXTRA_PORT_1:-2346}"
EXTRA_PORT_2="${XIAOYA_EXTRA_PORT_2:-2347}"
PROXY_URL="${XIAOYA_PROXY:-http://192.168.1.111:7890}"
FOLDER_ID="654633b6751d602006e5414ba39be435de28326a"
IMAGE="xiaoyaliu/alist:latest"
MIRROR_IMAGE="docker.m.daocloud.io/xiaoyaliu/alist:latest"

die() { printf '\033[31m错误：%s\033[0m\n' "$*" >&2; exit 1; }
info() { printf '\033[32m%s\033[0m\n' "$*"; }
warn() { printf '\033[33m%s\033[0m\n' "$*" >&2; }

TOKEN_RESPONSE=""
TOKEN_REQUEST=""
cleanup_tmp() {
    [[ -n "$TOKEN_RESPONSE" ]] && rm -f "$TOKEN_RESPONSE"
    [[ -n "$TOKEN_REQUEST" ]] && rm -f "$TOKEN_REQUEST"
}
trap cleanup_tmp EXIT

if [[ "$EUID" -ne 0 ]]; then
    exec sudo -E bash "$0" "$@"
fi

command -v docker >/dev/null 2>&1 || die "未找到 docker"
command -v curl >/dev/null 2>&1 || die "未找到 curl"
command -v python3 >/dev/null 2>&1 || die "未找到 python3（用于解析认证响应）"

if docker ps --format '{{.Names}}' | grep -Fxq "$CONTAINER_NAME"; then
    die "容器 $CONTAINER_NAME 已在运行；请设置 XIAOYA_CONTAINER_NAME 使用其他名称"
fi

for port in "$ALIST_PORT" "$WEBDAV_PORT" "$EXTRA_PORT_1" "$EXTRA_PORT_2"; do
    if ss -ltn 2>/dev/null | awk '{print $4}' | grep -Eq ":${port}$"; then
        die "宿主机端口 $port 已被占用"
    fi
done

mkdir -p "$CONFIG_DIR/data" "$CONFIG_DIR/watch"
chmod 700 "$CONFIG_DIR"

printf '\n标准 AList 认证配置（不要输入 TV Token）\n'
while :; do
    read -r -s -p '阿里云盘标准 Refresh Token（32位）: ' REFRESH_TOKEN
    printf '\n'
    [[ "${#REFRESH_TOKEN}" -eq 32 ]] || { warn 'Refresh Token 长度不是32位，请重试'; continue; }

    TOKEN_REQUEST=$(mktemp)
    TOKEN_RESPONSE=$(mktemp)
    chmod 600 "$TOKEN_REQUEST" "$TOKEN_RESPONSE"
    python3 - "$REFRESH_TOKEN" >"$TOKEN_REQUEST" <<'PY'
import json
import sys
print(json.dumps({"grant_type": "refresh_token", "refresh_token": sys.argv[1]}))
PY
    if ! curl --noproxy '' --proxy "$PROXY_URL" --connect-timeout 10 --max-time 30 \
        -fsS https://auth.aliyundrive.com/v2/account/token \
        -H 'Content-Type: application/json' --data-binary "@$TOKEN_REQUEST" \
        -o "$TOKEN_RESPONSE"; then
        warn '无法访问阿里认证接口，请检查网络或代理'
        rm -f "$TOKEN_REQUEST" "$TOKEN_RESPONSE"
        TOKEN_REQUEST=""; TOKEN_RESPONSE=""
        continue
    fi

    if ! NEW_REFRESH_TOKEN=$(python3 - "$TOKEN_RESPONSE" <<'PY'
import json
import sys
with open(sys.argv[1], encoding='utf-8') as f:
    data = json.load(f)
if not data.get('access_token'):
    print(data.get('code') or data.get('message') or 'Refresh Token 无效', file=sys.stderr)
    raise SystemExit(1)
print(data.get('refresh_token') or '')
PY
    ); then
        warn 'Refresh Token 无效，必须使用标准 AList Refresh Token'
        rm -f "$TOKEN_REQUEST" "$TOKEN_RESPONSE"
        TOKEN_REQUEST=""; TOKEN_RESPONSE=""
        continue
    fi
    [[ -n "$NEW_REFRESH_TOKEN" ]] || NEW_REFRESH_TOKEN="$REFRESH_TOKEN"
    break
done

while :; do
    read -r -s -p '标准 Open Token（280或335位，不要输入 TV Token）: ' OPEN_TOKEN
    printf '\n'
    [[ "${#OPEN_TOKEN}" -eq 280 || "${#OPEN_TOKEN}" -eq 335 ]] && break
    warn 'Open Token 长度必须为280或335位，请重试'
done

printf '%s\n' "$NEW_REFRESH_TOKEN" >"$CONFIG_DIR/mytoken.txt"
printf '%s\n' "$OPEN_TOKEN" >"$CONFIG_DIR/myopentoken.txt"
printf '%s\n' "$FOLDER_ID" >"$CONFIG_DIR/temp_transfer_folder_id.txt"
rm -f "$CONFIG_DIR/open_tv_token_url.txt"
chmod 600 "$CONFIG_DIR"/*.txt

printf '\n是否启用 AList 登录保护？[Y/n]（推荐启用）: '
read -r ENABLE_LOGIN
if [[ -z "$ENABLE_LOGIN" || "$ENABLE_LOGIN" =~ ^[Yy]$ ]]; then
    while :; do
        read -r -s -p 'AList dav 密码: ' ALIST_PASSWORD
        printf '\n'
        read -r -s -p '再次输入密码: ' ALIST_PASSWORD_CONFIRM
        printf '\n'
        [[ -n "$ALIST_PASSWORD" && "$ALIST_PASSWORD" == "$ALIST_PASSWORD_CONFIRM" ]] && break
        warn '密码为空或两次输入不一致，请重试'
    done
    : >"$CONFIG_DIR/guestlogin.txt"
    printf '%s\n' "$ALIST_PASSWORD" >"$CONFIG_DIR/guestpass.txt"
    chmod 600 "$CONFIG_DIR/guestlogin.txt" "$CONFIG_DIR/guestpass.txt"
else
    rm -f "$CONFIG_DIR/guestlogin.txt" "$CONFIG_DIR/guestpass.txt"
fi

info '拉取官方 Xiaoya AList 镜像……'
if ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
    docker pull "$MIRROR_IMAGE" >/dev/null
    docker tag "$MIRROR_IMAGE" "$IMAGE"
fi

if docker ps -a --format '{{.Names}}' | grep -Fxq "$CONTAINER_NAME"; then
    warn "发现已存在的测试容器 $CONTAINER_NAME"
    read -r -p '删除并重新创建该测试容器？[y/N]: ' RECREATE
    [[ "$RECREATE" =~ ^[Yy]$ ]] || die '已取消，现有测试容器未修改'
    docker rm -f "$CONTAINER_NAME" >/dev/null
fi

info "启动 $CONTAINER_NAME（不会修改 xiaoya-tvbox）……"
docker run -d \
    --name "$CONTAINER_NAME" \
    --privileged \
    --restart unless-stopped \
    -p "$ALIST_PORT:80" \
    -p "$WEBDAV_PORT:2345" \
    -p "$EXTRA_PORT_1:2346" \
    -p "$EXTRA_PORT_2:2347" \
    -e "HTTP_PROXY=$PROXY_URL" \
    -e "HTTPS_PROXY=$PROXY_URL" \
    -e 'NO_PROXY=localhost,127.0.0.1,::1,192.168.0.0/16,172.16.0.0/12,10.0.0.0/8,.aliyundrive.com,.alipan.com,.aliyuncs.com' \
    -e 'no_proxy=localhost,127.0.0.1,::1,192.168.0.0/16,172.16.0.0/12,10.0.0.0/8,.aliyundrive.com,.alipan.com,.aliyuncs.com' \
    -v "$CONFIG_DIR:/data" \
    -v "$CONFIG_DIR/data:/www/data" \
    -v "$CONFIG_DIR/watch:/index/watch" \
    "$IMAGE" >/dev/null

info '等待 AList 初始化（最多5分钟）……'
deadline=$((SECONDS + 300))
status='000'
while (( SECONDS < deadline )); do
    status=$(curl --noproxy '*' -sS -o /dev/null -w '%{http_code}' \
        --connect-timeout 3 --max-time 5 "http://127.0.0.1:$ALIST_PORT/api/public/settings" || true)
    [[ "$status" == '200' || "$status" == '401' || "$status" == '403' ]] && break
    sleep 3
done

if [[ "$status" != '200' && "$status" != '401' && "$status" != '403' ]]; then
    warn 'AList API 尚未就绪，最近日志如下：'
    docker logs --tail 80 "$CONTAINER_NAME" || true
    die '启动失败，请检查上述日志'
fi

storage_count=$(docker exec "$CONTAINER_NAME" sh -c \
    "sqlite3 /opt/alist/data/data.db 'select count(*) from x_storages;' 2>/dev/null || echo 0" | tail -n1)
[[ "$storage_count" =~ ^[0-9]+$ ]] || storage_count=0

info '安装完成。'
host_ip=$(hostname -I 2>/dev/null | awk '{print $1}')
[[ -n "$host_ip" ]] || host_ip='服务器IP'
printf '\nAList 地址：      http://%s:%s/\n' "$host_ip" "$ALIST_PORT"
printf 'TVBox 配置地址：  http://%s:%s/tvbox/my_ext_jar.json\n' "$host_ip" "$ALIST_PORT"
printf '配置目录：        %s\n' "$CONFIG_DIR"
printf '存储数量：        %s\n' "$storage_count"
printf '\n请在浏览器中测试目标影片；不要直接修改原 xiaoya-tvbox。\n'
printf '停止测试服务：    docker stop %s\n' "$CONTAINER_NAME"
printf '查看日志：        docker logs -f %s\n' "$CONTAINER_NAME"
