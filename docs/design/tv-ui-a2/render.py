"""A2 proposal renderer. Reuses original concept artwork; no Android changes."""
import base64
import importlib.util
import sys
from pathlib import Path

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parent
PREVIOUS = ROOT.parent / 'tv-ui-2026-09'
spec = importlib.util.spec_from_file_location('previous_concepts', PREVIOUS / 'render_concepts.py')
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)
rect, txt = base.rect, base.txt
BG, PANEL, INK, MUTED, GOLD = '#101114', '#232429', '#F5F3EF', '#B7B4AE', '#D7B889'


def picture(x, y, w, h, name='hero', radius=0, position='xMidYMid'):
    data = base64.b64encode((PREVIOUS / 'artwork' / f'{name}.jpg').read_bytes()).decode()
    clip = f'clip-{x}-{y}-{w}-{h}'
    return f'<defs><clipPath id="{clip}">{rect(x,y,w,h,BG,radius)}</clipPath></defs><image x="{x}" y="{y}" width="{w}" height="{h}" href="data:image/jpeg;base64,{data}" preserveAspectRatio="{position} slice" clip-path="url(#{clip})"/>'


def start(title):
    return base.begin({'bg': BG}, title) + '''<defs>
      <linearGradient id="shadeH"><stop stop-color="#101114"/><stop offset=".37" stop-color="#101114" stop-opacity=".96"/><stop offset=".65" stop-color="#101114" stop-opacity=".24"/><stop offset="1" stop-color="#101114" stop-opacity="0"/></linearGradient>
      <linearGradient id="shadeV" x2="0" y2="1"><stop stop-color="#101114" stop-opacity=".75"/><stop offset=".20" stop-color="#101114" stop-opacity="0"/><stop offset=".64" stop-color="#101114" stop-opacity=".04"/><stop offset="1" stop-color="#101114"/></linearGradient>
      <linearGradient id="bottom" x2="0" y2="1"><stop stop-color="#101114" stop-opacity="0"/><stop offset=".20" stop-color="#101114" stop-opacity=".94"/><stop offset="1" stop-color="#101114"/></linearGradient>
      <radialGradient id="ambient"><stop stop-color="#493B2B" stop-opacity=".7"/><stop offset="1" stop-color="#101114" stop-opacity="0"/></radialGradient>
    </defs>'''


def end(label):
    return txt(96,1034,label,24,MUTED) + txt(1460,1034,'A2 · 静态提案 / 示例内容',24,MUTED) + '</g></svg>'


def nav(active='首页'):
    s=rect(0,0,1920,144,'#101114F0',0)+txt(96,98,'影 視',42,INK,600)
    for i,label in enumerate(['首页','点播','直播','收藏','搜索']):
        x=334+i*146
        s+=txt(x,96,label,34,INK if label==active else MUTED,600 if label==active else 400)
        if label==active:s+=rect(x,115,68,4,GOLD,2)
    s+=txt(1260,95,'更多',30,MUTED)+txt(1384,95,'设置',30,MUTED)
    s+=rect(1510,52,310,64,'#202125',12)+txt(1534,94,'片源 · 示例片库 ⌄',28,INK)
    return s


def button(x,y,w,label,focus=False,primary=False):
    s=rect(x,y,w,72,GOLD if primary else PANEL,12)
    if focus:s+=rect(x-7,y-7,w+14,86,'none',18,INK,4)
    return s+txt(x+27,y+47,label,32,BG if primary else INK,600)


def resume_card(x,y,title,meta,film,progress,focus=False):
    w,h=408,230
    s=picture(x,y,w,h,f'film-{film}',14,'xMidYMin' if film==0 else 'xMidYMid')
    if focus:s+=rect(x-7,y-7,w+14,h+14,'none',20,INK,4)
    s+=rect(x+14,y+h-17,w-28,6,'#343940',3)+rect(x+14,y+h-17,(w-28)*progress,6,GOLD,3)
    return s+txt(x,y+h+42,title,34,INK,600)+txt(x,y+h+80,meta,28,MUTED)


