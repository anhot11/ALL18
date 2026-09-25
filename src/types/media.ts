export type MediaType = 'video' | 'gif' | 'image' | 'anime';

export interface MediaAuthor {
  name: string;
  avatar?: string;
  verified?: boolean;
}

export interface MediaItem {
  id: string; // Unique format: `${provider}:${originalId}`
  provider: string;
  providerName?: string;
  title: string;
  description?: string;
  thumbnail: string;
  preview?: string; // Animated GIF or short preview video
  duration?: number; // In seconds
  type: MediaType;
  tags: string[];
  categories: string[];
  views?: number;
  rating?: number;
  embedUrl?: string; // Safe iframe embed URL
  streamUrl?: string; // Direct mp4 or m3u8 if publicly exposed
  sourceUrl: string; // Link to original source page
  createdAt?: string;
  author?: MediaAuthor;
  likes?: number;
}

export interface FilterOptions {
  category?: string;
  tag?: string;
  type?: MediaType | 'all';
  provider?: string;
  duration?: 'short' | 'medium' | 'long' | 'all'; // short: < 5min (TikTok friendly), medium: 5-20min, long: 20min+
  sort?: 'trending' | 'latest' | 'top_rated' | 'most_viewed';
}

export type UIMode = 'all18' | 'tiktok' | 'x' | 'favorites' | 'settings';
