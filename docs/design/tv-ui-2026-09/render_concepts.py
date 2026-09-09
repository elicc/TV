"""Generate self-contained, editable TV concept SVGs. Python standard library only."""
from pathlib import Path
from html import escape
import base64

ROOT = Path(__file__).resolve().parent
THEMES = {
    "a": dict(bg="#101114", panel="#202228", text="#F5F5F7", muted="#AFB2BA", accent="#D7B889"),
    "b": dict(bg="#0B1220", panel="#172338", text="#F3F6FC", muted="#A8B6CB", accent="#79B8FF"),
    "c": dict(bg="#191816", panel="#2B2925", text="#F6F1E7", muted="#C0B8AC", accent="#E8C58A"),
}


def rect(x, y, w, h, fill, radius=16, stroke=None, sw=3):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{fill}"' + (f' stroke="{stroke}" stroke-width="{sw}"' if stroke else '') + '/>'


def txt(x, y, s, size=32, color="#F5F5F7", weight=400, extra=""):
    return f'<text x="{x}" y="{y}" font-size="{size}" fill="{color}" font-weight="{weight}" {extra}>{escape(s)}</text>'


def art(x, y, w, h, variant=0):
    source = ROOT / 'artwork' / ('hero.jpg' if w > 700 and h > 300 else f'film-{variant % 6}.jpg')
    if source.exists():
        data = base64.b64encode(source.read_bytes()).decode('ascii')
        position = 'xMidYMin' if source.name == 'hero.jpg' else ['xMidYMin','xMidYMid','xMidYMid','xMidYMax','xMidYMid','xMidYMid'][variant % 6]
        return f'<image x="{x}" y="{y}" width="{w}" height="{h}" href="data:image/jpeg;base64,{data}" preserveAspectRatio="{position} slice"/>'
    palettes = [('#172D3E', '#78978F', '#E7CB97'), ('#222139', '#646B97', '#E4AA84'), ('#443128', '#A67C54', '#EDCC91'), ('#142E2D', '#5C8073', '#C3D1AC'), ('#252D43', '#777A99', '#D6B7A3')]
    dark, mid, light = palettes[variant % len(palettes)]
    uid = f'p{int(x)}{int(y)}{variant}'
    s = f'<svg x="{x}" y="{y}" width="{w}" height="{h}" viewBox="0 0 600 400" preserveAspectRatio="xMidYMid slice"><defs><linearGradient id="{uid}" x2="0.3" y2="1"><stop stop-color="{mid}"/><stop offset="1" stop-color="{dark}"/></linearGradient></defs><rect width="600" height="400" fill="url(#{uid})"/>'
    s += f'<circle cx="430" cy="130" r="68" fill="{light}" opacity=".82"/><circle cx="430" cy="130" r="88" fill="none" stroke="{light}" opacity=".12"/>'
    if variant % 5 == 1:
        s += '<ellipse cx="430" cy="130" rx="150" ry="27" fill="none" stroke="#D6C6BD" stroke-width="8" transform="rotate(-28 430 130)" opacity=".7"/>'
    s += f'<path d="M0 295L120 170 210 265 326 205 430 294 550 213 600 240V400H0Z" fill="{dark}" opacity=".52"/><path d="M0 335L120 299 240 338 359 270 440 285 520 250 600 316V400H0Z" fill="{dark}"/>'
    for k in range(9):
        s += f'<path d="M{20+k*43} {330+k*7}h{160-k*10}" stroke="{light}" opacity=".09"/>'
    if variant % 5 == 0:
        s += f'<path d="M503 251V176h12v75z" fill="{light}"/><path d="M499 176l10-12 10 12z" fill="{dark}"/><path d="M503 180L302 232 503 187z" fill="{light}" opacity=".12"/>'
    elif variant % 5 == 4:
        for k in range(8):
            s += rect(50+k*53, 210-(k % 3)*25, 30, 160, dark, 0)
            s += rect(61+k*53, 230-(k % 3)*25, 5, 7, light, 0)
    else:
        s += '<path d="M282 339l4-38h6l5 38m-10-38v-14" fill="none" stroke="#101A22" stroke-width="5"/><circle cx="288" cy="281" r="6" fill="#101A22"/>'
    return s + '</svg>'