def home(poster=False):
    s=start('A2 曜石影院 / '+('海报适配' if poster else '沉浸首页'))
    if not poster:
        s+=picture(390,60,1530,680,'hero',position='xMidYMin')
        s+=rect(0,0,1920,740,'url(#shadeH)',0)+rect(0,0,1920,760,'url(#shadeV)',0)
    else:
        s+=rect(1000,112,820,530,'url(#ambient)',0)
        s+=picture(1360,174,260,390,'film-0',14)
        s+=rect(1360,174,260,390,'none',14,'#59524A',1)
    s+=nav()
    s+=txt(96,221,'来自当前片源',28,GOLD,500)
    s+=txt(92,327,'潮汐之间',82,INK,600)
    s+=txt(98,390,'2026   ·   剧情   ·   电影',32,MUTED)
    if not poster:
        s+=txt(98,447,'沿着海岸线，寻找一封迟到的信。',32,INK)
        s+=txt(98,490,'在潮起潮落之间，与往事重逢。',32,INK)
    else:
        s+=txt(98,451,'正片',30,INK)
    s+=button(103,515,250,'查看详情  ›',True,True)+button(382,515,252,'浏览片库',False)
    s+=txt(96,659,'继续观看',38,INK,600)+txt(1640,657,'全部记录  ›',28,MUTED)
    titles=[('月面来信','第 3 集 · 24:18',1,.48),('漫长的夏日','电影 · 48:06',2,.59),('林间慢行','第 2 集 · 12:32',3,.28),('远山回响','电影 · 32:10',4,.35)]
    for i,(title,meta,film,progress) in enumerate(titles):
        s+=resume_card(96+i*440,682,title,meta,film,progress)
    # Footer belongs to the proposal frame, outside content titles.
    return s+end('只有海报时保留比例；无简介不占位。' if poster else '有合适横图时展示氛围；没有自动轮播。')


def browse():
    s=start('A2 内容浏览状态')+nav('点播')
    s+=txt(96,210,'来自当前片源',44,INK,600)+txt(1494,206,'分类 / 筛选  ›',30,MUTED)
    s+=txt(96,260,'全部影片',28,MUTED)
    for i,(title,film) in enumerate([('潮汐之间',0),('月面来信',1),('漫长的夏日',2),('林间慢行',3),('远山回响',4),('夜航手记',5)]):
        x=96+i*294
        s+=picture(x,306,258,387,f'film-{film}',14)
        if i==0:s+=rect(x-7,299,272,401,'none',20,INK,4)
        s+=txt(x,743,title,34,INK,600)+txt(x,783,'剧集' if i in (1,3) else '电影',28,MUTED)
    s+=rect(96,838,1728,138,PANEL,16)
    s+=txt(126,891,'潮汐之间',34,INK,600)+txt(126,939,'2026 · 剧情    沿着海岸线，寻找一封迟到的信。',30,MUTED)
    s+=txt(1515,919,'确定  查看详情',28,INK)
    return s+end('焦点轻缩放、不改变列宽；返回恢复原片位。')


def detail():
    s=start('A2 详情 / 保留小窗播放')
    s+=txt(96,98,'‹  返回片库',32,MUTED)+txt(1544,98,'片源 · 示例片库',28,MUTED)
    s+=txt(96,205,'潮汐之间',66,INK,600)+txt(98,263,'2026 · 剧情 · 电影',32,MUTED)
    s+=txt(98,327,'沿着海岸线，寻找一封迟到的信。',32,INK)
    s+=txt(98,372,'在潮起潮落之间，与往事重逢。',32,INK)
    s+=txt(98,431,'更多简介与演职员  ›',29,MUTED)
    s+=button(96,484,264,'全屏观看',False,True)+button(388,484,200,'＋ 收藏')+button(616,484,236,'更换片源')
    s+=picture(1016,170,800,450,'hero',14)
    s+=rect(1009,163,814,464,'none',20,INK,4)
    s+=rect(1038,185,168,46,'#17191E',8)+txt(1058,216,'正在播放',26,INK)
    s+=rect(1032,545,768,58,'#17191E',8)+txt(1058,583,'正片   38:12',28,INK)+txt(1554,583,'确定 · 全屏',28,INK)
    s+=txt(96,691,'播放线路',34,INK,600)+rect(294,648,278,64,PANEL,10)+txt(318,691,'✓ 线路一',30,GOLD)
    s+=rect(594,648,228,64,PANEL,10)+txt(620,691,'线路二',30,INK)
    s+=txt(96,791,'选集',36,INK,600)+txt(1575,791,'共 1 个视频',28,MUTED)
    s+=rect(96,830,356,90,PANEL,12)+txt(123,886,'✓ 正片 · 播放中',32,GOLD)
    s+=txt(495,887,'字幕、音轨、倍速等在播放控制中调整。',28,MUTED)
    return s+end('示例为小窗已播放状态；保留现有全屏与返回机制。')


