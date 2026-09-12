export type NavTab = 'home' | 'vod' | 'live' | 'fav' | 'search' | 'settings' | 'spec' | 'details';

export interface MovieItem {
  id: string;
  title: string;
  subTitle: string;
  englishTitle: string;
  director: string;
  year: number;
  tags: string[];
  quality: string;
  audio: string;
  rating: number;
  duration: string;
  description: string;
  charSymbol: string;
  themeGradient: string;
  accentColor: string;
  badgeTop: string;
  rankNum: string;
  sourceLine: string;
  episodesCount?: number;
  lastProgress?: string;
}

export interface IPTVSource {
  id: string;
  name: string;
  isActive: boolean;
  tags: string[];
  channelsCount: number;
  url: string;
  epgRate: number;
  lastChecked: string;
  latency: number;
}

export interface SnifferCoreOption {
  id: 'pie' | 'webview' | 'x5';
  title: string;
  recommendBadge?: string;
  description: string;
  specs: string;
  penetration: string;
}

export interface LiveChannel {
  id: string;
  channelNumber: number;
  name: string;
  category: '央视频道' | '各省卫视' | '4K超清' | '影视轮播';
  quality: '4K' | '1080P';
  currentProgram: string;
  currentProgress: number; // 0-100
  nextProgram: string;
  streamUrl: string;
  epgTime: string;
}
