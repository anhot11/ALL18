import axios, { AxiosRequestConfig } from 'axios';
import { FilterOptions, MediaItem } from '../../types/media';
import { ProviderCategory } from '../../types/provider';

const USER_AGENTS = [
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36',
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36',
  'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36',
  'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1',
];

export interface ProviderConnector {
  id: string;
  name: string;
  category: ProviderCategory;
  baseUrl: string;
  icon?: string;
  search(query: string, page: number, filters?: FilterOptions): Promise<MediaItem[]>;
  getCategories(): Promise<string[]>;
  getTrending(page: number, category?: string): Promise<MediaItem[]>;
  getLatest(page: number): Promise<MediaItem[]>;
  getVideoDetails(id: string): Promise<MediaItem | null>;
}

export abstract class BaseConnector implements ProviderConnector {
  abstract id: string;
  abstract name: string;
  abstract category: ProviderCategory;
  abstract baseUrl: string;
  icon?: string = '🌐';

  protected getRandomUserAgent(): string {
    return USER_AGENTS[Math.floor(Math.random() * USER_AGENTS.length)];
  }

  protected async fetchJson<T = any>(url: string, config?: AxiosRequestConfig): Promise<T | null> {
    try {
      const res = await axios.get<T>(url, {
        headers: {
          'User-Agent': this.getRandomUserAgent(),
          Accept: 'application/json, text/plain, */*',
          ...config?.headers,
        },
        timeout: 10000,
        ...config,
      });
      return res.data;
    } catch (error: any) {
      console.warn(`[Connector:${this.id}] fetchJson error for ${url}:`, error.message);
      return null;
    }
  }

  protected async fetchHtml(url: string, config?: AxiosRequestConfig): Promise<string | null> {
    try {
      const res = await axios.get<string>(url, {
        headers: {
          'User-Agent': this.getRandomUserAgent(),
          Accept: 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
          ...config?.headers,
        },
        timeout: 10000,
        ...config,
      });
      return res.data;
    } catch (error: any) {
      console.warn(`[Connector:${this.id}] fetchHtml error for ${url}:`, error.message);
      return null;
    }
  }

  abstract search(query: string, page: number, filters?: FilterOptions): Promise<MediaItem[]>;
  abstract getCategories(): Promise<string[]>;
  abstract getTrending(page: number, category?: string): Promise<MediaItem[]>;
  abstract getLatest(page: number): Promise<MediaItem[]>;
  abstract getVideoDetails(id: string): Promise<MediaItem | null>;
}
