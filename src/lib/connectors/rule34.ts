import { BaseConnector } from './base';
import { FilterOptions, MediaItem, MediaType } from '../../types/media';

export class Rule34Connector extends BaseConnector {
  id = 'rule34';
  name = 'Rule34';
  category = 'anime' as const;
  baseUrl = 'https://rule34.xxx';
  icon = '🎨';

  private apiUrl = 'https://api.rule34.xxx/index.php?page=dapi&s=post&q=index&json=1';

  async search(query: string, page = 1, filters?: FilterOptions): Promise<MediaItem[]> {
    const cleanTag = query.trim().replace(/\s+/g, '_');
    const url = `${this.apiUrl}&limit=24&pid=${page}&tags=${encodeURIComponent(cleanTag)}`;
    const data = await this.fetchJson<any[]>(url);
    if (!Array.isArray(data)) return [];
    return data.map((item) => this.transformItem(item));
  }

  async getTrending(page = 1, category?: string): Promise<MediaItem[]> {
    // Trending high-score posts or video/animated tag
    const tag = category && category !== 'all' ? category.replace(/\s+/g, '_') : 'animated';
    const url = `${this.apiUrl}&limit=24&pid=${page}&tags=${encodeURIComponent(tag)}+sort:score:desc`;
    const data = await this.fetchJson<any[]>(url);
    if (!Array.isArray(data)) return [];
    return data.map((item) => this.transformItem(item));
  }

  async getLatest(page = 1): Promise<MediaItem[]> {
    const url = `${this.apiUrl}&limit=24&pid=${page}&tags=sort:id:desc`;
    const data = await this.fetchJson<any[]>(url);
    if (!Array.isArray(data)) return [];
    return data.map((item) => this.transformItem(item));
  }

  async getCategories(): Promise<string[]> {
    return [
      'animated',
      'video',
      '3d',
      'overwatch',
      'genshin_impact',
      'league_of_legends',
      'pokemon',
      'anime',
      'manga',
      'blender',
      'sfm',
    ];
  }

  async getVideoDetails(id: string): Promise<MediaItem | null> {
    const originalId = id.includes(':') ? id.split(':')[1] : id;
    const url = `${this.apiUrl}&id=${originalId}`;
    const data = await this.fetchJson<any[]>(url);
    if (!Array.isArray(data) || data.length === 0) return null;
    return this.transformItem(data[0]);
  }

  private transformItem(item: any): MediaItem {
    const itemId = String(item.id);
    const fileUrl = item.file_url || '';
    const sampleUrl = item.sample_url || fileUrl;
    const previewUrl = item.preview_url || sampleUrl;
    const rawTags = (item.tags || '').split(' ').filter(Boolean);

    let mediaType: MediaType = 'image';
    if (fileUrl.endsWith('.mp4') || fileUrl.endsWith('.webm')) {
      mediaType = 'anime';
    } else if (fileUrl.endsWith('.gif')) {
      mediaType = 'gif';
    } else {
      mediaType = 'anime';
    }

    return {
      id: `${this.id}:${itemId}`,
      provider: this.id,
      providerName: this.name,
      title: `${rawTags.slice(0, 3).join(' ')} #${itemId}`,
      description: `Rule34 anime animation / artwork. Score: ${item.score || 0}.`,
      thumbnail: previewUrl,
      preview: fileUrl.endsWith('.gif') ? fileUrl : undefined,
      duration: mediaType === 'anime' || fileUrl.endsWith('.mp4') ? 30 : undefined,
      type: mediaType,
      tags: rawTags,
      categories: rawTags.slice(0, 4),
      views: (item.score || 1) * 120,
      rating: Math.min(100, (item.score || 1) * 10),
      streamUrl: fileUrl.endsWith('.mp4') || fileUrl.endsWith('.webm') ? fileUrl : undefined,
      sourceUrl: `https://rule34.xxx/index.php?page=post&s=view&id=${itemId}`,
      createdAt: item.created_at ? new Date(item.created_at).toISOString() : new Date().toISOString(),
      author: {
        name: item.owner || 'Rule34 Creator',
        avatar: previewUrl,
      },
    };
  }
}
