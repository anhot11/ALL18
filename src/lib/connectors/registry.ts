import { FilterOptions, MediaItem } from '../../types/media';
import { ProviderConnector } from './base';
import { RedTubeConnector } from './redtube';
import { EpornerConnector } from './eporner';
import { Rule34Connector } from './rule34';
import { DanbooruConnector } from './danbooru';
import { GelbooruConnector } from './gelbooru';
import { RedGifsConnector } from './redgifs';
import { SpankBangConnector } from './spankbang';
import { XVideosConnector } from './xvideos';
import { XnxxConnector } from './xnxx';
import { HentaiMamaConnector } from './hentaimama';
import { PornhubConnector } from './pornhub';
import { getAllProviders } from '../db';
import { cache } from '../cache';

class ConnectorRegistry {
  private connectors = new Map<string, ProviderConnector>();

  constructor() {
    this.register(new RedTubeConnector());
    this.register(new EpornerConnector());
    this.register(new Rule34Connector());
    this.register(new DanbooruConnector());
    this.register(new GelbooruConnector());
    this.register(new RedGifsConnector());
    this.register(new SpankBangConnector());
    this.register(new XVideosConnector());
    this.register(new XnxxConnector());
    this.register(new HentaiMamaConnector());
    this.register(new PornhubConnector());
  }

  register(connector: ProviderConnector) {
    this.connectors.set(connector.id, connector);
  }

  getConnector(id: string): ProviderConnector | undefined {
    return this.connectors.get(id);
  }

  getAll(): ProviderConnector[] {
    return Array.from(this.connectors.values());
  }

  getEnabledConnectors(): ProviderConnector[] {
    try {
      const dbProviders = getAllProviders();
      const enabledIds = new Set(dbProviders.filter((p) => p.enabled).map((p) => p.id));
      return Array.from(this.connectors.values()).filter((c) => enabledIds.has(c.id));
    } catch {
      return Array.from(this.connectors.values());
    }
  }

  async searchAll(query: string, page = 1, filters?: FilterOptions): Promise<MediaItem[]> {
    const cacheKey = `search:${query}:${page}:${JSON.stringify(filters || {})}`;
    const cached = cache.get<MediaItem[]>(cacheKey);
    if (cached) return cached;

    let targetConnectors = this.getEnabledConnectors();
    if (filters?.provider && filters.provider !== 'all') {
      const specific = this.getConnector(filters.provider);
      if (specific) targetConnectors = [specific];
    } else if (filters?.type && filters.type !== 'all') {
      targetConnectors = targetConnectors.filter((c) => {
        if (filters.type === 'video') return c.category === 'tube';
        if (filters.type === 'gif') return c.category === 'gif';
        if (filters.type === 'anime') return c.category === 'anime';
        if (filters.type === 'image') return c.category === 'image';
        return true;
      });
    }

    // Run parallel with individual timeout protection (6s max per provider)
    const promises = targetConnectors.map(async (c) => {
      try {
        const timeoutPromise = new Promise<MediaItem[]>((resolve) =>
          setTimeout(() => resolve([]), 6000)
        );
        const searchPromise = c.search(query, page, filters);
        return await Promise.race([searchPromise, timeoutPromise]);
      } catch (err) {
        console.warn(`[Search error] ${c.id}:`, err);
        return [];
      }
    });

    const resultsArray = await Promise.all(promises);
    const merged = this.interleaveAndDeduplicate(resultsArray);

    // Apply duration filters if specified
    const filtered = this.applyFilters(merged, filters);
    cache.set(cacheKey, filtered, 1800, 'search'); // 30 min cache
    return filtered;
  }

