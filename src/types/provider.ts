export type ProviderCategory = 'tube' | 'gif' | 'image' | 'anime';
export type ProviderQuality = 'high' | 'medium' | 'experimental';

export interface ProviderInfo {
  id: string;
  name: string;
  category: ProviderCategory;
  baseUrl: string;
  icon?: string;
  enabled: boolean;
  priority: number; // 1-100, higher means prioritized in feeds
  quality: ProviderQuality;
  description: string;
  tags: string[];
  lastVerified?: string;
  hasApi: boolean;
  sourceCategory?: string; // from theporndude category
  stats?: {
    totalItemsServed: number;
    lastError?: string;
    responseTimeMs?: number;
  };
}

export interface ScrapedThePornDudeSite {
  name: string;
  url: string;
  description: string;
  category: string;
  tags: string[];
  rank?: number;
  badge?: string;
  isFree: boolean;
}
