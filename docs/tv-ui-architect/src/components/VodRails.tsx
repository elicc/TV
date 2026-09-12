import React from 'react';
import { MovieItem } from '../types/tv';
import { soundFX } from '../utils/sound';

interface VodRailsProps {
  movies: MovieItem[];
  onSelectMovie: (movie: MovieItem) => void;
  focusedMovieId?: string;
  onHoverMovie?: (movie: MovieItem) => void;
}

export const VodRails: React.FC<VodRailsProps> = ({
  movies,
  onSelectMovie,
  focusedMovieId,
  onHoverMovie,
}) => {
  return (
    <section className="mt-2 entrance-stagger-3 select-none">
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-baseline gap-4">
          <h2 className="text-[24px] lg:text-[26px] font-bold text-white tracking-wide flex items-center gap-3">
            <span className="w-1.5 h-6 rounded-full bg-[#E5A958]"></span>
            正在热播 · 热门精选
          </h2>
          <span className="text-[16px] lg:text-[17px] text-[#8E8E93]">按 D-pad 下键浏览更多分类行</span>
        </div>
        <div className="flex items-center gap-2 text-[15px] text-[#A2A2A8]">
          <span className="px-2 py-0.5 rounded bg-white/10 text-white font-medium">竖版海报 Grid (2:3)</span>
          <span>· 线路同步完毕</span>
        </div>
      </div>

      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-5 gap-6 pt-2 pb-6" id="poster-cards-row">
        {movies.map((m) => {
          const isSelected = focusedMovieId === m.id;
          return (
            <div
              key={m.id}
              onClick={() => {
                soundFX.playSelect();
                onSelectMovie(m);
              }}
              onMouseEnter={() => {
                soundFX.playFocus();
                onHoverMovie?.(m);
              }}
              className={`focusable group relative rounded-2xl bg-[#202024] p-2 border border-white/5 cursor-pointer focus:outline-none transition-all ${
                isSelected ? 'tv-focused' : ''
              }`}
              tabIndex={0}
              data-movie-id={m.id}
            >
              <div
                className={`card-poster-inner relative w-full aspect-[2/3] rounded-xl overflow-hidden bg-gradient-to-b ${m.themeGradient} flex flex-col justify-between p-3.5 select-none border border-white/5 transition-colors`}
              >
                <div className="absolute -right-4 -bottom-8 text-[130px] font-black text-white/[0.12] leading-none pointer-events-none font-serif select-none group-hover:scale-105 transition-transform">
                  {m.charSymbol}
                </div>
                <div className="relative z-10 flex items-center justify-between">
                  <div className="px-2 py-0.5 rounded bg-black/70 backdrop-blur-md text-[13px] text-[#E5A958] font-bold border border-white/10">
                    {m.badgeTop}
                  </div>
                  <div className="w-6 h-6 rounded-full bg-[#E5A958]/20 flex items-center justify-center text-[10px] text-[#E5A958] font-mono font-bold">
                    {m.rankNum}
                  </div>
                </div>
                <div className="relative z-10 card-meta">
                  <div className="text-[11px] font-mono tracking-widest text-[#E5A958]/80 uppercase">
                    {m.englishTitle}
                  </div>
                  <div className="text-[22px] font-black text-white/90 tracking-wide mt-0.5 font-serif line-clamp-1">
                    {m.title.split('：')[0]}
                  </div>
                  <div className="flex items-center justify-between mt-2 pt-2 border-t border-white/10">
                    <span className="text-[11px] text-white/50 font-mono line-clamp-1">{m.director}</span>
                    <span className="px-1.5 py-0.5 rounded bg-[#E5A958] text-[#0E0E10] text-[11px] font-extrabold shrink-0">
                      {m.quality}
                    </span>
                  </div>
                </div>
              </div>
              <div className="pt-3 px-1 flex flex-col gap-1.5">
                <h3 className="text-[20px] font-bold text-white truncate leading-snug group-hover:text-[#FCD58B] transition-colors">
                  {m.title}
                </h3>
                <p className="text-[14px] text-[#8E8E93] truncate leading-normal">
                  {m.subTitle}
                </p>
              </div>
            </div>
          );
        })}
      </div>
    </section>
  );
};
