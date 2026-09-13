#!/usr/bin/env python3
"""List and fuzzy-match FongMi TV Stitch page references without dependencies."""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path


ALIASES = {
    "collect_history": ("收藏", "历史", "观看历史", "favorites", "keep"),
    "detail": ("详情", "详情页", "detail"),
    "global_toast": ("提示", "通知", "toast", "全局提示"),
    "home_animated": ("首页", "首页动效", "首页动画", "微交互", "home animation"),
    "home_empty_live": ("首页", "直播空配置", "未配置直播", "empty live"),
    "home_empty_source": ("首页", "首页空源", "未配置源", "首次启动", "empty source"),
    "home_empty_vod": ("首页", "点播空配置", "未配置点播", "empty vod"),
    "home_empty_webdav": ("首页", "webdav空配置", "网盘空态", "empty webdav"),
    "home_focus": ("首页焦点", "home focus", "首页"),
    "live_epg": ("直播", "节目单", "频道", "epg", "live"),
    "player": ("播放器", "播放页", "osd", "player"),
    "search_keyboard": ("搜索", "键盘", "软键盘", "search"),
    "settings_api_detail_vod": ("设置", "api详情", "点播api详情", "vod api详情", "api detail"),
    "settings_api_full": ("设置", "api", "点播源", "api设置", "点播源设置", "vod设置", "api full"),
    "settings_iptv_full": ("设置", "iptv", "直播源", "iptv设置", "直播源设置", "iptv full"),
    "settings_sniffer_full": ("设置", "嗅探", "sniffer", "嗅探设置", "解析设置"),
    "settings": ("设置", "系统设置", "settings"),
    "tools": ("工具", "更多", "tools"),
}


def normalize(value: str) -> str:
    return re.sub(r"[^0-9a-z\u4e00-\u9fff]+", "", value.lower())


def find_root(start: Path) -> Path:
    current = start.resolve()
    if current.is_file():
        current = current.parent
    for candidate in (current, *current.parents):
        if (
            (candidate / "docs/stitch_fongmi_tv_ui_design").is_dir()
            and (candidate / "docs/tv-ui-architect").is_dir()
            and (candidate / "app/src/leanback").is_dir()
        ):
            return candidate
    raise FileNotFoundError(
        "not inside a FongMi TV checkout containing both design sources and app/src/leanback"
    )


def category(name: str) -> str:
    stripped = re.sub(r"^fongmi_tv_leanback_1080p_", "", name)
    for key in sorted(ALIASES, key=len, reverse=True):
        if key in stripped:
            return key
    return stripped


def pages(root: Path) -> list[dict[str, object]]:
    base = root / "docs/stitch_fongmi_tv_ui_design"
    result = []
    for screen in sorted(base.glob("*/screen.png")):
        folder = screen.parent
        key = category(folder.name)
        result.append(
            {
                "id": folder.name,
                "category": key,
                "aliases": list(ALIASES.get(key, ())),
                "screen": str(screen.relative_to(root)),
                "html": str((folder / "code.html").relative_to(root)),
            }
        )
    return result


def score(query: str, page: dict[str, object]) -> int:
    q = normalize(query)
    if not q:
        return 0
    values = [str(page["id"]), str(page["category"]), *page["aliases"]]
    normalized = [normalize(value) for value in values]
    best = 0
    for value in normalized:
        if not value:
            continue
        if q == value:
            best = max(best, 1000 + len(value))
        elif q in value:
            best = max(best, 700 + len(q))
        elif value in q:
            best = max(best, 500 + len(value))
        else:
            tokens = [normalize(token) for token in re.split(r"[\s/_-]+", query) if normalize(token)]
            best = max(best, 50 * sum(token in value for token in tokens))
    return best


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path.cwd(), help="repo root or a path inside it")
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument("--list", action="store_true", help="list every page reference")
    action.add_argument("--match", metavar="QUERY", help="rank references matching a page description")
    parser.add_argument("--json", action="store_true", help="emit JSON")
    args = parser.parse_args()

    try:
        root = find_root(args.root)
    except FileNotFoundError as error:
        print(f"error: {error}", file=sys.stderr)
        return 2

    catalog = pages(root)
    if args.match:
        ranked = [(score(args.match, page), page) for page in catalog]
        ranked = [(rank, page) for rank, page in ranked if rank > 0]
        ranked.sort(key=lambda item: (-item[0], str(item[1]["id"])))
        catalog = [{**page, "score": rank} for rank, page in ranked]

    if args.json:
        print(json.dumps({"root": str(root), "pages": catalog}, ensure_ascii=False, indent=2))
    elif not catalog:
        print("No matching design page found.")
        return 1
    else:
        for page in catalog:
            rank = f" score={page['score']}" if "score" in page else ""
            print(f"{page['id']} [{page['category']}]{rank}")
            print(f"  screen: {page['screen']}")
            print(f"  html:   {page['html']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
