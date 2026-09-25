import { BaseConnector } from './base';
import { FilterOptions, MediaItem } from '../../types/media';

export class RedGifsConnector extends BaseConnector {
  id = 'redgifs';
  name = 'RedGIFs';
  category = 'gif' as const;
  baseUrl = 'https://www.redgifs.com';
  icon = '🎞️';

  private apiUrl = 'https://api.redgifs.com/v2';
  private authToken: string | null = null;
  private tokenExpires: number = 0;

  private async getAuthToken(): Promise<string | null> {
    if (this.authToken && Date.now() < this.tokenExpires) {
      return this.authToken;
    }

    try {
      const res = await this.fetchJson<{ token: string }>(`${this.apiUrl}/auth/temporary`);
      if (res && res.token) {
        this.authToken = res.token;
        this.tokenExpires = Date.now() + 20 * 60 * 1000; // 20 mins
        return this.authToken;
      }
    } catch (e) {
      // fallback
    }
    return null;
  }

  async search(query: string, page = 1, filters?: FilterOptions): Promise<MediaItem[]> {
    const token = await this.getAuthToken();
    const headers = token ? { Authorization: `Bearer ${token}` } : {};

    const url = `${this.apiUrl}/gifs/search?search_text=${encodeURIComponent(
      query
    )}&count=20&page=${page}&order=trending`;

    const data = await this.fetchJson<any>(url, { headers });
    if (!data || !data.gfycats) return [];

    return (data.gfycats || []).map((item: any) => this.transformGif(item));
  }

  async getTrending(page = 1, category?: string): Promise<MediaItem[]> {
    const token = await this.getAuthToken();
    const headers = token ? { Authorization: `Bearer ${token}` } : {};

    const tag = category && category !== 'all' ? category : 'hot';
    const url = `${this.apiUrl}/gifs/search?search_text=${encodeURIComponent(
      tag
    )}&count=24&page=${page}&order=trending`;

    const data = await this.fetchJson<any>(url, { headers });
    if (!data || !data.gfycats) return [];

    return (data.gfycats || []).map((item: any) => this.transformGif(item));
  }

  async getLatest(page = 1): Promise<MediaItem[]> {
    const token = await this.getAuthToken();
    const headers = token ? { Authorization: `Bearer ${token}` } : {};

    const url = `${this.apiUrl}/gifs/search?search_text=verified&count=24&page=${page}&order=recent`;
    const data = await this.fetchJson<any>(url, { headers });
    if (!data || !data.gfycats) return [];

    return (data.gfycats || []).map((item: any) => this.transformGif(item));
  }

  async getCategories(): Promise<string[]> {
    return ['hot', 'amateur', 'cosplay', 'milf', 'pov', 'anal', 'hentai', 'petite', 'blowjob', 'creampie'];
  }

  async getVideoDetails(id: string): Promise<MediaItem | null> {
    const originalId = id.includes(':') ? id.split(':')[1] : id;
    const token = await this.getAuthToken();
    const headers = token ? { Authorization: `Bearer ${token}` } : {};

    const url = `${this.apiUrl}/gifs/${originalId}`;
    const data = await this.fetchJson<any>(url, { headers });
    if (!data || !data.gfyItem) return null;

    return this.transformGif(data.gfyItem);
  }

  private transformGif(item: any): MediaItem {
    const gifId = String(item.id || item.gfyId || item.gfyName);
    const mp4Url = item.urls?.hd || item.urls?.sd || item.mp4Url || '';
    const poster = item.urls?.poster || item.posterUrl || item.urls?.thumbnail || '';
    const tags = Array.isArray(item.tags) ? item.tags : [];

    return {
      id: `${this.id}:${gifId}`,
      provider: this.id,
      providerName: this.name,
      title: item.userName ? `@${item.userName}: ${tags.slice(0, 3).join(' ')}` : tags.slice(0, 3).join(' ') || 'RedGIF Short',
      description: `High quality short clip/GIF by ${item.userName || 'Creator'} on RedGIFs.`,
      thumbnail: poster,
      preview: mp4Url,
      duration: Math.round(item.duration || 15),
      type: 'gif',
      tags,
      categories: tags.slice(0, 3),
      views: Number(item.views) || 500,
      likes: Number(item.likes) || 25,
      streamUrl: mp4Url,
      embedUrl: `https://www.redgifs.com/ifr/${gifId}`,
      sourceUrl: `https://www.redgifs.com/watch/${gifId}`,
      createdAt: item.createDate ? new Date(item.createDate * 1000).toISOString() : new Date().toISOString(),
      author: {
        name: item.userName || 'RedGifs Star',
        avatar: poster,
        verified: Boolean(item.verified),
      },
    };
  }
}
