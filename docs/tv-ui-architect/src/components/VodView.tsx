import React, { useState } from 'react';
import { MovieItem } from '../types/tv';
import { POSTER_MOVIES, MORE_MOVIES } from '../data/mockData';
import { soundFX } from '../utils/sound';
import { Filter, LayoutGrid } from 'lucide-react';

interface VodViewProps {
  onSelectMovie: (movie: MovieItem) => void;
}

export const VodView: React.FC<VodViewProps> = ({ onSelectMovie }) => {
  const [activeCategory, setActiveCategory] = useState('全部');
  const [activeSort, setActiveSort] = useState('最热');

  const categories = ['全部', '电影', '剧集', '动漫', '综艺', '纪录片'];
  const sorts = ['最热', '最新', '高分'];

  const allMovies = [...POSTER_MOVIES, ...MORE_MOVIES];

  return (
    <div className="relative min-h-[calc(100vh-140px)] px-6 lg:px-[72px] py-4 select-none">
      <div className="flex flex-col gap-5 mb-8">
        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 text-[15px] font-bold text-[#8E8E93] min-w-[60px]">
            <Filter className="w-4 h-4" /> 分类
          </div>
          <div className="flex gap-2">
            {categories.map((cat) => (
              <button
                key={cat}
                onClick={() => { soundFX.playFocus(); setActiveCategory(cat); }}
                className={`focusable px-5 py-2 rounded-xl font-bold text-[15px] transition-all cursor-pointer ${
                  activeCategory === cat ? 'bg-white/10 text-white' : 'text-[#8E8E93] hover:text-white'
                }`}
                tabIndex={0}
              >
                {cat}
              </button>
            ))}
          </div>
        </div>

        <div className="flex items-center gap-4">
          <div className="flex items-center gap-2 text-[15px] font-bold text-[#8E8E93] min-w-[60px]">
            <LayoutGrid className="w-4 h-4" /> 排序
          </div>
          <div className="flex gap-2">
            {sorts.map((sort) => (
              <button
                key={sort}
                onClick={() => { soundFX.playFocus(); setActiveSort(sort); }}
                className={`focusable px-5 py-2 rounded-xl font-bold text-[15px] transition-all cursor-pointer ${
                  activeSort === sort ? 'bg-white/10 text-white' : 'text-[#8E8E93] hover:text-white'
                }`}
                tabIndex={0}
              >
                {sort}
              </button>
            ))}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 gap-6 pb-12 pt-2">
        {allMovies.map((m) => (
          <div
            key={m.id}
            onClick={() => { soundFX.playSelect(); onSelectMovie(m); }}
            className="focusable group relative rounded-2xl bg-[#202024] p-2 border border-white/5 cursor-pointer"
            tabIndex={0}
          >
            <div className={`card-poster-inner relative w-full aspect-[2/3] rounded-xl overflow-hidden bg-gradient-to-b ${m.themeGradient} flex flex-col justify-between p-3.5`}>
              <div className="flex justify-between items-center z-10">
                <span className="text-xs px-2 py-0.5 rounded bg-black/70 text-[#E5A958] font-bold">{m.badgeTop}</span>
                <span className="text-[11px] font-mono font-bold text-[#4ADE80] bg-[#4ADE80]/20 px-1.5 py-0.5 rounded">{m.rating.toFixed(1)}</span>
              </div>
              <div className="text-[20px] font-bold text-white z-10 drop-shadow-md">{m.title}</div>
              <div className="absolute -right-4 -bottom-8 text-[120px] font-black text-white/10 font-serif pointer-events-none group-hover:scale-105 transition-transform">{m.charSymbol}</div>
            </div>
            <div className="pt-2 px-1 text-sm font-bold text-white truncate">{m.title}</div>
          </div>
        ))}
      </div>
    </div>
  );
};
