import React, { useState } from 'react';
import {
  ChevronUp,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Home,
  Menu,
  RotateCcw,
  Volume2,
  VolumeX,
  Power,
  Tv,
  X,
} from 'lucide-react';
import { soundFX } from '../utils/sound';

interface VirtualRemoteProps {
  onDirection: (direction: 'up' | 'down' | 'left' | 'right') => void;
  onSelect: () => void;
  onBack: () => void;
  onHome: () => void;
  onMenu: () => void;
  onToggleSound: () => void;
  soundEnabled: boolean;
}

export const VirtualRemote: React.FC<VirtualRemoteProps> = ({
  onDirection,
  onSelect,
  onBack,
  onHome,
  onMenu,
  onToggleSound,
  soundEnabled,
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [activeBtn, setActiveBtn] = useState<string | null>(null);

  const handlePress = (btnName: string, action: () => void) => {
    setActiveBtn(btnName);
    action();
    setTimeout(() => setActiveBtn(null), 180);
  };

  if (!isOpen) {
    return (
      <button
        onClick={() => {
          soundFX.playFocus();
          setIsOpen(true);
        }}
        className="fixed bottom-6 right-6 z-40 px-4 py-2.5 rounded-full bg-[#202024]/90 backdrop-blur-xl border border-[#E5A958]/40 shadow-xl shadow-black/50 text-white flex items-center gap-2 text-sm font-bold hover:scale-105 transition-all cursor-pointer group"
        title="打开客厅虚拟遥控器"
      >
        <Tv className="w-4 h-4 text-[#E5A958] group-hover:rotate-12 transition-transform" />
        <span>客厅遥控器</span>
        <span className="text-[10px] px-1.5 py-0.5 rounded bg-white/10 text-[#E5A958] font-mono">
          按 R
        </span>
      </button>
    );
  }

  return (
    <div className="fixed bottom-6 right-6 z-50 w-64 rounded-3xl bg-[#141418]/95 backdrop-blur-2xl border-2 border-[#E5A958]/30 shadow-2xl p-5 select-none animate-fadeIn">
      <div className="flex items-center justify-between pb-3 border-b border-white/10 mb-4">
        <div className="flex items-center gap-2">
          <div className="w-2.5 h-2.5 rounded-full bg-[#4ADE80] animate-pulse" />
          <span className="text-xs font-mono font-bold tracking-wider text-white">
            LEANBACK REMOTE
          </span>
        </div>
        <button
          onClick={() => setIsOpen(false)}
          className="text-[#8E8E93] hover:text-white transition-colors"
          title="最小化"
        >
          <X className="w-4 h-4" />
        </button>
      </div>

      <div className="flex justify-between items-center px-3 mb-5">
        <button
          onClick={() =>
            handlePress('power', () => {
              soundFX.playFocus();
              alert('FongMi TV 待机节能模式就绪');
            })
          }
          className={`w-9 h-9 rounded-full bg-red-500/20 text-red-400 border border-red-500/30 flex items-center justify-center transition-all ${
            activeBtn === 'power' ? 'scale-90 bg-red-500 text-white' : 'hover:scale-105'
          }`}
          title="电源"
        >
          <Power className="w-4 h-4" />
        </button>

        <button
          onClick={() =>
            handlePress('home', () => {
              soundFX.playSelect();
              onHome();
            })
          }
          className={`w-9 h-9 rounded-full bg-white/10 text-white border border-white/10 flex items-center justify-center transition-all ${
            activeBtn === 'home' ? 'scale-90 bg-[#E5A958] text-[#0E0E10]' : 'hover:scale-105'
          }`}
          title="主页"
        >
          <Home className="w-4 h-4" />
        </button>
      </div>

      <div className="relative w-44 h-44 mx-auto rounded-full bg-[#1C1C22] border-2 border-white/10 p-2 shadow-inner flex items-center justify-center mb-5">
        <button
          onClick={() => handlePress('up', () => { soundFX.playFocus(); onDirection('up'); })}
          className={`absolute top-2 w-14 h-10 rounded-t-2xl flex items-center justify-center text-[#C6C6CD] hover:text-white transition-all ${activeBtn === 'up' ? 'text-[#E5A958] scale-95' : ''}`}
        >
          <ChevronUp className="w-6 h-6" />
        </button>
        <button
          onClick={() => handlePress('down', () => { soundFX.playFocus(); onDirection('down'); })}
          className={`absolute bottom-2 w-14 h-10 rounded-b-2xl flex items-center justify-center text-[#C6C6CD] hover:text-white transition-all ${activeBtn === 'down' ? 'text-[#E5A958] scale-95' : ''}`}
        >
          <ChevronDown className="w-6 h-6" />
        </button>
        <button
          onClick={() => handlePress('left', () => { soundFX.playFocus(); onDirection('left'); })}
          className={`absolute left-2 w-10 h-14 rounded-l-2xl flex items-center justify-center text-[#C6C6CD] hover:text-white transition-all ${activeBtn === 'left' ? 'text-[#E5A958] scale-95' : ''}`}
        >
          <ChevronLeft className="w-6 h-6" />
        </button>
        <button
          onClick={() => handlePress('right', () => { soundFX.playFocus(); onDirection('right'); })}
          className={`absolute right-2 w-10 h-14 rounded-r-2xl flex items-center justify-center text-[#C6C6CD] hover:text-white transition-all ${activeBtn === 'right' ? 'text-[#E5A958] scale-95' : ''}`}
        >
          <ChevronRight className="w-6 h-6" />
        </button>

        <button
          onClick={() => handlePress('ok', () => { soundFX.playSelect(); onSelect(); })}
          className={`w-16 h-16 rounded-full bg-gradient-to-br from-[#E5A958] to-[#B37B30] text-[#0E0E10] font-black text-sm tracking-wider shadow-lg flex items-center justify-center transition-all ${activeBtn === 'ok' ? 'scale-90 shadow-none' : 'hover:scale-105'}`}
        >
          OK
        </button>
      </div>

      <div className="grid grid-cols-3 gap-2 px-2">
        <button
          onClick={() => handlePress('back', () => { soundFX.playBack(); onBack(); })}
          className={`p-2.5 rounded-xl bg-white/5 hover:bg-white/15 text-white flex flex-col items-center justify-center text-[10px] font-bold ${activeBtn === 'back' ? 'bg-[#E5A958] text-[#0E0E10]' : ''}`}
        >
          <RotateCcw className="w-4 h-4 mb-0.5" />
          <span>返回</span>
        </button>

        <button
          onClick={() => handlePress('menu', () => { soundFX.playFocus(); onMenu(); })}
          className={`p-2.5 rounded-xl bg-white/5 hover:bg-white/15 text-white flex flex-col items-center justify-center text-[10px] font-bold ${activeBtn === 'menu' ? 'bg-[#E5A958] text-[#0E0E10]' : ''}`}
        >
          <Menu className="w-4 h-4 mb-0.5" />
          <span>菜单</span>
        </button>

        <button
          onClick={() => handlePress('sound', () => onToggleSound())}
          className={`p-2.5 rounded-xl bg-white/5 hover:bg-white/15 text-white flex flex-col items-center justify-center text-[10px] font-bold ${activeBtn === 'sound' ? 'bg-[#E5A958] text-[#0E0E10]' : ''}`}
        >
          {soundEnabled ? <Volume2 className="w-4 h-4 mb-0.5 text-[#E5A958]" /> : <VolumeX className="w-4 h-4 mb-0.5 text-red-400" />}
          <span>{soundEnabled ? '音效' : '静音'}</span>
        </button>
      </div>
    </div>
  );
};
