import React from 'react';
import { MovieItem } from '../types/tv';
import { POSTER_MOVIES } from '../data/mockData';
import { soundFX } from '../utils/sound';
import { Clock, Bookmark, Play, Trash2 } from 'lucide-react';

interface FavoritesViewProps {
  onSelectMovie: (movie: MovieItem) => void;
  favoritesList: MovieItem[];
  onRemoveFavorite: (movieId: string) => void;
}

export const FavoritesView: React.FC<FavoritesViewProps> = ({
  onSelectMovie,
  favoritesList,
  onRemoveFavorite,
}) => {
  const continueWatching = [
    { movie: POSTER_MOVIES[0], progressText: '已看 38:15 / 166:00', percent: 23 },
    { movie: POSTER_MOVIES[1], progressText: '已看 112:00 / 180:00', percent: 62 },
    { movie: POSTER_MOVIES[3], progressText: '第 12 集 · 已看 24:00', percent: 54 },
  ];

  return (
    <div className="relative min-h-[calc(100vh-140px)] px-6 lg:px-[72px] py-4 select-none space-y-10">
      <div>
        <div className="flex items-center gap-2 text-[22px] font-bold text-white mb-4">
          <Clock className="w-5 h-5 text-[#E5A958]" />
          <span>最近续播 (按 OK 键即刻回到上次播放点)</span>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6 pt-2">
          {continueWatching.map(({ movie, progressText, percent }) => (
            <div
              key={movie.id}
              onClick={() => { soundFX.playSelect(); onSelectMovie(movie); }}
              className="focusable group rounded-2xl bg-[#202024] p-3 border border-white/5 cursor-pointer"
              tabIndex={0}
            >
              <div className={`relative aspect-video rounded-xl bg-gradient-to-r ${movie.themeGradient} p-4 flex flex-col justify-between overflow-hidden`}>
                <div className="flex justify-between items-center">
                  <span className="text-xs px-2 py-0.5 rounded bg-black/60 text-[#E5A958] font-bold">{movie.quality}</span>
                  <div className="w-8 h-8 rounded-full bg-[#E5A958] text-[#0E0E10] flex items-center justify-center shadow-lg group-hover:scale-110 transition-transform">
                    <Play className="w-4 h-4 fill-current ml-0.5" />
                  </div>
                </div>
                <div>
                  <div className="text-[18px] font-bold text-white drop-shadow-md">{movie.title}</div>
                  <div className="text-xs text-[#E5A958] font-mono mt-1">{progressText}</div>
                </div>
                <div className="absolute bottom-0 left-0 right-0 h-1.5 bg-white/20">
                  <div className="h-full bg-[#E5A958]" style={{ width: `${percent}%` }} />
                </div>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div>
        <div className="flex items-center gap-2 text-[22px] font-bold text-white mb-4">
          <Bookmark className="w-5 h-5 text-[#E5A958]" />
          <span>我的收藏夹 ({favoritesList.length})</span>
        </div>
        {favoritesList.length === 0 ? (
          <div className="p-12 rounded-2xl bg-[#1C1C20] border border-dashed border-white/10 text-center text-[#8E8E93]">
            暂无收藏片源，在首页或详情页点击收藏按钮即可添加。
          </div>
        ) : (
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-5 gap-6 pt-2 pb-12">
            {favoritesList.map((m) => (
              <div
                key={m.id}
                onClick={() => { soundFX.playSelect(); onSelectMovie(m); }}
                className="focusable group relative rounded-2xl bg-[#202024] p-2 border border-white/5 cursor-pointer"
                tabIndex={0}
              >
                <div className={`card-poster-inner relative w-full aspect-[2/3] rounded-xl overflow-hidden bg-gradient-to-b ${m.themeGradient} flex flex-col justify-between p-3.5`}>
                  <div className="flex justify-between items-center z-10">
                    <span className="text-xs px-2 py-0.5 rounded bg-black/70 text-[#E5A958] font-bold">{m.badgeTop}</span>
                    <button
                      onClick={(e) => { e.stopPropagation(); soundFX.playFocus(); onRemoveFavorite(m.id); }}
                      className="w-7 h-7 rounded-full bg-black/60 hover:bg-[#EF4444] text-white flex items-center justify-center transition-colors"
                      title="取消收藏"
                    >
                      <Trash2 className="w-3.5 h-3.5" />
                    </button>
                  </div>
                  <div className="text-[20px] font-bold text-white z-10">{m.title}</div>
                  <div className="absolute -right-4 -bottom-8 text-[120px] font-black text-white/10 font-serif pointer-events-none group-hover:scale-105 transition-transform">{m.charSymbol}</div>
                </div>
                <div className="pt-2 px-1 text-sm font-bold text-white truncate">{m.title}</div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