def button(x, y, w, label, t, focused=False):
    s = rect(x, y, w, 72, t['accent'] if focused else t['panel'], 12)
    if focused:
        s += rect(x-7, y-7, w+14, 86, 'none', 18, t['text'], 4)
    return s + txt(x+28, y+47, label, 30, t['bg'] if focused else t['text'], 600)


def card(x, y, w, h, title, meta, t, variant=0, focused=False, progress=False):
    uid = f'c{x}{y}'
    s = f'<defs><clipPath id="{uid}">{rect(x,y,w,h,t["panel"],14)}</clipPath></defs><g clip-path="url(#{uid})">' + art(x,y,w,h,variant) + '</g>'
    if progress:
        s += rect(x+14,y+h-12,w-28,5,'#44505A',2) + rect(x+14,y+h-12,(w-28)*.57,5,t['accent'],2)
    if focused:
        s += rect(x-7,y-7,w+14,h+14,'none',20,t['text'],4)
        s += rect(x+14,y+14,74,40,t['text'],8) + txt(x+28,y+43,'续播',23,t['bg'],600)
    return s + txt(x,y+h+45,title,34,t['text'],600) + txt(x,y+h+83,meta,27,t['muted'])


def begin(t, title):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="1920" height="1080" viewBox="0 0 1920 1080" role="img" aria-labelledby="title"><title id="title">{title} — 静态设计提案，虚构示例内容</title><g font-family="PingFang SC, Noto Sans CJK SC, Microsoft YaHei, sans-serif">' + rect(0,0,1920,1080,t['bg'],0)


def footer(t, label):
    return txt(96,1030,label,25,t['muted']) + txt(1415,1030,'设计提案 · 虚构内容示例',24,t['muted']) + '</g></svg>'


def topnav(t, active='首页'):
    s = txt(96,104,'影 視',42,t['text'],600)
    for i,label in enumerate(['首页','点播','直播','收藏','搜索']):
        x = 340+i*150
        s += txt(x,101,label,32,t['text'] if label==active else t['muted'],600 if label==active else 400)
        if label==active: s += rect(x,119,60,4,t['accent'],2)
    return s + txt(1520,101,'设置',30,t['muted']) + txt(1720,101,'20:45',30,t['muted'])


def scheme_a():
    t = THEMES['a']; s = begin(t,'A 曜石影院') + topnav(t)
    s += art(600,156,1224,460,0)
    s += '<defs><linearGradient id="fadeA"><stop stop-color="#101114"/><stop offset="1" stop-color="#101114" stop-opacity="0"/></linearGradient></defs>'
    s += rect(570,156,650,460,'url(#fadeA)',0)
    s += txt(96,211,'今晚，从一部好电影开始',28,t['accent'],500)
    s += txt(96,323,'潮 汐 之 间',76,t['text'],600)
    s += txt(99,379,'电影  ·  剧情  ·  2026',30,t['muted'])
    s += txt(99,433,'循着海岸线，寻找一封迟到的信。',30,t['text'])
    s += button(103,486,246,'▶  查看影片',t,True) + button(378,486,204,'＋  收藏',t)
    s += txt(96,659,'继续观看',40,t['text'],600) + txt(1640,658,'全部记录  ›',28,t['muted'])
    for i,(a,b) in enumerate([('月面来信','第 3 集 · 看到 24:18'),('漫长的夏日','电影 · 看到 48:06'),('林间慢行','第 2 集 · 看到 12:32')]):
        s += card(96+i*584,701,560,210,a,b,t,i+1,progress=True)
    return s + footer(t,'A / 曜石影院     ·     沉浸大图 / 顶部导航')


