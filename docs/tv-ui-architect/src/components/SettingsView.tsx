import React, { useState } from 'react';
import { ArrowLeft, Monitor, ShieldCheck, Database, Sliders, Globe, Save } from 'lucide-react';
import { soundFX } from '../utils/sound';
import { SNIFFER_CORES } from '../data/mockData';

interface SettingsViewProps {
  onBack: () => void;
}

export const SettingsView: React.FC<SettingsViewProps> = ({ onBack }) => {
  const [activeTab, setActiveTab] = useState<'sniffer' | 'iptv' | 'ui' | 'about'>('sniffer');
  const [activeCore, setActiveCore] = useState('pie');
  const [iptvUrl, setIptvUrl] = useState('http://ftylive.top/api/v1/iptv/live.m3u8');
  const [timeoutSec, setTimeoutSec] = useState(5);

  const tabs = [
    { id: 'sniffer', label: '网络嗅探设置', icon: Globe },
    { id: 'iptv', label: '直播源与 EPG', icon: Monitor },
    { id: 'ui', label: '界面与交互', icon: Sliders },
    { id: 'about', label: '关于应用', icon: ShieldCheck },
  ];

  return (
    <div className="absolute inset-0 z-40 bg-[#0E0E10] flex flex-col select-none overflow-hidden animate-fadeIn">
      <div className="flex items-center px-6 lg:px-[72px] py-8 border-b border-white/10">
        <button
          onClick={onBack}
          className="focusable px-5 py-2.5 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center gap-2 transition-all cursor-pointer font-bold text-[15px]"
        >
          <ArrowLeft className="w-5 h-5" />
          <span>返回客厅 (Esc)</span>
        </button>
        <h1 className="text-[28px] font-bold text-white ml-8 tracking-wide">偏好设置</h1>
      </div>

      <div className="flex-1 flex h-[calc(100vh-100px)]">
        {/* Left Sidebar - Add scroll-container-safe padding to prevent clipping */}
        <div className="w-[320px] shrink-0 border-r border-white/10 p-6 lg:pl-[72px] flex flex-col gap-2 overflow-y-auto scroll-container-safe">
          {tabs.map((tab) => {
            const Icon = tab.icon;
            const isActive = activeTab === tab.id;
            return (
              <button
                key={tab.id}
                onClick={() => {
                  soundFX.playFocus();
                  setActiveTab(tab.id as any);
                }}
                className={`focusable flex items-center gap-4 p-4 rounded-2xl transition-all font-bold text-[16px] cursor-pointer ${
                  isActive
                    ? 'bg-[#E5A958] text-[#0E0E10]'
                    : 'bg-transparent text-[#8E8E93] hover:bg-white/5 hover:text-white'
                }`}
                tabIndex={0}
              >
                <Icon className="w-6 h-6" />
                <span>{tab.label}</span>
              </button>
            );
          })}
        </div>

        {/* Right Content - Add scroll-container-safe padding */}
        <div className="flex-1 p-8 lg:p-12 overflow-y-auto scroll-container-safe">
          <div className="max-w-[800px]">
            {activeTab === 'sniffer' && (
              <div className="space-y-10 animate-fadeIn">
                <div>
                  <h2 className="text-[22px] font-bold text-white mb-6">默认嗅探内核</h2>
                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {SNIFFER_CORES.map((core) => (
                      <button
                        key={core.id}
                        onClick={() => {
                          soundFX.playSelect();
                          setActiveCore(core.id);
                        }}
                        className={`focusable text-left p-5 rounded-2xl border transition-all cursor-pointer ${
                          activeCore === core.id
                            ? 'bg-[#E5A958]/10 border-[#E5A958] shadow-[0_0_20px_rgba(229,169,88,0.15)]'
                            : 'bg-[#1C1C20] border-white/5 hover:border-white/20'
                        }`}
                        tabIndex={0}
                      >
                        <div className="flex justify-between items-center mb-2">
                          <span className={`text-[18px] font-bold ${activeCore === core.id ? 'text-[#E5A958]' : 'text-white'}`}>
                            {core.title}
                          </span>
                          {core.recommendBadge && (
                            <span className="text-[11px] px-2 py-0.5 rounded bg-[#4ADE80]/20 text-[#4ADE80] font-bold">
                              {core.recommendBadge}
                            </span>
                          )}
                        </div>
                        <p className="text-[14px] text-[#8E8E93] mb-3 leading-relaxed">
                          {core.description}
                        </p>
                        <div className="text-[12px] font-mono text-white/50">{core.specs}</div>
                      </button>
                    ))}
                  </div>
                </div>

                <div>
                  <h2 className="text-[22px] font-bold text-white mb-6">嗅探规则配置</h2>
                  <div className="rounded-2xl bg-[#1C1C20] border border-white/5 p-6 space-y-5">
                    <div>
                      <label className="block text-[14px] font-bold text-[#8E8E93] mb-2">
                        拦截媒体后缀 (Regex)
                      </label>
                      <input
                        type="text"
                        defaultValue="(?i)\.(m3u8|mp4|flv|avi|mkv|mov|ts)(?:\?|$)"
                        className="w-full bg-black/50 border border-white/10 rounded-xl px-4 py-3 text-white font-mono text-[14px] focus:outline-none focus:border-[#E5A958]"
                      />
                    </div>
                    <div className="flex items-center justify-between py-2 border-t border-white/10">
                      <div>
                        <div className="text-[16px] font-bold text-white">广告分片过滤</div>
                        <div className="text-[13px] text-[#8E8E93]">自动剔除疑似插入广告的 TS 短切片</div>
                      </div>
                      <div className="w-12 h-6 rounded-full bg-[#E5A958] flex justify-end items-center px-1">
                        <div className="w-4 h-4 rounded-full bg-[#0E0E10]"></div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {activeTab === 'iptv' && (
              <div className="space-y-10 animate-fadeIn">
                <div>
                  <h2 className="text-[22px] font-bold text-white mb-6">直播源地址 (M3U / TXT)</h2>
                  <div className="flex gap-4">
                    <input
                      type="text"
                      value={iptvUrl}
                      onChange={(e) => setIptvUrl(e.target.value)}
                      className="flex-1 bg-[#1C1C20] border border-white/10 rounded-xl px-5 py-4 text-white font-mono text-[14px] focus:outline-none focus:border-[#E5A958]"
                    />
                    <button className="focusable px-8 py-4 rounded-xl bg-[#E5A958] text-[#0E0E10] font-bold flex items-center gap-2 transition-transform" tabIndex={0}>
                      <Save className="w-5 h-5" />
                      <span>保存</span>
                    </button>
                  </div>
                </div>

                <div className="rounded-2xl bg-[#1C1C20] border border-white/5 p-6 space-y-6">
                  <div>
                    <label className="block text-[16px] font-bold text-white mb-1">切台超时阈值: {timeoutSec} 秒</label>
                    <div className="text-[13px] text-[#8E8E93] mb-4">超过此时间未获取到流媒体数据则自动切换下一线路。</div>
                    <input
                      type="range"
                      min="3"
                      max="15"
                      value={timeoutSec}
                      onChange={(e) => setTimeoutSec(Number(e.target.value))}
                      className="w-full accent-[#E5A958]"
                    />
                  </div>
                  
                  <div className="flex items-center justify-between py-4 border-t border-white/10">
                    <div>
                      <div className="text-[16px] font-bold text-white">开机自动进入直播</div>
                      <div className="text-[13px] text-[#8E8E93]">适合长辈使用的电视模式</div>
                    </div>
                    <div className="w-12 h-6 rounded-full bg-white/10 flex justify-start items-center px-1">
                      <div className="w-4 h-4 rounded-full bg-white/50"></div>
                    </div>
                  </div>
                </div>
              </div>
            )}

            {activeTab === 'ui' && (
              <div className="text-center py-20 text-[#8E8E93]">
                交互与外观设置开发中...
              </div>
            )}
            
            {activeTab === 'about' && (
              <div className="text-center py-20">
                <div className="w-24 h-24 mx-auto bg-gradient-to-br from-[#E5A958] to-[#9E6E2D] rounded-3xl flex items-center justify-center mb-6">
                  <Database className="w-10 h-10 text-[#0E0E10]" />
                </div>
                <h2 className="text-[28px] font-black text-white tracking-wider mb-2">FONGMI TV</h2>
                <div className="text-[#E5A958] font-mono mb-8">Version 2.0.1 (Leanback Build)</div>
                <p className="text-[#8E8E93] max-w-md mx-auto leading-relaxed">
                  基于 React 构建的现代化 Android TV 交互体验。<br/>
                  遵循 10-foot UI 设计规范，深度优化焦点反馈与大屏阅读性。
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
