import React from 'react';
import { ShieldCheck, Monitor, MousePointer2 } from 'lucide-react';
import { soundFX } from '../utils/sound';

export const SpecBoard: React.FC<{ onTestJumpConfig: () => void }> = ({ onTestJumpConfig }) => {
  return (
    <div className="bg-[#18181C] border-t border-white/5 py-12 px-6 lg:px-[72px] mt-8 select-none">
      <div className="max-w-[1200px] mx-auto">
        <div className="flex items-center gap-3 mb-8">
          <ShieldCheck className="w-7 h-7 text-[#E5A958]" />
          <h2 className="text-[26px] font-bold text-white tracking-wide">
            Android TV Leanback 10-foot 视觉设计规范与状态集
          </h2>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          <div
            className="focusable-spec group rounded-3xl bg-[#202024] p-8 border border-white/10 transition-all cursor-pointer"
            tabIndex={0}
            onClick={() => {
              soundFX.playFocus();
              console.log('Focus spec card clicked');
            }}
          >
            <div className="w-14 h-14 rounded-2xl bg-white/5 flex items-center justify-center mb-5 group-focus:bg-[#E5A958]/20 transition-colors">
              <Monitor className="w-7 h-7 text-[#A2A2A8] group-focus:text-[#E5A958]" />
            </div>
            <h3 className="text-[20px] font-bold text-white mb-2">空间焦点态 (Focus State)</h3>
            <p className="text-[15px] text-[#8E8E93] leading-relaxed">
              严格遵循电视端交互，选中目标放大至 <code className="text-[#E5A958] bg-black/40 px-1 rounded">1.05x</code>，
              外发光 <code className="text-[#E5A958] bg-black/40 px-1 rounded">28px blur</code> 的香槟金光晕，
              描边 <code className="text-[#E5A958] bg-black/40 px-1 rounded">3.5dp</code>，确保 3 米外焦点绝对清晰。
            </p>
          </div>

          <div
            className="focusable-spec group rounded-3xl bg-[#202024] p-8 border border-white/10 transition-all cursor-pointer"
            tabIndex={0}
            onClick={() => {
              soundFX.playSelect();
              onTestJumpConfig();
            }}
          >
            <div className="w-14 h-14 rounded-2xl bg-white/5 flex items-center justify-center mb-5 group-focus:bg-[#E5A958]/20 transition-colors">
              <MousePointer2 className="w-7 h-7 text-[#A2A2A8] group-focus:text-[#E5A958]" />
            </div>
            <h3 className="text-[20px] font-bold text-white mb-2">全键盘与遥控器映射</h3>
            <p className="text-[15px] text-[#8E8E93] leading-relaxed mb-4">
              支持物理键盘方向键、回车、Esc。支持呼出虚拟遥控器测试反馈。
            </p>
            <div className="text-[13px] text-[#E5A958] font-bold bg-[#E5A958]/10 px-3 py-1.5 rounded-lg inline-block">
              点击此卡片可跳转设置页测试焦点流转 ➔
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