  async getAggregatedFeed(
    mode: 'all18' | 'tiktok' | 'x' | 'trending' | 'latest',
    page = 1,
    filters?: FilterOptions
  ): Promise<MediaItem[]> {
    const cacheKey = `feed:${mode}:${page}:${JSON.stringify(filters || {})}`;
    const cached = cache.get<MediaItem[]>(cacheKey);
    if (cached) return cached;

    let targetConnectors = this.getEnabledConnectors();

    if (mode === 'tiktok') {
      // Prioritize fast short looping clips and GIF providers (RedGIFs, Rule34, Eporner, SpankBang)
      const priorityOrder = ['redgifs', 'rule34', 'eporner', 'spankbang', 'redtube'];
      targetConnectors = targetConnectors.sort((a, b) => {
        const indexA = priorityOrder.indexOf(a.id);
        const indexB = priorityOrder.indexOf(b.id);
        return (indexA === -1 ? 99 : indexA) - (indexB === -1 ? 99 : indexB);
      });
    } else if (mode === 'x') {
      // Variety feed: tubes, GIFs, anime art
      targetConnectors = targetConnectors.sort(() => Math.random() - 0.5);
    }

    if (filters?.provider && filters.provider !== 'all') {
      const specific = this.getConnector(filters.provider);
      if (specific) targetConnectors = [specific];
    }

    const promises = targetConnectors.map(async (c) => {
      try {
        const timeoutPromise = new Promise<MediaItem[]>((resolve) =>
          setTimeout(() => resolve([]), 6000)
        );
        const fetchPromise =
          mode === 'latest'
            ? c.getLatest(page)
            : c.getTrending(page, filters?.category);
        return await Promise.race([fetchPromise, timeoutPromise]);
      } catch (err) {
        console.warn(`[Feed error] ${c.id}:`, err);
        return [];
      }
    });

    const resultsArray = await Promise.all(promises);
    let merged = this.interleaveAndDeduplicate(resultsArray);

    if (mode === 'tiktok') {
      // For TikTok mode, prioritize items under 90s or GIFs
      merged = merged.filter((item) => {
        if (item.type === 'gif') return true;
        if (!item.duration) return true;
        return item.duration <= 120;
      });
    }

    const filtered = this.applyFilters(merged, filters);
    cache.set(cacheKey, filtered, 900, 'feed'); // 15 min cache
    return filtered;
  }

  async getVideoDetails(id: string): Promise<MediaItem | null> {
    const cached = cache.get<MediaItem>(`item:${id}`);
    if (cached) return cached;

    const [providerId] = id.split(':');
    const connector = this.getConnector(providerId);
    if (!connector) return null;

    try {
      const details = await connector.getVideoDetails(id);
      if (details) {
        cache.set(`item:${id}`, details, 3600, providerId);
      }
      return details;
    } catch {
      return null;
    }
  }

  private applyFilters(items: MediaItem[], filters?: FilterOptions): MediaItem[] {
    if (!filters) return items;
    let list = [...items];

    if (filters.type && filters.type !== 'all') {
      list = list.filter((i) => i.type === filters.type);
    }

    if (filters.duration && filters.duration !== 'all') {
      if (filters.duration === 'short') {
        list = list.filter((i) => !i.duration || i.duration <= 300);
      } else if (filters.duration === 'medium') {
        list = list.filter((i) => i.duration && i.duration > 300 && i.duration <= 1200);
      } else if (filters.duration === 'long') {
        list = list.filter((i) => i.duration && i.duration > 1200);
      }
    }

    return list;
  }

  private interleaveAndDeduplicate(results: MediaItem[][]): MediaItem[] {
    const seenIds = new Set<string>();
    const seenTitles = new Set<string>();
    const interleaved: MediaItem[] = [];

    const maxLen = Math.max(...results.map((r) => r.length), 0);
    for (let i = 0; i < maxLen; i++) {
      for (const list of results) {
        if (i < list.length) {
          const item = list[i];
          const simplifiedTitle = item.title.toLowerCase().replace(/[^a-z0-9]/g, '').slice(0, 25);
          if (!seenIds.has(item.id) && !seenTitles.has(simplifiedTitle)) {
            seenIds.add(item.id);
            if (simplifiedTitle.length > 5) seenTitles.add(simplifiedTitle);
            interleaved.push(item);
          }
        }
      }
    }

    return interleaved;
  }
}

export const registry = new ConnectorRegistry();
