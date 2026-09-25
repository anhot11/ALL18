import { BaseConnector } from './base';
import { FilterOptions, MediaItem } from '../../types/media';

export class EpornerConnector extends BaseConnector {
  id = 'eporner';
  name = 'EPORNER';
  category = 'tube' as const;
  baseUrl = 'https://www.eporner.com';
  icon = '⚡';

  private apiUrl = 'https://www.eporner.com/api/v2/web/search/';

  async search(query: string, page = 1, filters?: FilterOptions): Promise<MediaItem[]> {
    const url = `${this.apiUrl}?query=${encodeURIComponent(query)}&per_page=24&page=${page}&thumbsize=big&order=top-weekly`;
    const data = await this.fetchJson<any>(url);
    if (!data || !data.videos) return [];
    return (data.videos || []).map((v: any) => this.transformVideo(v));
  }

  async getTrending(page = 1, category?: string): Promise<MediaItem[]> {
    const query = category && category !== 'all' ? category : '4k';
    const url = `${this.apiUrl}?query=${encodeURIComponent(query)}&per_page=24&page=${page}&thumbsize=big&order=top-weekly`;
    const data = await this.fetchJson<any>(url);
    if (!data || !data.videos) return [];
    return (data.videos || []).map((v: any) => this.transformVideo(v));
  }

  async getLatest(page = 1): Promise<MediaItem[]> {
    const url = `${this.apiUrl}?query=all&per_page=24&page=${page}&thumbsize=big&order=latest`;
    const data = await this.fetchJson<any>(url);
    if (!data || !data.videos) return [];
    return (data.videos || []).map((v: any) => this.transformVideo(v));
  }

  async getCategories(): Promise<string[]> {
    return [
      '4K Ultra HD',
      '1080p Full HD',
      'Amateur',
      'Anal',
      'Asian',
      'BBW',
      'Blowjob',
      'Brunette',
      'College',
      'Creampie',
      'Cumshot',
      'Hardcore',
      'Japanese',
      'Lesbian',
      'MILF',
      'POV',
      'Redhead',
      'Teen (18+)',
      'VR Porn',
    ];
  }

  async getVideoDetails(id: string): Promise<MediaItem | null> {
    const originalId = id.includes(':') ? id.split(':')[1] : id;
    const url = `https://www.eporner.com/api/v2/video/id/?id=${originalId}&thumbsize=big`;
    const data = await this.fetchJson<any>(url);
    if (!data || !data.id) return null;
    return this.transformVideo(data);
  }

  private transformVideo(v: any): MediaItem {
    const videoId = String(v.id);
    const tags = typeof v.keywords === 'string' ? v.keywords.split(',').map((k: string) => k.trim()) : [];

    return {
      id: `${this.id}:${videoId}`,
      provider: this.id,
      providerName: this.name,
      title: v.title || 'EPORNER Ultra HD Video',
      description: `Watch high-definition adult clip ${v.title} on EPORNER. Resolution: ${v.quality || '1080p'}`,
      thumbnail: v.default_thumb?.src || v.thumbs?.[0]?.src || '',
      preview: '',
      duration: Number(v.length_sec) || 0,
      type: 'video',
      tags,
      categories: tags.slice(0, 3),
      views: Number(v.views) || 0,
      rating: Number(v.rate) ? Math.round(Number(v.rate) * 20) : undefined,
      embedUrl: v.embed || `https://www.eporner.com/embed/${videoId}/`,
      sourceUrl: v.url || `${this.baseUrl}/hd-porn/${videoId}/`,
      createdAt: v.added || new Date().toISOString(),
      author: {
        name: 'EPORNER 4K',
        verified: true,
      },
    };
  }
}