def player():
    s=start('A2 播放控制 / 常用与高级分层')+picture(0,0,1920,1080,'hero')
    s+=rect(0,0,1920,1080,'#00000033',0)
    s+=rect(0,0,1920,150,'#101114C0',0)+txt(96,97,'‹  潮汐之间',38,INK,600)+txt(1640,95,'正片',30,INK)
    s+=rect(0,648,1920,432,'url(#bottom)',0)
    s+=txt(96,746,'已暂停',30,GOLD)
    s+=txt(96,808,'38:12',30,INK)+txt(1720,808,'1:42:00',30,INK)
    s+=rect(96,839,1728,8,'#62615E',4)+rect(96,839,647,8,GOLD,4)
    s+='<circle cx="743" cy="843" r="10" fill="#D7B889"/>'
    for x,w,label,focused in [(103,226,'▶  继续',True),(361,210,'选集',False),(603,226,'字幕 / 音轨',False),(861,200,'倍速',False),(1093,200,'画面',False),(1325,250,'更多设置',False)]:
        s+=button(x,900,w,label,focused,focused)
    return s+end('上：进度条    返回：收起控制层    更多：引擎 / 解码 / 跳过片头片尾')


if __name__ == '__main__':
    screens=[('01-home',lambda:home(False),'沉浸首页','合适横图下的展示。相比 A1，画面融入背景，续播增至四张标准横卡，增加片源与更多入口。'),
             ('02-poster-fallback',lambda:home(True),'只有竖海报时','同一套布局的真实能力适配状态，不是另一套配色。没有独立横幅也可以使用；简介缺失不编造。'),
             ('03-browse',browse,'进入片库','稳定六列竖海报与下方信息区。不会像示范中的 Netflix 展开卡那样重新分配列宽。'),
             ('04-detail',detail,'保留小窗的详情页','示例为已经开始小窗播放的状态；保持播放器、线路与选集能力，明确全屏入口。'),
             ('05-player',player,'播放控制','保留常用层，技术项移到更多；示例为暂停状态。')]
    blocks=[]
    for name,fn,title,desc in screens:
        (ROOT/f'{name}.svg').write_text(fn(),encoding='utf-8')
        blocks.append(f'<section id="{name}"><h2>{title}</h2><p>{desc}</p><a href="{name}.svg"><img width="1920" height="1080" src="{name}.svg" alt="A2 {title} 静态设计图"></a><p><a href="{name}.png">PNG 大图</a> · <a href="{name}.svg">可编辑 SVG</a></p></section>')
    (ROOT/'index.html').write_text('''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>A2 · 曜石影院 / 主流 TV 设计研究</title><style>*{box-sizing:border-box}body{margin:0;background:#101114;color:#F5F3EF;font:18px/1.7 system-ui,"PingFang SC",sans-serif}main{max-width:1500px;margin:auto;padding:56px 28px}h1{font-size:48px;line-height:1.25}h2{font-size:32px}p{color:#B7B4AE}a{color:#D7B889}a:focus-visible{outline:3px solid #F5F3EF;outline-offset:5px}section{margin-top:64px;padding-top:24px;border-top:1px solid #343436}img{display:block;width:100%;height:auto;border-radius:12px;border:1px solid #343436}nav{display:flex;gap:24px;flex-wrap:wrap}.note{padding:20px 24px;background:#232429;border-radius:12px}</style><main><p>DESIGN RESEARCH / A2 / 待选型，未实施</p><h1>曜石影院，再进一步。</h1><p>沿用 A 的炭黑与香槟金，借鉴 Netflix、Prime Video、Apple TV 的可验证设计模式，不复制它们的商业内容体系。</p><div class="note">本页是设计提案，不是 Android 运行截图。复用上一轮 GPT Image 生成的虚构影片素材，便于公平比较布局；文字、控件、焦点与布局均为可编辑 SVG。没有新增内容服务或播放器能力。</div><nav><a href="#01-home">沉浸首页</a><a href="#02-poster-fallback">海报适配</a><a href="#03-browse">片库</a><a href="#04-detail">详情</a><a href="#05-player">播放控制</a><a href="research.md">调研与取舍</a><a href="../tv-ui-2026-09/a-obsidian.png">对比上一版 A</a></nav>''' + ''.join(blocks) + '<section><h2>下一步由你选择</h2><p>推荐采用 A2 的统一视觉与交互方向，同时保留海报适配状态。待你确认后再制定实施与 Android 真机验收方案。</p></section></main></html>',encoding='utf-8')
    print('Rendered five A2 SVG screens and gallery')
