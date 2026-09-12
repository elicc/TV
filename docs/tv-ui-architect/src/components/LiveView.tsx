import React, { useState } from 'react';
import { LiveChannel } from '../types/tv';
import { LIVE_CHANNELS_DATA, IPTV_SOURCES_DATA } from '../data/mockData';
import { soundFX } from '../utils/sound';
import { Play, Tv, RefreshCcw, Activity, AlignLeft, ShieldCheck, CheckCircle2 } from 'lucide-react';

interface LiveViewProps {
  onPlayChannel: (channel: LiveChannel) => void;
}

export const LiveView: React.FC<LiveViewProps> = ({ onPlayChannel }) => {
  const [activeCategory, setActiveCategory] = useState('全部频道');
  const [activeSourceId, setActiveSourceId] = useState(IPTV_SOURCES_DATA[0].id);

  const categories = ['全部频道', '央视频道', '各省卫视', '4K超清', '影视轮播'];

  const filteredChannels =
    activeCategory === '全部频道'
      ? LIVE_CHANNELS_DATA
      : LIVE_CHANNELS_DATA.filter((c) => c.category === activeCategory);

  const activeSource = IPTV_SOURCES_DATA.find((s) => s.id === activeSourceId) || IPTV_SOURCES_DATA[0];

  return (
    <div className="relative min-h-[calc(100vh-140px)] px-6 lg:px-[72px] py-4 select-none">
      <div className="grid grid-cols-12 gap-8 h-full">
        {/* Left Column: Sources & Categories */}
        <div className="col-span-12 lg:col-span-3 space-y-6">
          {/* Source Selection Card (Option A requirement) */}
          <div className="rounded-3xl bg-[#1C1C20] border border-white/5 p-4 space-y-3">
            <div className="flex items-center gap-2 mb-2 px-1">
              <ShieldCheck className="w-4 h-4 text-[#E5A958]" />
              <span className="text-[15px] font-bold text-white">直播调度源</span>
            </div>
            
            {IPTV_SOURCES_DATA.map((source) => (
              <button
                key={source.id}
                onClick={() => {
                  soundFX.playFocus();
                  setActiveSourceId(source.id);
                }}
                className={`focusable w-full text-left p-3 rounded-2xl border transition-all cursor-pointer flex flex-col gap-2 ${
                  activeSourceId === source.id
                    ? 'bg-[#E5A958]/10 border-[#E5A958]/50'
                    : 'bg-white/5 border-transparent hover:bg-white/10'
                }`}
                tabIndex={0}
              >
                <div className="flex justify-between items-start">
                  <span className={`font-bold ${activeSourceId === source.id ? 'text-[#E5A958]' : 'text-white'}`}>
                    {source.name}
                  </span>
                  {activeSourceId === source.id && <CheckCircle2 className="w-4 h-4 text-[#E5A958]" />}
                </div>
                <div className="flex items-center justify-between text-xs font-mono">
                  <span className={activeSourceId === source.id ? 'text-[#E5A958]/80' : 'text-[#8E8E93]'}>
                    {source.channelsCount} 频道
                  </span>
                  <span className="flex items-center gap-1 text-[#4ADE80]">
                    <Activity className="w-3 h-3" /> {source.latency}ms
                  </span>
                </div>
              </button>
            ))}
          </div>

          {/* Categories List */}
          <div className="rounded-3xl bg-[#1C1C20] border border-white/5 p-3">
            <div className="flex items-center gap-2 mb-2 px-2 pt-2">
              <AlignLeft className="w-4 h-4 text-[#8E8E93]" />
              <span className="text-[14px] font-bold text-[#8E8E93]">频道分类</span>
            </div>
            <div className="flex flex-col space-y-1">
              {categories.map((cat) => (
                <button
                  key={cat}
                  onClick={() => {
                    soundFX.playFocus();
                    setActiveCategory(cat);
                  }}
                  className={`focusable w-full text-left px-4 py-3 rounded-xl text-[16px] font-bold transition-all ${
                    activeCategory === cat
                      ? 'bg-white/10 text-white'
                      : 'text-[#8E8E93] hover:text-white hover:bg-white/5'
                  }`}
                  tabIndex={0}
                >
                  {cat}
                </button>
              ))}
            </div>
          </div>
        </div>

        {/* Right Column: Channels List */}
        <div className="col-span-12 lg:col-span-9 flex flex-col h-full">
          <div className="flex items-center justify-between mb-4">
            <div className="text-[20px] font-bold text-white flex items-center gap-2">
              <Tv className="w-5 h-5 text-[#E5A958]" />
              <span>{activeCategory} ({filteredChannels.length})</span>
            </div>
            <div className="text-sm text-[#8E8E93] flex items-center gap-2">
              <RefreshCcw className="w-4 h-4" />
              EPG 已同步 ({activeSource.epgRate}% 覆盖率)
            </div>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pb-12">
            {filteredChannels.map((channel) => (
              <div
                key={channel.id}
                onClick={() => {
                  soundFX.playSelect();
                  onPlayChannel(channel);
                }}
                className="focusable group rounded-2xl bg-[#202024] p-4 border border-white/5 flex items-center gap-4 cursor-pointer"
                tabIndex={0}
              >
                {/* Channel Icon Placeholder */}
                <div className="w-[88px] h-[66px] rounded-xl bg-white/5 flex flex-col items-center justify-center border border-white/10 shrink-0">
                  <div className="text-[20px] font-black text-white">{channel.channelNumber}</div>
                  <div className="text-[10px] text-[#E5A958] font-bold bg-[#E5A958]/20 px-1.5 py-0.5 rounded mt-0.5">
                    {channel.quality}
                  </div>
                </div>

                {/* Channel Info & EPG */}
                <div className="flex-1 min-w-0">
                  <div className="text-[18px] font-bold text-white mb-1.5 group-hover:text-[#FCD58B] transition-colors">
                    {channel.name}
                  </div>
                  
                  {/* Current Program */}
                  <div className="flex items-center justify-between text-[13px] mb-1">
                    <span className="text-[#E5A958] font-bold truncate">正在播放: {channel.currentProgram}</span>
                    <span className="text-[#E5A958] font-mono shrink-0 ml-2">{channel.epgTime}</span>
                  </div>
                  
                  {/* Progress bar */}
                  <div className="w-full h-1.5 bg-white/10 rounded-full mb-1.5 overflow-hidden">
                    <div
                      className="h-full bg-gradient-to-r from-[#E5A958] to-[#FCD58B]"
                      style={{ width: `${channel.currentProgress}%` }}
                    />
                  </div>

                  {/* Next Program */}
                  <div className="text-[12px] text-[#8E8E93] truncate">
                    接下来: {channel.nextProgram}
                  </div>
                </div>

                <div className="w-10 h-10 rounded-full bg-white/5 flex items-center justify-center shrink-0 group-hover:bg-[#E5A958] group-hover:text-[#0E0E10] transition-colors text-white">
                  <Play className="w-4 h-4 fill-current ml-0.5" />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};
