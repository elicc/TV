import React, { useState } from 'react';
import { MovieItem } from '../types/tv';
import { POSTER_MOVIES, MORE_MOVIES } from '../data/mockData';
import { soundFX } from '../utils/sound';
import { Search, Delete, RotateCcw, TrendingUp } from 'lucide-react';

interface SearchViewProps {
  onSelectMovie: (movie: MovieItem) => void;
}

export const SearchView: React.FC<SearchViewProps> = ({ onSelectMovie }) => {
  const [query, setQuery] = useState('');
  const allMovies = [...POSTER_MOVIES, ...MORE_MOVIES];

  const keys = [
    'A', 'B', 'C', 'D', 'E', 'F',
    'G', 'H', 'I', 'J', 'K', 'L',
    'M', 'N', 'O', 'P', 'Q', 'R',
    'S', 'T', 'U', 'V', 'W', 'X',
    'Y', 'Z', '1', '2', '3', '4',
    '5', '6', '7', '8', '9', '0',
  ];

  const hotSearches = ['沙丘 2', '奥本海默', '间谍过家家', '星际穿越', '繁花', '异形', '三体'];

  const filteredMovies = query.trim()
    ? allMovies.filter(
        (m) => m.title.toLowerCase().includes(query.toLowerCase()) || m.englishTitle.toLowerCase().includes(query.toLowerCase())
      )
    : allMovies.slice(0, 6);

  return (
    <div className="relative min-h-[calc(100vh-140px)] px-6 lg:px-[72px] py-4 select-none">
      <div className="grid grid-cols-12 gap-8">
        <div className="col-span-12 lg:col-span-5 space-y-5">
          <div className="flex items-center p-3.5 rounded-2xl bg-[#1C1C20] border border-white/10 shadow-inner">
            <Search className="w-5 h-5 text-[#E5A958] mr-3" />
            <div className="flex-1 font-mono text-[20px] text-white tracking-widest min-h-[30px]">
              {query || <span className="text-[#8E8E93] text-[16px]">输入片名首字母...</span>}
            </div>
            {query && (
              <button onClick={() => { soundFX.playFocus(); setQuery(''); }} className="text-xs px-2.5 py-1 rounded bg-white/10 text-[#8E8E93] hover:text-white">清空</button>
            )}
          </div>

          <div className="p-4 rounded-2xl bg-[#1C1C20] border border-white/5 space-y-3">
            <div className="grid grid-cols-6 gap-2">
              {keys.map((k) => (
                <button
                  key={k}
                  onClick={() => { soundFX.playFocus(); setQuery((p) => p + k); }}
                  className="focusable aspect-square rounded-xl bg-[#26262C] hover:bg-[#E5A958] hover:text-[#0E0E10] text-white font-mono font-bold text-[18px] flex items-center justify-center transition-all cursor-pointer"
                  tabIndex={0}
                >
                  {k}
                </button>
              ))}
            </div>
            <div className="grid grid-cols-2 gap-2 pt-2 border-t border-white/5">
              <button onClick={() => { soundFX.playFocus(); setQuery((p) => p.slice(0, -1)); }} className="focusable py-3 rounded-xl bg-white/10 text-white font-bold text-[14px] flex items-center justify-center gap-2" tabIndex={0}>
                <Delete className="w-4 h-4" /> 回退
              </button>
              <button onClick={() => { soundFX.playFocus(); setQuery(''); }} className="focusable py-3 rounded-xl bg-white/10 text-white font-bold text-[14px] flex items-center justify-center gap-2" tabIndex={0}>
                <RotateCcw className="w-4 h-4" /> 重置
              </button>
            </div>
          </div>

          <div className="rounded-2xl bg-[#1C1C20] p-4 border border-white/5">
            <div className="text-[14px] font-bold text-[#E5A958] mb-3 flex items-center gap-2">
              <TrendingUp className="w-4 h-4" /> 全网热搜榜
            </div>
            <div className="flex flex-wrap gap-2">
              {hotSearches.map((h, i) => (
                <button key={h} onClick={() => { soundFX.playSelect(); setQuery(h); }} className="focusable px-3 py-1.5 rounded-lg bg-white/5 text-xs font-medium text-white flex items-center gap-1.5">
                  <span className={`font-mono font-bold ${i < 3 ? 'text-[#E5A958]' : 'text-[#8E8E93]'}`}>{i + 1}</span>
                  <span>{h}</span>
                </button>
              ))}
            </div>
          </div>
        </div>

        <div className="col-span-12 lg:col-span-7 space-y-4">
          <div className="text-[18px] font-bold text-white flex items-center gap-2 mb-2">
            <span className="w-1.5 h-4 rounded-full bg-[#E5A958]" />
            <span>{query ? `搜索结果 (${filteredMovies.length})` : '推荐片源'}</span>
          </div>

          {filteredMovies.length === 0 ? (
            <div className="p-12 rounded-2xl bg-[#1C1C20] border border-dashed border-white/10 text-center">
              <div className="text-[18px] font-bold text-white mb-2">未找到匹配片源</div>
              <div className="text-sm text-[#8E8E93]">请尝试更换关键词</div>
            </div>
          ) : (
            <div className="grid grid-cols-2 md:grid-cols-3 gap-6 pb-12 pt-2">
              {filteredMovies.map((m) => (
                <div key={m.id} onClick={() => { soundFX.playSelect(); onSelectMovie(m); }} className="focusable group rounded-2xl bg-[#202024] p-2 border border-white/5 cursor-pointer" tabIndex={0}>
                  <div className={`aspect-[2/3] rounded-xl bg-gradient-to-b ${m.themeGradient} p-3 flex flex-col justify-between relative overflow-hidden`}>
                    <div className="absolute -right-3 -bottom-6 text-[100px] font-black text-white/10 font-serif">{m.charSymbol}</div>
                    <div className="text-xs px-1.5 py-0.5 rounded bg-black/60 text-[#E5A958] font-bold inline-block self-start border border-white/10">{m.quality}</div>
                    <div className="relative z-10">
                      <div className="text-xs text-[#E5A958]/80 font-mono">{m.year}</div>
                      <div className="text-[18px] font-bold text-white line-clamp-1 drop-shadow-md">{m.title}</div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
