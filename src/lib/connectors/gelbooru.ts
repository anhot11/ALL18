import { BaseConnector } from './base';
import { FilterOptions, MediaItem, MediaType } from '../../types/media';

export class GelbooruConnector extends BaseConnector {
  id = 'gelbooru';
  name = 'Gelbooru';
  category = 'anime' as const;
  baseUrl = 'https://gelbooru.com';
  icon = '🌸';

  private apiUrl = 'https://gelbooru.com/index.php?page=dapi&s=post&q=index&json=1';

  async search(query: string, page = 1, filters?: FilterOptions): Promise<MediaItem[]> {
    const cleanTag = query.trim().replace(/\s+/g, '_');
    const url = `${this.apiUrl}&limit=24&pid=${page}&tags=${encodeURIComponent(cleanTag)}`;
    const data = await this.fetchJson<any>(url);
    const posts = data?.post || (Array.isArray(data) ? data : []);
    return posts.map((p: any) => this.transformPost(p)).filter(Boolean);
  }

  async getTrending(page = 1, category?: string): Promise<MediaItem[]> {
    const tag = category && category !== 'all' ? category.replace(/\s+/g, '_') : 'sort:score:desc';
    const url = `${this.apiUrl}&limit=24&pid=${page}&tags=${encodeURIComponent(tag)}`;
    const data = await this.fetchJson<any>(url);
    const posts = data?.post || (Array.isArray(data) ? data : []);
    return posts.map((p: any) => this.transformPost(p)).filter(Boolean);
  }

  async getLatest(page = 1): Promise<MediaItem[]> {
    const url = `${this.apiUrl}&limit=24&pid=${page}`;
    const data = await this.fetchJson<any>(url);
    const posts = data?.post || (Array.isArray(data) ? data : []);
    return posts.map((p: any) => this.transformPost(p)).filter(Boolean);
  }

  async getCategories(): Promise<string[]> {
    return ['animated', 'video', 'highres', 'wallpaper', 'breasts', 'cleavage', 'blonde_hair'];
  }

  async getVideoDetails(id: string): Promise<MediaItem | null> {
    const originalId = id.includes(':') ? id.split(':')[1] : id;
    const url = `${this.apiUrl}&id=${originalId}`;
    const data = await this.fetchJson<any>(url);
    const posts = data?.post || (Array.isArray(data) ? data : []);
    if (!posts.length) return null;
    return this.transformPost(posts[0]);
  }

  private transformPost(item: any): MediaItem | null {
    if (!item || !item.id) return null;

    const itemId = String(item.id);
    const fileUrl = item.file_url || '';
    const previewUrl = item.preview_url || fileUrl;
    const tags = (item.tags || '').split(' ').filter(Boolean);

    let type: MediaType = 'anime';
    if (fileUrl.endsWith('.mp4') || fileUrl.endsWith('.webm')) {
      type = 'anime';
    } else if (fileUrl.endsWith('.gif')) {
      type = 'gif';
    } else {
      type = 'image';
    }

    return {
      id: `${this.id}:${itemId}`,
      provider: this.id,
      providerName: this.name,
      title: `${tags[0] || 'Gelbooru'} #${itemId}`,
      description: `Gelbooru artwork/animation. Rating: ${item.rating || 'explicit'}.`,
      thumbnail: previewUrl,
      preview: type === 'gif' ? fileUrl : undefined,
      duration: type === 'anime' ? 30 : undefined,
      type,
      tags,
      categories: tags.slice(0, 3),
      views: (item.score || 1) * 90,
      rating: Math.min(100, (item.score || 1) * 10),
      streamUrl: type === 'anime' ? fileUrl : undefined,
      sourceUrl: `https://gelbooru.com/index.php?page=post&s=view&id=${itemId}`,
      createdAt: item.created_at || new Date().toISOString(),
      author: {
        name: 'Gelbooru Artist',
      },
    };
  }
}
