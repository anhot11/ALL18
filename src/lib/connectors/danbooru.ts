import { BaseConnector } from './base';
import { FilterOptions, MediaItem, MediaType } from '../../types/media';

export class DanbooruConnector extends BaseConnector {
  id = 'danbooru';
  name = 'Danbooru';
  category = 'image' as const;
  baseUrl = 'https://danbooru.donmai.us';
  icon = '🖼️';

  private apiUrl = 'https://danbooru.donmai.us/posts.json';

  async search(query: string, page = 1, filters?: FilterOptions): Promise<MediaItem[]> {
    const cleanTag = query.trim().replace(/\s+/g, '_');
    const url = `${this.apiUrl}?tags=${encodeURIComponent(cleanTag)}&limit=24&page=${page}`;
    const data = await this.fetchJson<any[]>(url);
    if (!Array.isArray(data)) return [];
    return data.map((item) => this.transformItem(item)).filter((x): x is MediaItem => x !== null);
  }

  async getTrending(page = 1, category?: string): Promise<MediaItem[]> {
    const tag = category && category !== 'all' ? category.replace(/\s+/g, '_') : 'order:rank';
    const url = `${this.apiUrl}?tags=${encodeURIComponent(tag)}&limit=24&page=${page}`;
    const data = await this.fetchJson<any[]>(url);
    if (!Array.isArray(data)) return [];
    return data.map((item) => this.transformItem(item)).filter((x): x is MediaItem => x !== null);
  }

  async getLatest(page = 1): Promise<MediaItem[]> {
    const url = `${this.apiUrl}?limit=24&page=${page}`;
    const data = await this.fetchJson<any[]>(url);
    if (!Array.isArray(data)) return [];
    return data.map((item) => this.transformItem(item)).filter((x): x is MediaItem => x !== null);
  }

  async getCategories(): Promise<string[]> {
    return [
      'highres',
      'original',
      'long_hair',
      'virtual_youtuber',
      'fate/grand_order',
      'swimsuit',
      'blush',
      'genshin_impact',
      'blue_archive',
    ];
  }

  async getVideoDetails(id: string): Promise<MediaItem | null> {
    const originalId = id.includes(':') ? id.split(':')[1] : id;
    const url = `https://danbooru.donmai.us/posts/${originalId}.json`;
    const data = await this.fetchJson<any>(url);
    if (!data || !data.id) return null;
    return this.transformItem(data);
  }

  private transformItem(item: any): MediaItem | null {
    if (!item.id || (!item.file_url && !item.preview_file_url && !item.large_file_url)) {
      return null;
    }

    const itemId = String(item.id);
    const fileUrl = item.large_file_url || item.file_url || item.preview_file_url;
    const thumbUrl = item.preview_file_url || fileUrl;
    const tags = (item.tag_string || '').split(' ').filter(Boolean);

    let type: MediaType = 'image';
    if (fileUrl.endsWith('.mp4') || fileUrl.endsWith('.webm')) {
      type = 'anime';
    } else if (fileUrl.endsWith('.gif')) {
      type = 'gif';
    }

    return {
      id: `${this.id}:${itemId}`,
      provider: this.id,
      providerName: this.name,
      title: `${(item.tag_string_character || item.tag_string_artist || 'Danbooru Art').split(' ')[0]} #${itemId}`,
      description: `Tags: ${tags.slice(0, 6).join(', ')}. Artist: ${item.tag_string_artist || 'Unknown'}.`,
      thumbnail: thumbUrl,
      preview: type === 'gif' ? fileUrl : undefined,
      type,
      tags,
      categories: tags.slice(0, 3),
      views: (item.fav_count || 0) * 85,
      rating: Math.min(100, (item.score || 0) * 8),
      streamUrl: type === 'anime' ? fileUrl : undefined,
      sourceUrl: `https://danbooru.donmai.us/posts/${itemId}`,
      createdAt: item.created_at || new Date().toISOString(),
      author: {
        name: item.tag_string_artist || 'Anime Artist',
      },
    };
  }
}
