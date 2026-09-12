import React from 'react';

interface FooterHintProps {
  activeKeyHint?: string;
}

export const FooterHint: React.FC<FooterHintProps> = ({ activeKeyHint }) => {
  return (
    <footer className="relative z-20 px-6 lg:px-[72px] pb-4 pt-3 flex flex-wrap items-center justify-between text-[#8E8E93] text-[15px] border-t border-white/5 entrance-stagger-4 select-none w-full">
      <div className="flex items-center gap-6 lg:gap-7 flex-wrap">
        <span className="flex items-center gap-2">
          <kbd
            className={`transition-all duration-150 px-2 py-0.5 rounded font-mono text-xs border ${
              activeKeyHint === 'arrows'
                ? 'bg-[#E5A958] text-[#0E0E10] border-[#FCD58B] scale-110 shadow-[0_0_12px_rgba(229,169,88,0.8)]'
                : 'bg-white/10 text-white border-white/15'
            }`}
          >
            ▲▼◀▶
          </kbd>{' '}
          移动焦点
        </span>
        <span className="flex items-center gap-2">
          <kbd
            className={`transition-all duration-150 px-2.5 py-0.5 rounded font-mono text-xs border ${
              activeKeyHint === 'ok'
                ? 'bg-[#E5A958] text-[#0E0E10] border-[#FCD58B] scale-110 shadow-[0_0_12px_rgba(229,169,88,0.8)]'
                : 'bg-white/10 text-white border-white/15'
            }`}
          >
            OK
          </kbd>{' '}
          打开 / 播放
        </span>
        <span className="flex items-center gap-2">
          <kbd
            className={`transition-all duration-150 px-2 py-0.5 rounded font-mono text-xs border ${
              activeKeyHint === 'menu'
                ? 'bg-[#E5A958] text-[#0E0E10] border-[#FCD58B] scale-110 shadow-[0_0_12px_rgba(229,169,88,0.8)]'
                : 'bg-white/10 text-white border-white/15'
            }`}
          >
            Menu
          </kbd>{' '}
          换源 / 选项
        </span>
        <span className="flex items-center gap-2">
          <kbd
            className={`transition-all duration-150 px-2 py-0.5 rounded font-mono text-xs border ${
              activeKeyHint === 'back'
                ? 'bg-[#E5A958] text-[#0E0E10] border-[#FCD58B] scale-110 shadow-[0_0_12px_rgba(229,169,88,0.8)]'
                : 'bg-white/10 text-white border-white/15'
            }`}
          >
            Back
          </kbd>{' '}
          返回上一级
        </span>
      </div>
      <div className="flex items-center gap-3 mt-2 lg:mt-0">
        <span className="inline-block w-2 h-2 rounded-full bg-[#E5A958] animate-ping opacity-75"></span>
        <span className="text-white/80 text-[14px]">
          1080p 60fps Leanback · 支持键盘 / 遥控器实时交互
        </span>
      </div>
    </footer>
  );
};
