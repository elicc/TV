import { useState, useEffect, useRef } from 'react';
import { NavTab, MovieItem, LiveChannel } from './types/tv';
import { HERO_MOVIE, POSTER_MOVIES } from './data/mockData';
import { soundFX } from './utils/sound';
import { Header } from './components/Header';
import { HeroSection } from './components/HeroSection';
import { VodRails } from './components/VodRails';
import { SpecBoard } from './components/SpecBoard';
import { SettingsView } from './components/SettingsView';
import { LiveView } from './components/LiveView';
import { VodView } from './components/VodView';
import { SearchView } from './components/SearchView';
import { FavoritesView } from './components/FavoritesView';
import { PlayerModal } from './components/PlayerModal';
import { DetailView } from './components/DetailView';
import { VirtualRemote } from './components/VirtualRemote';
import { FooterHint } from './components/FooterHint';

export function App() {
  const [currentTab, setCurrentTab] = useState<NavTab>('home');
  const [activeApiSource, setActiveApiSource] = useState('饭太硬 (极速4K线路)');
  const [soundEnabled, setSoundEnabled] = useState(true);
  const [activeKeyHint, setActiveKeyHint] = useState<string | undefined>(undefined);

  const [heroMovie, setHeroMovie] = useState<MovieItem>(HERO_MOVIE);
  const [selectedMovieForDetail, setSelectedMovieForDetail] = useState<MovieItem | null>(null);
  const [selectedMovieForPlayer, setSelectedMovieForPlayer] = useState<MovieItem | null>(null);
  const [selectedChannelForPlayer, setSelectedChannelForPlayer] = useState<LiveChannel | null>(null);

  const [favorites, setFavorites] = useState<MovieItem[]>([POSTER_MOVIES[0], POSTER_MOVIES[3]]);
  const specBoardRef = useRef<HTMLDivElement>(null);

  const handleToggleSound = () => {
    const next = !soundEnabled;
    setSoundEnabled(next);
    soundFX.enabled = next;
    if (next) soundFX.playSelect();
  };

  const handleToggleFavorite = (movie: MovieItem) => {
    setFavorites((prev) => {
      const exists = prev.some((m) => m.id === movie.id);
      if (exists) return prev.filter((m) => m.id !== movie.id);
      return [movie, ...prev];
    });
  };

  const handleRemoveFavorite = (id: string) => {
    setFavorites((prev) => prev.filter((m) => m.id !== id));
  };

  const handleSwitchApiSource = () => {
    soundFX.playSelect();
    const sources = ['饭太硬 (极速4K线路)', '肥猫 VIP 专线 (Spider)', '摸鱼网盘直链仓', '天微 4K 原盘专线'];
    const nextIndex = (sources.indexOf(activeApiSource) + 1) % sources.length;
    setActiveApiSource(sources[nextIndex]);
  };

  const handleSelectTab = (tab: NavTab) => {
    if (tab === 'spec') {
      setCurrentTab('spec');
    } else {
      setCurrentTab(tab);
    }
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const triggerKeyFeedback = (key: 'arrows' | 'ok' | 'menu' | 'back') => {
    setActiveKeyHint(key);
    setTimeout(() => setActiveKeyHint(undefined), 320);
  };

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.target instanceof HTMLInputElement || e.target instanceof HTMLTextAreaElement) return;

      if (e.key === 'ArrowUp' || e.key === 'ArrowDown' || e.key === 'ArrowLeft' || e.key === 'ArrowRight') {
        triggerKeyFeedback('arrows');
        soundFX.playFocus();
      } else if (e.key === 'Enter' || e.key === ' ') {
        triggerKeyFeedback('ok');
        soundFX.playSelect();
      } else if (e.key === 'Escape' || e.key === 'Backspace') {
        triggerKeyFeedback('back');
        soundFX.playBack();
        if (selectedMovieForPlayer || selectedChannelForPlayer) {
          setSelectedMovieForPlayer(null);
          setSelectedChannelForPlayer(null);
        } else if (currentTab === 'details') {
          setCurrentTab('home');
          setSelectedMovieForDetail(null);
        } else if (currentTab !== 'home') {
          setCurrentTab('home');
        }
      } else if (e.key.toLowerCase() === 'm') {
        triggerKeyFeedback('menu');
        handleSwitchApiSource();
      } else if (e.key.toLowerCase() === 's') {
        soundFX.playFocus();
        setCurrentTab('settings');
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [currentTab, selectedMovieForPlayer, selectedChannelForPlayer]);

  const handleRemoteDirection = (dir: 'up' | 'down' | 'left' | 'right') => {
    triggerKeyFeedback('arrows');
    soundFX.playFocus();
    if (dir === 'down' && currentTab === 'home') {
      specBoardRef.current?.scrollIntoView({ behavior: 'smooth' });
    } else if (dir === 'up' && currentTab === 'home') {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    } else if (dir === 'right') {
      const tabs: NavTab[] = ['home', 'vod', 'live', 'fav', 'search', 'spec', 'settings'];
      const nextIdx = (tabs.indexOf(currentTab) + 1) % tabs.length;
      handleSelectTab(tabs[nextIdx]);
    } else if (dir === 'left') {
      const tabs: NavTab[] = ['home', 'vod', 'live', 'fav', 'search', 'spec', 'settings'];
      const nextIdx = (tabs.indexOf(currentTab) - 1 + tabs.length) % tabs.length;
      handleSelectTab(tabs[nextIdx]);
    }
  };

  const handleRemoteSelect = () => {
    triggerKeyFeedback('ok');
    soundFX.playSelect();
    if (!selectedMovieForPlayer && currentTab === 'home') {
      setSelectedMovieForPlayer(heroMovie);
    }
  };

  const handleRemoteBack = () => {
    triggerKeyFeedback('back');
    soundFX.playBack();
    if (selectedMovieForPlayer || selectedChannelForPlayer) {
      setSelectedMovieForPlayer(null);
      setSelectedChannelForPlayer(null);
    } else if (currentTab === 'details') {
      setCurrentTab('home');
    } else {
      setCurrentTab('home');
    }
  };

  const isFavoriteHero = favorites.some((m) => m.id === heroMovie.id);

  // When a movie is clicked for details, we switch to details tab
  const handleOpenDetails = (m: MovieItem) => {
    setSelectedMovieForDetail(m);
    setCurrentTab('details');
  };

  return (
    <div className="relative min-h-screen w-full bg-[#0E0E10] text-[#F3F3F6] overflow-x-hidden flex flex-col justify-between selection:bg-[#E5A958] selection:text-[#0E0E10]">
      <div className="fixed inset-0 pointer-events-none overflow-hidden z-0">
        <div className="absolute -top-32 -right-32 w-[700px] h-[700px] rounded-full bg-[#E5A958]/12 blur-[140px] animate-ambient-light" />
        <div className="absolute -bottom-40 -left-40 w-[650px] h-[650px] rounded-full bg-[#3B2815]/15 blur-[150px] animate-ambient-drift" />
      </div>

      {/* Hide Header on Settings and Details and Player */}
      {currentTab !== 'settings' && currentTab !== 'details' && !(selectedMovieForPlayer || selectedChannelForPlayer) && (
        <Header
          currentTab={currentTab}
          onSelectTab={handleSelectTab}
          onOpenSettings={() => { soundFX.playFocus(); setCurrentTab('settings'); }}
          activeApiSource={activeApiSource}
          onSwitchApiSource={handleSwitchApiSource}
          soundEnabled={soundEnabled}
          onToggleSound={handleToggleSound}
        />
      )}

      <main className="relative z-10 flex-1 flex flex-col justify-between">
        {currentTab === 'home' && (
          <div className="flex flex-col justify-between flex-1">
            <div className="px-6 lg:px-[72px] pt-4 pb-8">
              <HeroSection
                movie={heroMovie}
                onPlay={(m) => setSelectedMovieForPlayer(m)}
                onDetails={handleOpenDetails}
                isFavorite={isFavoriteHero}
                onToggleFavorite={handleToggleFavorite}
                isPrimaryFocused={true}
              />
              <VodRails
                movies={POSTER_MOVIES}
                onSelectMovie={(m) => { setHeroMovie(m); handleOpenDetails(m); }}
                focusedMovieId={heroMovie.id}
                onHoverMovie={(m) => setHeroMovie(m)}
              />
            </div>
            <div ref={specBoardRef}><SpecBoard onTestJumpConfig={() => setCurrentTab('settings')} /></div>
          </div>
        )}

        {currentTab === 'vod' && <VodView onSelectMovie={(m) => { setHeroMovie(m); handleOpenDetails(m); }} />}
        {currentTab === 'live' && <LiveView onPlayChannel={(c) => setSelectedChannelForPlayer(c)} />}
        {currentTab === 'fav' && <FavoritesView favoritesList={favorites} onSelectMovie={(m) => { setHeroMovie(m); setSelectedMovieForPlayer(m); }} onRemoveFavorite={handleRemoveFavorite} />}
        {currentTab === 'search' && <SearchView onSelectMovie={(m) => { setHeroMovie(m); handleOpenDetails(m); }} />}
        {currentTab === 'spec' && <div className="pt-2"><SpecBoard onTestJumpConfig={() => setCurrentTab('settings')} /></div>}
        
        {currentTab === 'settings' && <SettingsView onBack={() => { soundFX.playBack(); setCurrentTab('home'); }} />}
        
        {currentTab === 'details' && selectedMovieForDetail && (
          <DetailView 
            movie={selectedMovieForDetail}
            onBack={() => { soundFX.playBack(); setCurrentTab('home'); }}
            onPlay={(m) => { setSelectedMovieForPlayer(m); }}
            isFavorite={favorites.some((f) => f.id === selectedMovieForDetail.id)}
            onToggleFavorite={handleToggleFavorite}
          />
        )}
      </main>

      {/* Hide Footer on Settings and Details */}
      {currentTab !== 'settings' && currentTab !== 'details' && !(selectedMovieForPlayer || selectedChannelForPlayer) && (
        <FooterHint activeKeyHint={activeKeyHint} />
      )}

      {(selectedMovieForPlayer || selectedChannelForPlayer) && (
        <PlayerModal
          movie={selectedMovieForPlayer}
          channel={selectedChannelForPlayer}
          onClose={() => { setSelectedMovieForPlayer(null); setSelectedChannelForPlayer(null); }}
        />
      )}

      <VirtualRemote
        onDirection={handleRemoteDirection}
        onSelect={handleRemoteSelect}
        onBack={handleRemoteBack}
        onHome={() => { soundFX.playSelect(); setCurrentTab('home'); window.scrollTo({ top: 0, behavior: 'smooth' }); }}
        onMenu={handleSwitchApiSource}
        onToggleSound={handleToggleSound}
        soundEnabled={soundEnabled}
      />
    </div>
  );
}

export default App;