def scheme_b():
    t=THEMES['b']; s=begin(t,'B 午夜蓝 内容优先')
    s += rect(0,0,274,1080,'#0E1829',0) + txt(78,104,'影 視',40,t['text'],600)
    for i,label in enumerate(['首页','点播','直播','搜索','收藏','更多']):
        y=202+i*90
        if i==0:
            s += rect(54,y-44,170,64,t['panel'],12) + rect(54,y-27,4,30,t['accent'],2)
        s += txt(91,y,label,32,t['text'] if i==0 else t['muted'],600 if i==0 else 400)
    s += txt(91,911,'设置',30,t['muted'])
    s += txt(328,103,'接着看，或发现下一部',44,t['text'],600) + txt(1740,100,'20:45',28,t['muted'])
    s += txt(328,150,'当前内容源  /  示例片库',27,t['muted'])
    s += txt(328,229,'继续观看',36,t['text'],600) + txt(1652,227,'全部记录  ›',27,t['muted'])
    for i,(a,b) in enumerate([('潮汐之间','电影 · 看到 38:12'),('月面来信','第 3 集 · 看到 24:18'),('漫长的夏日','电影 · 看到 48:06')]):
        s += card(328+i*506,269,474,205,a,b,t,i,focused=i==0,progress=True)
    s += txt(328,626,'来自当前内容源',36,t['text'],600) + txt(1638,624,'浏览全部  ›',27,t['muted'])
    for i,title in enumerate(['林间慢行','远山回响','夜航手记','月面来信','漫长的夏日']):
        s += card(328+i*304,666,270,238,title,'剧集' if i in (0,3) else '电影',t,[3,4,5,1,2][i])
    s += txt(328,1030,'B / 午夜蓝     ·     确定：继续播放   长按：更多',25,t['muted']) + txt(1460,1030,'静态提案 · 虚构内容',24,t['muted'])
    return s + '</g></svg>'


def scheme_c():
    t=THEMES['c']; s=begin(t,'C 暖墨 家庭易用') + topnav(t)
    s += txt(96,211,'打开电视，轻松接着看',48,t['text'],600)
    s += rect(96,253,1092,340,t['panel'],20)
    s += '<defs><clipPath id="warmHero">' + rect(96,253,1092,340,t['panel'],20) + '</clipPath></defs><g clip-path="url(#warmHero)">' + art(724,253,464,340,0) + '</g>'
    s += txt(132,315,'上次看到 38:12',30,t['muted']) + txt(132,397,'潮汐之间',58,t['text'],600)
    s += button(139,467,274,'▶  继续观看',t,True) + txt(457,513,'电影',30,t['muted'])
    s += rect(1220,253,604,154,t['panel'],20) + txt(1260,316,'直播频道',38,t['text'],600) + txt(1260,365,'打开频道列表  →',30,t['muted'])
    s += rect(1220,439,604,154,t['panel'],20) + txt(1260,502,'搜索影片',38,t['text'],600) + txt(1260,551,'语音或遥控器输入  →',30,t['muted'])
    s += txt(96,665,'我的收藏',40,t['text'],600) + txt(1643,664,'查看全部  ›',29,t['muted'])
    for i,title in enumerate(['月面来信','漫长的夏日','林间慢行','远山回响']):
        s += card(96+i*440,709,408,200,title,'剧集' if i%2==0 else '电影',t,i+1)
    return s + footer(t,'C / 暖墨     ·     大字 / 少选项 / 无自动轮播')


