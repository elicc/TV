import React, { useState, useEffect } from 'react';
import { NavTab } from '../types/tv';
import { soundFX } from '../utils/sound';
import { Home, Film, Tv, Bookmark, Search, Settings, Volume2, VolumeX, ShieldCheck } from 'lucide-react';

interface HeaderProps {
  currentTab: NavTab;
  onSelectTab: (tab: NavTab) => void;
  onOpenSettings: () => void;
  activeApiSource?: string;
  onSwitchApiSource?: () => void;
  soundEnabled: boolean;
  onToggleSound: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  currentTab,
  onSelectTab,
  onOpenSettings,
  activeApiSource = '饭太硬 (极速4K线路)',
  onSwitchApiSource,
  soundEnabled,
  onToggleSound,
}) => {
  const [timeStr, setTimeStr] = useState('20:45');
  const [dateStr, setDateStr] = useState('周五 · 4月18日');

  useEffect(() => {
    const updateTime = () => {
      const now = new Date();
      const h = String(now.getHours()).padStart(2, '0');
      const m = String(now.getMinutes()).padStart(2, '0');
      setTimeStr(`${h}:${m}`);

      const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
      const day = days[now.getDay()];
      const month = now.getMonth() + 1;
      const date = now.getDate();
      setDateStr(`${day} · ${month}月${date}日`);
    };
    updateTime();
    const timer = setInterval(updateTime, 10000);
    return () => clearInterval(timer);
  }, []);

  const handleTabClick = (tab: NavTab) => {
    soundFX.playFocus();
    onSelectTab(tab);
  };

  return (
    <header className="relative z-30 px-6 lg:px-[72px] pt-6 pb-2 flex items-center justify-between entrance-stagger-1 w-full select-none">
      <div className="flex items-center gap-6 lg:gap-8">
        <button
          onClick={() => handleTabClick('home')}
          className="flex items-center gap-3 focus:outline-none group text-left cursor-pointer"
        >
          <div className="w-10 h-10 rounded-xl bg-gradient-to-br from-[#E5A958] to-[#9E6E2D] flex items-center justify-center shadow-lg shadow-[#E5A958]/20 group-hover:scale-105 transition-transform">
            <svg className="w-5 h-5 text-[#0E0E10]" fill="currentColor" viewBox="0 0 24 24">
              <path d="M4 6.5C4 5.11929 5.11929 4 6.5 4H17.5C18.8807 4 20 5.11929 20 6.5V14.5C20 15.8807 18.8807 17 17.5 17H6.5C5.11929 17 4 15.8807 4 14.5V6.5ZM8 19H16V21H8V19Z" />
              <path d="M10 8.5L15 11.5L10 14.5V8.5Z" fill="#0E0E10" />
            </svg>
          </div>
          <div className="text-[22px] font-black tracking-wider text-white flex items-center gap-2">
            FONGMI{' '}
            <span className="text-[11px] px-1.5 py-0.5 rounded bg-[#E5A958]/20 text-[#E5A958] font-bold tracking-widest border border-[#E5A958]/30">
              TV
            </span>
          </div>
        </button>

        <nav className="rounded-full bg-white/[0.04] border border-white/[0.06] p-1 flex items-center gap-1 shadow-inner">
          <button
            onClick={() => handleTabClick('home')}
            className={`focusable rounded-full px-4 py-1.5 flex items-center gap-2 text-[15px] font-medium transition-all focus:outline-none cursor-pointer ${
              currentTab === 'home'
                ? 'bg-white/10 text-white font-semibold shadow-sm'
                : 'text-[#A2A2A8] hover:text-white'
            }`}
            tabIndex={0}
          >
            <Home className={`w-4 h-4 ${currentTab === 'home' ? 'text-[#E5A958]' : 'text-[#A2A2A8]'}`} />
            <span>首页</span>
          </button>
          <button
            onClick={() => handleTabClick('vod')}
            className={`focusable rounded-full px-4 py-1.5 flex items-center gap-2 text-[15px] font-medium transition-all focus:outline-none cursor-pointer ${
              currentTab === 'vod'
                ? 'bg-white/10 text-white font-semibold shadow-sm'
                : 'text-[#A2A2A8] hover:text-white'
            }`}
            tabIndex={0}
          >
            <Film className={`w-4 h-4 ${currentTab === 'vod' ? 'text-[#E5A958]' : 'text-[#A2A2A8]'}`} />
            <span>点播</span>
          </button>
          <button
            onClick={() => handleTabClick('live')}
            className={`focusable rounded-full px-4 py-1.5 flex items-center gap-2 text-[15px] font-medium transition-all focus:outline-none cursor-pointer ${
              currentTab === 'live'
                ? 'bg-white/10 text-white font-semibold shadow-sm'
                : 'text-[#A2A2A8] hover:text-white'
            }`}
            tabIndex={0}
          >
            <Tv className={`w-4 h-4 ${currentTab === 'live' ? 'text-[#E5A958]' : 'text-[#A2A2A8]'}`} />
            <span>直播</span>
          </button>
          <button
            onClick={() => handleTabClick('fav')}
            className={`focusable rounded-full px-4 py-1.5 flex items-center gap-2 text-[15px] font-medium transition-all focus:outline-none cursor-pointer ${
              currentTab === 'fav'
                ? 'bg-white/10 text-white font-semibold shadow-sm'
                : 'text-[#A2A2A8] hover:text-white'
            }`}
            tabIndex={0}
          >
            <Bookmark className={`w-4 h-4 ${currentTab === 'fav' ? 'text-[#E5A958]' : 'text-[#A2A2A8]'}`} />
            <span>收藏历史</span>
          </button>
          <button
            onClick={() => handleTabClick('search')}
            className={`focusable rounded-full px-4 py-1.5 flex items-center gap-2 text-[15px] font-medium transition-all focus:outline-none cursor-pointer ${
              currentTab === 'search'
                ? 'bg-white/10 text-white font-semibold shadow-sm'
                : 'text-[#A2A2A8] hover:text-white'
            }`}
            tabIndex={0}
          >
            <Search className={`w-4 h-4 ${currentTab === 'search' ? 'text-[#E5A958]' : 'text-[#A2A2A8]'}`} />
            <span>搜索</span>
          </button>
          <button
            onClick={() => handleTabClick('spec')}
            className={`focusable rounded-full px-3 py-1.5 flex items-center gap-1.5 text-[14px] font-medium transition-all focus:outline-none cursor-pointer ${
              currentTab === 'spec'
                ? 'bg-[#E5A958]/20 text-[#E5A958] font-bold border border-[#E5A958]/40'
                : 'text-[#8E8E93] hover:text-[#E5A958]'
            }`}
            title="查看 Android TV 10-foot 视觉设计规范与状态集"
            tabIndex={0}
          >
            <ShieldCheck className="w-3.5 h-3.5 text-[#E5A958]" />
            <span>规范看板</span>
          </button>
        </nav>
      </div>

      <div className="flex items-center gap-4 lg:gap-5">
        <button
          onClick={onSwitchApiSource}
          className="focusable glass-pill px-4 py-1.5 rounded-full flex items-center gap-2.5 text-[15px] text-[#D1D1D8] hover:border-[#E5A958]/40 cursor-pointer focus:outline-none"
          title="点击快速切换解析接口"
          tabIndex={0}
        >
          <span className="w-2.5 h-2.5 rounded-full bg-[#4ADE80] animate-pulse"></span>
          <span className="text-[#A2A2A8] text-[14px]">接口:</span>
          <span className="font-bold text-white tracking-wide">{activeApiSource}</span>
          <span className="text-xs px-1.5 py-0.5 rounded bg-white/10 text-[#E5A958] font-mono font-medium">
            Spider
          </span>
        </button>

        <button
          onClick={onToggleSound}
          className="focusable w-10 h-10 rounded-full bg-white/[0.05] border border-white/[0.08] flex items-center justify-center text-[#D1D1D8] hover:text-[#E5A958] transition-all focus:outline-none cursor-pointer"
          title={soundEnabled ? '音效已开启' : '音效已静音'}
          tabIndex={0}
        >
          {soundEnabled ? <Volume2 className="w-4 h-4 text-[#E5A958]" /> : <VolumeX className="w-4 h-4 text-[#8E8E93]" />}
        </button>

        <button
          onClick={onOpenSettings}
          aria-label="设置"
          className={`focusable w-10 h-10 rounded-full border flex items-center justify-center transition-all focus:outline-none cursor-pointer ${
            currentTab === 'settings'
              ? 'bg-[#E5A958] text-[#0E0E10] border-[#FCD58B]'
              : 'bg-white/[0.05] border-white/[0.08] text-[#D1D1D8] hover:text-white'
          }`}
          tabIndex={0}
        >
          <Settings className="w-5 h-5" />
        </button>

        <div className="text-right pl-2 min-w-[110px]">
          <div className="text-[28px] font-bold text-white tracking-tight leading-none font-mono" id="tv-clock">
            {timeStr}
          </div>
          <div className="text-[13px] text-[#8E8E93] font-medium tracking-wide mt-1">
            {dateStr}
          </div>
        </div>
      </div>
    </header>
  );
};
