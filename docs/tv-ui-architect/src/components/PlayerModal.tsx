import React, { useState, useEffect } from 'react';
import { MovieItem, LiveChannel } from '../types/tv';
import { soundFX } from '../utils/sound';
import { Play, Pause, ChevronLeft, Volume2, FastForward, Rewind, Check, SkipBack, SkipForward, List, Settings } from 'lucide-react';

interface PlayerModalProps {
  movie?: MovieItem | null;
  channel?: LiveChannel | null;
  onClose: () => void;
}

export const PlayerModal: React.FC<PlayerModalProps> = ({ movie, channel, onClose }) => {
  const [isPlaying, setIsPlaying] = useState(true);
  const [currentTimeSec, setCurrentTimeSec] = useState(2295); // 38m 15s
  const [totalTimeSec] = useState(9992); // 02:46:32
  const [showHUD, setShowHUD] = useState(true);
  
  const [showSkipHUD, setShowSkipHUD] = useState(false);
  const [skipDirection, setSkipDirection] = useState<'forward' | 'backward'>('forward');
  const [showSettings, setShowSettings] = useState(false);
  const [showEpisodes, setShowEpisodes] = useState(false);
  const [currentEpisode, setCurrentEpisode] = useState(1);

  const [activeSpeed, setActiveSpeed] = useState('1.0x');
  const [activeQuality, setActiveQuality] = useState(movie?.quality || '4K 原盘');
  const [activeAudio, setActiveAudio] = useState(movie?.audio || '杜比全景声 7.1');

  // Format seconds to HH:MM:SS
  const formatTime = (secs: number) => {
    const h = Math.floor(secs / 3600);
    const m = Math.floor((secs % 3600) / 60);
    const s = Math.floor(secs % 60);
    return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  };

  // Simulated playback
  useEffect(() => {
    let interval: NodeJS.Timeout;
    if (isPlaying) {
      interval = setInterval(() => {
        setCurrentTimeSec((prev) => (prev < totalTimeSec ? prev + 1 : prev));
      }, 1000);
    }
    return () => clearInterval(interval);
  }, [isPlaying, totalTimeSec]);

  // Auto hide HUD
  useEffect(() => {
    let timer: NodeJS.Timeout;
    if (showHUD && !showSettings && !showEpisodes) {
      timer = setTimeout(() => setShowHUD(false), 3000); // 3 seconds hide
    }
    return () => clearTimeout(timer);
  }, [showHUD, showSettings, showEpisodes, currentTimeSec, isPlaying]);

  // Auto hide Skip HUD
  useEffect(() => {
    let timer: NodeJS.Timeout;
    if (showSkipHUD) {
      timer = setTimeout(() => setShowSkipHUD(false), 1500);
    }
    return () => clearTimeout(timer);
  }, [showSkipHUD, currentTimeSec]);

  // Handle keyboard shortcuts
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      setShowHUD(true);
      if (e.key === 'Escape' || e.key === 'Backspace') {
        e.preventDefault();
        soundFX.playBack();
        if (showSettings) {
          setShowSettings(false);
        } else if (showEpisodes) {
          setShowEpisodes(false);
        } else {
          onClose();
        }
      } else if (e.key === ' ' || e.key === 'Enter') {
        e.preventDefault();
        soundFX.playSelect();
        setIsPlaying((prev) => !prev);
      } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        soundFX.playFocus();
        setCurrentTimeSec((prev) => Math.max(0, prev - 15));
        setSkipDirection('backward');
        setShowSkipHUD(true);
      } else if (e.key === 'ArrowRight') {
        e.preventDefault();
        soundFX.playFocus();
        setCurrentTimeSec((prev) => Math.min(totalTimeSec, prev + 15));
        setSkipDirection('forward');
        setShowSkipHUD(true);
      } else if (e.key === 'ArrowDown' || e.key === 'ArrowUp') {
        e.preventDefault();
        soundFX.playSelect();
        setShowEpisodes((p) => !p);
      } else if (e.key.toLowerCase() === 'm') {
        e.preventDefault();
        soundFX.playSelect();
        setShowSettings((p) => !p);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose, totalTimeSec, showSettings]);

  const progressPercent = (currentTimeSec / totalTimeSec) * 100;
  const title = movie?.title || channel?.name || '沙丘 2：厄拉科斯 命运之战';

  return (
    <div 
      className="fixed inset-0 z-50 bg-[#121214] flex flex-col justify-between overflow-hidden select-none font-sans"
      onMouseMove={() => setShowHUD(true)}
      onClick={() => setShowHUD(true)}
    >
      {/* Background Simulation & Visuals */}
      <div className="absolute inset-0 z-0 overflow-hidden flex items-center justify-center">
        {/* Simulate Video Content */}
        <div className="absolute inset-0 bg-[#0f0e0c]"></div>
        
        {/* Ambient drift */}
        <div className="absolute w-[900px] h-[600px] rounded-full bg-[#E5A958]/10 blur-[150px] pointer-events-none"></div>

        {/* Huge Watermark */}
        <div className="text-[280px] font-black text-white/[0.03] font-serif leading-none select-none pointer-events-none translate-x-[15%]">
          DUNE
        </div>

        {/* Fake Danmaku / Comments */}
        <div className="absolute right-[20%] top-[20%] px-5 py-2.5 rounded-full bg-black/60 border border-[#E5A958]/30 text-[#FCD58B] font-bold text-[15px] shadow-lg backdrop-blur-md whitespace-nowrap">
          4K 原盘 HDR10+ 色彩过渡极度丝滑
        </div>
        <div className="absolute right-[10%] top-[30%] px-5 py-2.5 rounded-full bg-black/60 border border-white/10 text-[#e5e1e4] text-[15px] shadow-lg backdrop-blur-md flex items-center gap-2 whitespace-nowrap">
          汉斯·季默配乐真的封神！低音炮在震了 <Volume2 className="w-4 h-4 text-[#E5A958]" />
        </div>
        <div className="absolute right-[25%] top-[40%] text-[18px] text-white/70 drop-shadow-md whitespace-nowrap tracking-wide">
          沙虫出场这一段名场面，准备好爆米花
        </div>
        <div className="absolute left-[32%] top-[45%] px-5 py-2 rounded-full bg-[#113a23]/70 border border-[#4ADE80]/30 text-[#4ADE80] font-bold text-[15px] backdrop-blur-md flex items-center gap-2 whitespace-nowrap">
          <div className="w-2 h-2 rounded-full bg-[#4ADE80]"></div>
          MediaCodec 硬解 60fps 极速无丢帧
        </div>
        <div className="absolute right-[15%] top-[55%] text-[16px] text-[#B8B8C0] drop-shadow-md whitespace-nowrap tracking-wide">
          遥控器按 [菜单] 键唤起音轨与外挂字幕设置
        </div>
      </div>

      {/* Main HUD */}
      <div className={`absolute inset-0 z-20 flex flex-col justify-between pointer-events-none transition-opacity duration-500 ${showHUD ? 'opacity-100' : 'opacity-0'}`}>
        
        {/* TOP BAR */}
        <div className="px-[90px] pt-[54px] pb-10 bg-gradient-to-b from-[#0e0e10]/95 to-transparent flex justify-between items-start pointer-events-auto">
          {/* Left: Title & Info */}
          <div className="flex gap-5">
            <button 
              onClick={() => { soundFX.playBack(); onClose(); }} 
              className="focusable w-[48px] h-[48px] rounded-xl bg-white/5 border border-white/10 flex items-center justify-center hover:bg-white/10 transition-colors shrink-0 focus:border-[#FCD58B] focus:shadow-[0_0_15px_rgba(252,213,139,0.3)]"
            >
              <ChevronLeft className="w-7 h-7 text-white" />
            </button>
            <div>
              <div className="flex items-center gap-3 mb-2.5">
                <h2 className="text-[32px] font-bold text-[#F4F4F6] tracking-wide font-sans">{title}</h2>
                <span className="px-3 py-1 rounded-md bg-[#5c4304] text-[#FCD58B] text-[13px] font-bold tracking-wider">第 {String(currentEpisode).padStart(2, '0')} 集 正片</span>
                <span className="px-3 py-1 rounded-md bg-white/10 text-[#e5e1e4] text-[13px] font-mono tracking-wide">4K 原盘 · HEVC 10bit</span>
                <span className="px-3 py-1 rounded-md bg-[#113a23] text-[#4ADE80] text-[13px] font-bold flex items-center gap-1.5 tracking-wide">
                  <div className="w-1.5 h-1.5 rounded-full bg-[#4ADE80]"></div>
                  {activeAudio}
                </span>
              </div>
              <div className="text-[14px] text-[#B8B8C0] tracking-wide">
                线路: {movie?.sourceLine || '饭太硬 (极速4K专线)'} &nbsp;|&nbsp; 解码: 硬解 (MediaCodec) &nbsp;|&nbsp; 倍速: {activeSpeed} &nbsp;|&nbsp; 按 [下键] 快速选集 / [菜单键] 呼出高级设置
              </div>
            </div>
          </div>

          {/* Right: Metrics & Exit */}
          <div className="flex items-center gap-5">
            <div className="px-5 py-2.5 rounded-full bg-black/40 border border-white/5 flex items-center gap-3 text-[14px] font-mono backdrop-blur-md">
              <span className="flex items-center gap-2"><div className="w-1.5 h-1.5 rounded-full bg-[#4ADE80]"></div><span className="text-[#4ADE80]">58 Mbps</span></span>
              <span className="text-[#504538]">|</span>
              <span className="text-[#e5e1e4]">缓冲: 94s (已满)</span>
              <span className="text-[#504538]">|</span>
              <span className="text-white font-bold">21:14</span>
            </div>
            <button 
              onClick={() => { soundFX.playBack(); onClose(); }} 
              className="focusable px-4 py-2.5 rounded-lg bg-white/5 border border-white/10 flex items-center gap-2.5 text-[14px] text-[#B8B8C0] hover:text-white transition-colors"
            >
              <span className="px-2 py-0.5 rounded bg-white/10 text-[11px] font-mono">Back</span>
              退出全屏
            </button>
          </div>
        </div>

        {/* MIDDLE: Floating Overlays */}
        <div className="relative flex-1 pointer-events-none overflow-hidden">
          {/* Skip HUD */}
          <div className={`absolute left-[90px] top-[40%] flex flex-col gap-3 transition-opacity duration-300 ${showSkipHUD ? 'opacity-100' : 'opacity-0'}`}>
            <div className="px-6 py-4 rounded-2xl bg-[#1C1C20]/95 backdrop-blur-xl border border-white/10 flex items-center gap-5 shadow-2xl">
              <div className="w-[52px] h-[52px] rounded-xl bg-[#E5A958]/15 text-[#FCD58B] flex items-center justify-center">
                {skipDirection === 'forward' ? <FastForward className="w-7 h-7" /> : <Rewind className="w-7 h-7" />}
              </div>
              <div>
                <div className="flex items-baseline gap-3 mb-1">
                  <span className="text-[26px] font-bold text-white tracking-wider">{skipDirection === 'forward' ? '+15s' : '-15s'}</span>
                  <span className="text-[15px] font-mono text-[#8C8C96]">{formatTime(currentTimeSec)}</span>
                </div>
                <div className="text-[13px] text-[#8C8C96] tracking-wide">短按 ±15s / 长按平滑快进</div>
              </div>
            </div>
            <div className="px-5 py-3 rounded-xl bg-[#1C1C20]/95 backdrop-blur-xl border border-[#E5A958]/30 flex items-center justify-between shadow-lg">
              <div className="flex items-center gap-2.5 text-[14px] text-[#e5e1e4]">
                <div className="w-2 h-2 rounded-full bg-[#E5A958]"></div>
                智能防卡顿: 备用切源就绪
              </div>
              <span className="text-[13px] font-mono text-[#E5A958] font-bold ml-6">Auto</span>
            </div>
          </div>

          {/* Quick Settings Panel */}
          <div className={`absolute right-[90px] top-[15%] w-[400px] rounded-[24px] bg-[#1C1C20]/95 backdrop-blur-2xl border border-white/10 shadow-2xl p-7 pointer-events-auto transition-all duration-300 transform ${showSettings ? 'translate-x-0 opacity-100' : 'translate-x-12 opacity-0 pointer-events-none'}`}>
            <div className="flex items-center justify-between mb-8">
              <div className="flex items-center gap-3 text-[20px] font-bold text-white tracking-wide">
                <Settings className="w-5 h-5 text-[#E5A958]" />
                播放快速调优
              </div>
              <div className="text-[13px] text-[#8C8C96] flex items-center gap-1.5 font-mono">
                Menu 键收起
              </div>
            </div>
            
            <div className="space-y-8">
              <div>
                <div className="flex justify-between text-[15px] mb-4">
                  <span className="text-[#B8B8C0]">字幕音轨选择</span>
                  <span className="text-[#E5A958] font-bold text-[13px]">已同步原盘双语</span>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <button className="focusable py-3.5 rounded-[14px] bg-[#26262C] text-[#B8B8C0] text-[14px] font-medium border border-transparent hover:border-white/10 transition-colors">01. 英语原声 (默认)</button>
                  <button className="focusable py-3.5 rounded-[14px] bg-[#F9BB68] text-[#121214] text-[14px] font-bold border border-[#FCD58B] shadow-[0_0_20px_rgba(252,213,139,0.15)] flex items-center justify-center gap-2">
                    <Check className="w-4 h-4 stroke-[3]" /> 02. 中英双语特效
                  </button>
                </div>
              </div>
              
              <div>
                <div className="text-[15px] text-[#B8B8C0] mb-4">画面填充模式</div>
                <div className="grid grid-cols-3 gap-3">
                  <button className="focusable py-3 rounded-[12px] bg-[#18181C] text-[#8C8C96] text-[14px] border border-white/5 hover:bg-[#26262C] hover:text-white transition-colors">原始 16:9</button>
                  <button className="focusable py-3 rounded-[12px] bg-[#26262C] text-[#FCD58B] text-[14px] font-bold border border-[#FCD58B]/40 bg-[#e5a958]/10">智能去黑边</button>
                  <button className="focusable py-3 rounded-[12px] bg-[#18181C] text-[#8C8C96] text-[14px] border border-white/5 hover:bg-[#26262C] hover:text-white transition-colors">全屏拉伸</button>
                </div>
              </div>

              <div>
                <div className="flex justify-between items-center">
                  <div className="text-[15px] text-[#B8B8C0]">字幕时间轴补偿</div>
                  <div className="flex gap-2">
                    <button className="focusable px-3.5 py-2 rounded-lg bg-[#26262C] text-[#8C8C96] text-[13px] font-mono hover:bg-white/10">-0.25s</button>
                    <button className="focusable px-3.5 py-2 rounded-lg bg-[#26262C] text-[#FCD58B] font-bold text-[13px] font-mono border border-[#E5A958]/40 bg-[#e5a958]/10">0.0s</button>
                    <button className="focusable px-3.5 py-2 rounded-lg bg-[#26262C] text-[#8C8C96] text-[13px] font-mono hover:bg-white/10">+0.25s</button>
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* Episode Selection Rail */}
          <div className={`absolute bottom-0 left-[90px] right-[90px] pb-10 transition-all duration-300 transform pointer-events-auto ${showEpisodes ? 'translate-y-0 opacity-100' : 'translate-y-12 opacity-0 pointer-events-none'}`}>
            <div className="flex items-center gap-4 mb-5">
              <h3 className="text-[22px] font-bold text-[#F4F4F6] tracking-wide">选集</h3>
              <span className="px-3 py-1 rounded-full bg-white/10 text-[#B8B8C0] text-[13px] font-mono">共 28 集</span>
            </div>
            <div className="flex gap-4 overflow-x-auto pb-6 snap-x [&::-webkit-scrollbar]:hidden [-ms-overflow-style:none] [scrollbar-width:none]">
              {Array.from({ length: 28 }).map((_, i) => {
                const ep = i + 1;
                const isActive = currentEpisode === ep;
                return (
                  <button
                    key={ep}
                    onClick={() => {
                      soundFX.playSelect();
                      setCurrentEpisode(ep);
                    }}
                    className={`focusable snap-start shrink-0 w-[160px] h-[96px] rounded-[16px] flex flex-col items-center justify-center gap-1.5 transition-all duration-200 border ${
                      isActive 
                        ? 'bg-[#E5A958]/20 border-[#FCD58B] scale-105 shadow-[0_8px_24px_rgba(252,213,139,0.25)]' 
                        : 'bg-[#1C1C20]/95 border-white/5 hover:bg-[#26262C] hover:border-white/20'
                    } backdrop-blur-md`}
                  >
                    <span className={`text-[24px] font-bold font-mono ${isActive ? 'text-[#FCD58B]' : 'text-white'}`}>
                      {String(ep).padStart(2, '0')}
                    </span>
                    <span className={`text-[13px] tracking-wide ${isActive ? 'text-[#FCD58B] font-bold' : 'text-[#8C8C96]'}`}>
                      {isActive ? '正在播放' : '正片'}
                    </span>
                  </button>
                );
              })}
            </div>
          </div>
        </div>

        {/* BOTTOM BAR */}
        <div className="px-[90px] pb-[54px] pt-24 bg-gradient-to-t from-[#0e0e10] via-[#0e0e10]/90 to-transparent flex flex-col gap-4 pointer-events-auto">
          
          {/* Progress & Time */}
          <div className="flex flex-col gap-4 relative">
            {/* Progress Track */}
            <div 
              onClick={(e) => {
                const rect = e.currentTarget.getBoundingClientRect();
                const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
                setCurrentTimeSec(Math.floor(ratio * totalTimeSec));
                soundFX.playSelect();
              }}
              className="relative w-full h-2 rounded-full bg-white/10 cursor-pointer overflow-visible flex items-center"
            >
              <div className="h-full rounded-full bg-gradient-to-r from-[#E5A958] to-[#FCD58B] relative shadow-[0_0_15px_rgba(252,213,139,0.4)]" style={{ width: `${progressPercent}%` }}>
                <div className="absolute right-0 top-1/2 -translate-y-1/2 translate-x-1/2 w-[20px] h-[20px] rounded-full bg-[#FFE8A3] border-[3px] border-white shadow-[0_0_16px_rgba(252,213,139,0.9)] hover:scale-125 transition-transform"></div>
              </div>
            </div>
            
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-4">
                <div className="text-[20px] font-mono text-[#FCD58B] font-bold tracking-wider">{formatTime(currentTimeSec)}</div>
                <div className="text-[18px] font-mono text-[#8C8C96] tracking-wider">/ {formatTime(totalTimeSec)}</div>
                <div className="ml-3 px-3 py-1 rounded-md bg-[#5c4304] text-[#d4b06a] text-[13px] font-bold border border-[#d4b06a]/20">章节 03: 帝国军团沙丘降临</div>
              </div>
              <div className="flex items-center gap-5 text-[14px] font-mono tracking-wide">
                <span className="text-[#B8B8C0]">剩余 {formatTime(totalTimeSec - currentTimeSec)}</span>
                <span className="text-[#504538]">|</span>
                <span className="text-[#4ADE80] font-bold flex items-center gap-2"><div className="w-2 h-2 rounded-full bg-[#4ADE80]"></div> 60 FPS 稳定</span>
              </div>
            </div>
          </div>

          {/* Controls */}
          <div className="flex items-center justify-between mt-3">
            <div className="flex items-center gap-4">
              <button 
                onClick={() => { soundFX.playSelect(); setIsPlaying(!isPlaying); }}
                className="focusable h-14 px-8 rounded-[16px] bg-[#FCD58B] text-[#121214] font-bold text-[18px] flex items-center justify-center gap-3 hover:bg-[#FFE8A3] transition-colors shadow-[0_0_25px_rgba(252,213,139,0.25)] focus:ring-4 focus:ring-[#FFE8A3]/50"
              >
                {isPlaying ? <Pause className="w-6 h-6 fill-current" /> : <Play className="w-6 h-6 fill-current" />}
                {isPlaying ? '暂停 [OK]' : '播放 [OK]'}
              </button>
              
              <button 
                onClick={() => { 
                  if (currentEpisode > 1) {
                    soundFX.playSelect(); 
                    setCurrentEpisode(p => p - 1); 
                  } else {
                    soundFX.playBack();
                  }
                }}
                className={`focusable h-14 px-5 rounded-[16px] bg-[#1C1C20] border border-white/5 transition-colors flex items-center gap-2.5 text-[16px] font-medium ${currentEpisode > 1 ? 'text-[#e5e1e4] hover:bg-[#26262C] hover:border-white/10' : 'text-[#8C8C96] opacity-50 cursor-not-allowed'}`}
              >
                <SkipBack className="w-5 h-5 text-[#8C8C96]" /> 上一集
              </button>
              <button 
                onClick={() => { 
                  if (currentEpisode < 28) {
                    soundFX.playSelect(); 
                    setCurrentEpisode(p => p + 1); 
                  } else {
                    soundFX.playBack();
                  }
                }}
                className={`focusable h-14 px-5 rounded-[16px] bg-[#1C1C20] border border-white/5 transition-colors flex items-center gap-2.5 text-[16px] font-medium ${currentEpisode < 28 ? 'text-[#e5e1e4] hover:bg-[#26262C] hover:border-white/10' : 'text-[#8C8C96] opacity-50 cursor-not-allowed'}`}
              >
                下一集 <SkipForward className="w-5 h-5 text-[#8C8C96]" />
              </button>
              <button 
                onClick={() => { soundFX.playSelect(); setShowEpisodes(!showEpisodes); setShowSettings(false); }}
                className={`focusable h-14 px-6 rounded-[16px] bg-[#1C1C20] border transition-colors flex items-center gap-3 text-[16px] font-medium ${showEpisodes ? 'border-[#FCD58B] text-[#FCD58B] bg-[#E5A958]/10' : 'border-white/5 text-[#e5e1e4] hover:bg-[#26262C] hover:border-white/10'}`}
              >
                <List className="w-5 h-5 text-[#E5A958]" /> 选集 ({currentEpisode}/28)
              </button>
            </div>
            
            <div className="flex items-center gap-3">
              <button className="focusable h-12 px-5 rounded-[14px] bg-[#1C1C20] border border-white/5 text-[#E5A958] text-[15px] font-bold hover:bg-[#26262C] transition-colors flex items-center gap-2">
                <div className="w-2 h-2 rounded-full bg-[#E5A958]"></div> 弹幕 开
              </button>
              <button 
                onClick={() => { soundFX.playSelect(); setActiveSpeed(s => s === '1.0x' ? '1.5x' : '1.0x'); }}
                className="focusable h-12 px-5 rounded-[14px] bg-[#1C1C20] border border-white/5 text-[#e5e1e4] text-[15px] hover:bg-[#26262C] transition-colors font-mono font-bold"
              >
                {activeSpeed}
              </button>
              <button 
                onClick={() => { soundFX.playSelect(); setActiveQuality(q => q.includes('4K') ? '1080P' : '4K 原盘'); }}
                className="focusable h-12 px-5 rounded-[14px] bg-[#1C1C20] border border-[#E5A958]/30 text-[#FCD58B] text-[15px] font-bold hover:bg-[#26262C] transition-colors"
              >
                {activeQuality}
              </button>
              <button 
                onClick={() => { soundFX.playSelect(); setActiveAudio(a => a.includes('杜比') ? '双声道原声' : '杜比全景声 7.1'); }}
                className="focusable h-12 px-5 rounded-[14px] bg-[#1C1C20] border border-white/5 text-[#e5e1e4] text-[15px] hover:bg-[#26262C] transition-colors flex items-center gap-2.5"
              >
                <Volume2 className="w-4 h-4 text-[#8C8C96]" /> {activeAudio.split(' ')[0]}
              </button>
              <button className="focusable h-12 px-5 rounded-[14px] bg-[#1C1C20] border border-white/5 text-[#e5e1e4] text-[15px] hover:bg-[#26262C] transition-colors">
                双语字幕
              </button>
              <button 
                onClick={() => { soundFX.playSelect(); setShowSettings(!showSettings); }}
                className="focusable h-12 w-12 rounded-[14px] bg-[#1C1C20] border border-white/5 text-[#e5e1e4] hover:bg-[#26262C] hover:text-[#E5A958] transition-colors flex items-center justify-center focus:border-[#E5A958]"
              >
                <Settings className="w-5 h-5" />
              </button>
            </div>
          </div>

          {/* Hints */}
          <div className="flex items-center justify-between mt-5 border-t border-white/5 pt-5 text-[13px] text-[#8C8C96] tracking-wide">
            <div className="flex items-center gap-8">
              <div className="flex items-center gap-2.5"><span className="px-2 py-1 rounded bg-white/10 font-mono text-[11px] font-bold text-white tracking-widest">{'< >'}</span> 快进 / 快退</div>
              <div className="flex items-center gap-2.5"><span className="px-2 py-1 rounded bg-white/10 font-mono text-[11px] font-bold text-white tracking-widest">{'^ v'}</span> 呼出选集 / 切换音画轨</div>
              <div className="flex items-center gap-2.5"><span className="px-2 py-1 rounded bg-white/10 font-mono text-[11px] font-bold text-white">OK</span> 暂停 / 确定</div>
              <div className="flex items-center gap-2.5"><span className="px-2 py-1 rounded bg-white/10 font-mono text-[11px] font-bold text-white">Menu</span> 快速设置面板</div>
            </div>
            <div>3秒无按键自动沉浸隐藏</div>
          </div>
          
        </div>
      </div>
    </div>
  );
};
