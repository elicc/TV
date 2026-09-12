import React from 'react';
import { MovieItem } from '../types/tv';
import { Play, Info, Heart, BookmarkCheck } from 'lucide-react';
import { soundFX } from '../utils/sound';

interface HeroSectionProps {
  movie: MovieItem;
  onPlay: (movie: MovieItem) => void;
  onDetails: (movie: MovieItem) => void;
  isFavorite: boolean;
  onToggleFavorite: (movie: MovieItem) => void;
  isPrimaryFocused?: boolean;
}

export const HeroSection: React.FC<HeroSectionProps> = ({
  movie,
  onPlay,
  onDetails,
  isFavorite,
  onToggleFavorite,
  isPrimaryFocused = true,
}) => {
  return (
    <section className="relative z-20 max-w-[1100px] mb-8 entrance-stagger-2 select-none">
      <div className="flex flex-wrap items-center gap-3 mb-3">
        <span className="shimmer-badge px-3 py-1 rounded-md bg-[#E5A958] text-[#0E0E10] text-[15px] font-extrabold tracking-wide uppercase shadow-sm">
          编辑精选 · 今日推荐
        </span>
        <span className="text-[17px] text-[#A2A2A8] font-medium">
          {movie.tags.join(' / ')}
        </span>
        <span className="w-1.5 h-1.5 rounded-full bg-white/30"></span>
        <span className="text-[17px] text-[#A2A2A8] font-medium font-mono">{movie.year}</span>
        <span className="w-1.5 h-1.5 rounded-full bg-white/30"></span>
        <span className="px-2 py-0.5 rounded text-[13px] bg-white/10 text-white font-mono font-bold border border-white/15">
          {movie.quality}
        </span>
        <span className="px-2 py-0.5 rounded text-[13px] bg-white/10 text-[#E5A958] font-mono font-bold border border-[#E5A958]/30">
          {movie.audio}
        </span>
        <span className="px-2 py-0.5 rounded text-[13px] bg-white/5 text-[#4ADE80] font-mono font-bold border border-[#4ADE80]/30">
          ★ {movie.rating.toFixed(1)}
        </span>
      </div>

      <h1 className="text-[44px] lg:text-[52px] font-extrabold text-white tracking-tight leading-[1.15] mb-3 drop-shadow-md">
        {movie.title}
      </h1>

      <p className="text-[19px] lg:text-[20px] text-[#C6C6CD] leading-relaxed line-clamp-2 max-w-[880px] mb-7 font-normal">
        {movie.description}
      </p>

      <div className="flex items-center gap-5">
        <button
          onClick={() => {
            soundFX.playSelect();
            onPlay(movie);
          }}
          className={`focusable px-8 py-4 rounded-2xl bg-[#E5A958] text-[#0E0E10] flex items-center gap-3.5 transition-all focus:outline-none cursor-pointer group shadow-lg ${
            isPrimaryFocused ? 'tv-focus-primary tv-focused' : ''
          }`}
          tabIndex={0}
        >
          <Play className="w-7 h-7 text-[#0E0E10] fill-current group-hover:scale-110 transition-transform" />
          <span className="text-[22px] font-extrabold tracking-wide">立即播放</span>
          {movie.lastProgress && (
            <span className="text-[15px] px-2.5 py-0.5 rounded-lg bg-black/25 text-[#0E0E10] font-bold font-mono">
              {movie.lastProgress}
            </span>
          )}
        </button>

        <button
          onClick={() => {
            soundFX.playSelect();
            onDetails(movie);
          }}
          className="focusable glass-pill px-7 py-4 rounded-2xl text-white flex items-center gap-3 text-[20px] font-semibold hover:bg-white/20 transition-all focus:outline-none cursor-pointer"
          tabIndex={0}
        >
          <Info className="w-6 h-6 text-[#D1D1D8]" />
          <span>影片详情</span>
        </button>

        <button
          onClick={() => {
            soundFX.playSelect();
            onToggleFavorite(movie);
          }}
          aria-label="收藏"
          className="focusable glass-pill w-14 h-14 rounded-2xl flex items-center justify-center text-[#D1D1D8] hover:text-white transition-all focus:outline-none cursor-pointer"
          title={isFavorite ? '已在收藏夹中' : '加入收藏'}
          tabIndex={0}
        >
          {isFavorite ? (
            <BookmarkCheck className="w-6 h-6 text-[#E5A958] fill-[#E5A958]/20" />
          ) : (
            <Heart className="w-6 h-6 hover:text-[#E5A958]" />
          )}
        </button>
      </div>
    </section>
  );
};