def detail_b():
    t=THEMES['b']; s=begin(t,'B 详情及选集延展')
    s += txt(96,99,'‹  返回片库',30,t['muted']) + txt(1565,99,'当前内容源',28,t['muted'])
    s += art(1030,147,794,400,0)
    s += txt(96,206,'影片详情',28,t['accent']) + txt(96,301,'潮汐之间',70,t['text'],600)
    s += txt(99,362,'2026  ·  剧情  ·  电影',32,t['muted'])
    s += txt(99,421,'循着海岸线，寻找一封迟到的信。',32,t['text'])
    s += txt(99,465,'一次重逢，让沉默的往事有了回声。',32,t['text'])
    s += button(103,521,336,'▶  继续 38:12',t,True) + button(468,521,192,'＋ 收藏',t) + button(684,521,232,'更多信息',t)
    s += txt(96,686,'播放线路',34,t['text'],600) + rect(304,643,292,66,t['panel'],12) + txt(330,688,'✓ 当前线路',30,t['accent']) + txt(638,688,'切换线路  ›',30,t['muted'])
    s += txt(96,777,'选集',36,t['text'],600) + txt(1608,777,'共 1 个视频',28,t['muted'])
    s += rect(96,816,360,92,t['panel'],12) + txt(124,873,'✓ 正片 · 上次观看',32,t['accent'])
    s += txt(96,956,'返回保留影片位置；没有历史时，主按钮显示“播放”。',28,t['muted'])
    return s + footer(t,'B / 详情页延展     ·     主操作优先 / 技术选项折叠')


if __name__ == '__main__':
    names = [('a-obsidian',scheme_a),('b-midnight',scheme_b),('c-warm',scheme_c),('b-detail',detail_b)]
    for name, fn in names:
        (ROOT / f'{name}.svg').write_text(fn(),encoding='utf-8')
    blocks = []
    for name,title,body in [
        ('a-obsidian','A · 曜石影院','大图、留白、顶部导航。适合以点播发现为主；依赖优质横图，缺图时必须降级。'),
        ('b-midnight','B · 午夜蓝｜推荐','侧边稳定导航，先继续观看，再浏览片库。更贴合外部内容源、点播与直播并存的定位。'),
        ('c-warm','C · 暖墨','更大的文字、更少的首屏选项。适合远距离和家庭共用；浏览密度较低。'),
        ('b-detail','B · 详情延展','先看清影片和续播位置，再操作线路与选集。技术设置不与播放争夺注意力。')]:
        blocks.append(f'<section id="{name}"><h2>{title}</h2><p>{body}</p><a href="{name}.svg" target="_blank"><img src="{name}.svg" alt="{title} 静态界面设计图" width="1920" height="1080"></a><p><a href="{name}.svg">打开可编辑 SVG</a> · <a href="{name}.png">打开 PNG</a></p></section>')
    (ROOT/'index.html').write_text('''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>影视 TV · 设计候选方案</title><style>*{box-sizing:border-box}body{margin:0;background:#080d16;color:#f3f6fc;font:18px/1.7 system-ui,"PingFang SC",sans-serif}main{max-width:1500px;margin:auto;padding:48px 28px}h1{font-size:40px;line-height:1.25}h2{font-size:30px}p{color:#b4c1d5}a{color:#91c2ff}nav{display:flex;gap:24px;flex-wrap:wrap}section{margin-top:64px;border-top:1px solid #29364a;padding-top:24px}img{display:block;width:100%;height:auto;border:1px solid #29364a;border-radius:12px}a:focus-visible{outline:3px solid #fff;outline-offset:5px}small{color:#b4c1d5}</style><main><small>DESIGN EXPLORATION / 2026.09 / 未批准实施</small><h1>让内容成为主角，<br>让遥控器操作更确定。</h1><p>三套方向，不只是换颜色。以下为 1920 × 1080 静态概念图，影片、进度与海报均为虚构示例。界面文字与布局以可编辑 SVG 绘制；电影氛围图与示例海报由 GPT Image 生成。</p><nav><a href="#a-obsidian">A 曜石影院</a><a href="#b-midnight">B 午夜蓝</a><a href="#c-warm">C 暖墨</a><a href="#b-detail">详情延展</a><a href="audit.md">完整审查报告</a></nav>''' + ''.join(blocks) + '<section><h2>审查边界</h2><p>已查看当前模拟器首页与仓库 TV 布局/交互代码。未验证安装包与当前源码完全一致，未完成播放、直播、焦点回归和远距离真机测试。所有提案均待用户选择，未修改 Android 应用。</p></section></main></html>',encoding='utf-8')
    print('Created four SVG concepts and index.html')
