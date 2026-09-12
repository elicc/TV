import React from 'react';
import { MovieItem } from '../types/tv';
import { soundFX } from '../utils/sound';
import { Play, Heart, BookmarkCheck, ArrowLeft, Star, Film, MonitorPlay } from 'lucide-react';

interface DetailViewProps {
  movie: MovieItem;
  onBack: () => void;
  onPlay: (movie: MovieItem) => void;
  isFavorite: boolean;
  onToggleFavorite: (movie: MovieItem) => void;
}

export const DetailView: React.FC<DetailViewProps> = ({
  movie,
  onBack,
  onPlay,
  isFavorite,
  onToggleFavorite,
}) => {
  return (
    <div className="absolute inset-0 z-40 bg-[#0E0E10] flex flex-col select-none animate-fadeIn overflow-hidden">
      {/* Background Graphic */}
      <div className={`absolute top-0 right-0 w-[70vw] h-full bg-gradient-to-l ${movie.themeGradient} opacity-40 pointer-events-none`}></div>
      <div className="absolute top-0 right-0 w-[70vw] h-full bg-gradient-to-b from-transparent to-[#0E0E10] opacity-80 pointer-events-none"></div>

      {/* Decorative Character */}
      <div className="absolute -right-20 top-20 text-[400px] font-black text-white/[0.04] font-serif select-none pointer-events-none tracking-tighter">
        {movie.charSymbol}
      </div>

      <div className="relative z-10 flex flex-col h-full px-6 lg:px-[72px] py-12">
        {/* Top bar */}
        <div className="mb-12">
          <button
            onClick={() => {
              soundFX.playBack();
              onBack();
            }}
            className="focusable px-5 py-2.5 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center gap-2 transition-all cursor-pointer font-bold text-[15px]"
          >
            <ArrowLeft className="w-5 h-5" />
            <span>返回 (Esc)</span>
          </button>
        </div>

        {/* Content Area */}
        <div className="flex-1 flex gap-16 items-center">
          {/* Left: Info */}
          <div className="flex-1 max-w-[800px] entrance-stagger-1">
            <div className="flex items-center gap-3 mb-4">
              <span className="px-2.5 py-0.5 rounded bg-[#E5A958] text-[#0E0E10] text-[13px] font-black tracking-wide">
                {movie.quality}
              </span>
              <span className="text-[13px] px-2 py-0.5 rounded bg-white/10 text-white font-mono border border-white/20">
                {movie.audio}
              </span>
              <span className="text-[15px] text-[#E5A958] flex items-center gap-1 font-bold">
                <Star className="w-4 h-4 fill-current" />
                {movie.rating.toFixed(1)} 分
              </span>
            </div>

            <h1 className="text-[54px] lg:text-[72px] font-extrabold text-white tracking-tight leading-[1.1] mb-2 drop-shadow-xl">
              {movie.title}
            </h1>
            
            <div className="text-[16px] font-mono text-[#E5A958]/90 tracking-widest uppercase mb-6 flex items-center gap-3">
              <span>{movie.englishTitle}</span>
              <span className="w-1.5 h-1.5 rounded-full bg-white/20"></span>
              <span>{movie.year}</span>
              <span className="w-1.5 h-1.5 rounded-full bg-white/20"></span>
              <span>{movie.director}</span>
            </div>

            <p className="text-[19px] lg:text-[22px] text-[#C6C6CD] leading-relaxed mb-10 opacity-90 max-w-[700px]">
              {movie.description}
            </p>

            <div className="flex flex-wrap gap-8 text-[15px] font-mono text-[#8E8E93] border-y border-white/10 py-5 mb-10">
              <div className="flex items-center gap-2">
                <Film className="w-4 h-4 text-white/40" />
                <span className="text-white">类型：</span>
                <span>{movie.tags.join(' / ')}</span>
              </div>
              <div className="flex items-center gap-2">
                <MonitorPlay className="w-4 h-4 text-white/40" />
                <span className="text-white">时长：</span>
                <span>{movie.duration}</span>
              </div>
              <div>
                <span className="text-white">默认解析路线：</span>
                <span className="text-[#E5A958] font-bold">{movie.sourceLine}</span>
              </div>
            </div>

            <div className="flex items-center gap-5 entrance-stagger-2">
              <button
                onClick={() => {
                  soundFX.playSelect();
                  onPlay(movie);
                }}
                className="focusable px-10 py-5 rounded-2xl bg-[#E5A958] text-[#0E0E10] font-black text-[22px] flex items-center gap-3 hover:scale-105 transition-transform"
                tabIndex={0}
              >
                <Play className="w-7 h-7 fill-current" />
                <span>立即播放</span>
                {movie.lastProgress && (
                  <span className="text-[14px] px-2.5 py-0.5 rounded-lg bg-black/25 text-[#0E0E10] font-bold font-mono ml-2">
                    {movie.lastProgress}
                  </span>
                )}
              </button>

              <button
                onClick={() => {
                  soundFX.playSelect();
                  onToggleFavorite(movie);
                }}
                className="focusable px-8 py-5 rounded-2xl bg-white/10 hover:bg-white/20 text-white font-bold text-[18px] flex items-center gap-3 transition-all"
                tabIndex={0}
              >
                {isFavorite ? (
                  <>
                    <BookmarkCheck className="w-6 h-6 text-[#E5A958]" />
                    <span>已收藏</span>
                  </>
                ) : (
                  <>
                    <Heart className="w-6 h-6" />
                    <span>加入收藏</span>
                  </>
                )}
              </button>
            </div>
          </div>

          {/* Right: Large Poster */}
          <div className="hidden lg:block w-[400px] shrink-0 entrance-stagger-3">
            <div className={`w-full aspect-[2/3] rounded-3xl bg-gradient-to-b ${movie.themeGradient} border-2 border-white/10 shadow-[0_30px_60px_-15px_rgba(0,0,0,0.8)] relative overflow-hidden flex flex-col justify-end p-8`}>
              <div className="text-[160px] font-black text-white/10 absolute -right-4 -top-8 font-serif">
                {movie.charSymbol}
              </div>
              <div className="relative z-10">
                <div className="text-[32px] font-black text-white drop-shadow-lg">{movie.title.split('：')[0]}</div>
                <div className="text-[14px] font-mono text-[#E5A958]">{movie.englishTitle}</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
