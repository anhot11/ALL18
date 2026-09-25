import { BaseConnector } from './base';
import { FilterOptions, MediaItem } from '../../types/media';

export class RedTubeConnector extends BaseConnector {
  id = 'redtube';
  name = 'RedTube';
  category = 'tube' as const;
  baseUrl = 'https://www.redtube.com';
  icon = '🔴';

  private apiUrl = 'https://api.redtube.com';

  async search(query: string, page = 1, filters?: FilterOptions): Promise<MediaItem[]> {
    const searchUrl = `${this.apiUrl}/?data=redtube.Videos.searchVideos&output=json&search=${encodeURIComponent(
      query
    )}&page=${page}&thumbsize=medium`;

    const data = await this.fetchJson<any>(searchUrl);
    if (!data || !data.videos) return [];

    return (data.videos || []).map((v: any) => this.transformVideo(v.video));
  }

  async getTrending(page = 1, category?: string): Promise<MediaItem[]> {
    const searchTag = category && category !== 'all' ? category : 'popular';
    const url = `${this.apiUrl}/?data=redtube.Videos.searchVideos&output=json&search=${encodeURIComponent(
      searchTag
    )}&page=${page}&ordering=mostviewed&thumbsize=big`;

    const data = await this.fetchJson<any>(url);
    if (!data || !data.videos) return [];

    return (data.videos || []).map((v: any) => this.transformVideo(v.video));
  }

  async getLatest(page = 1): Promise<MediaItem[]> {
    const url = `${this.apiUrl}/?data=redtube.Videos.searchVideos&output=json&page=${page}&ordering=newest&thumbsize=big`;
    const data = await this.fetchJson<any>(url);
    if (!data || !data.videos) return [];

    return (data.videos || []).map((v: any) => this.transformVideo(v.video));
  }

  async getCategories(): Promise<string[]> {
    const url = `${this.apiUrl}/?data=redtube.Categories.getCategoriesList&output=json`;
    const data = await this.fetchJson<any>(url);
    if (!data || !data.categories) {
      return ['Amateur', 'Blowjob', 'Brunette', 'HD', 'Hardcore', 'Lesbian', 'MILF', 'POV', 'Teen'];
    }
    return (data.categories || []).map((c: any) => c.category);
  }

  async getVideoDetails(id: string): Promise<MediaItem | null> {
    const originalId = id.includes(':') ? id.split(':')[1] : id;
    const url = `${this.apiUrl}/?data=redtube.Videos.getVideoById&video_id=${originalId}&output=json&thumbsize=all`;
    const data = await this.fetchJson<any>(url);
    if (!data || !data.video) return null;

    return this.transformVideo(data.video);
  }

  private transformVideo(v: any): MediaItem {
    const videoId = String(v.video_id || v.id);
    const tags = Array.isArray(v.tags)
      ? v.tags.map((t: any) => (typeof t === 'string' ? t : t.tag_name))
      : [];

    return {
      id: `${this.id}:${videoId}`,
      provider: this.id,
      providerName: this.name,
      title: v.title || 'Untitled RedTube Video',
      description: `Watch ${v.title} on RedTube. Duration: ${v.duration || 'N/A'}.`,
      thumbnail: v.default_thumb || v.thumb || (v.thumbs && v.thumbs[0]?.src) || '',
      preview: v.preview || '',
      duration: this.parseDuration(v.duration),
      type: 'video',
      tags,
      categories: tags.slice(0, 3),
      views: Number(v.views) || 0,
      rating: Number(v.rating) ? Math.round(Number(v.rating) * 20) : undefined, // percentage
      embedUrl: `https://embed.redtube.com/?id=${videoId}&autoplay=0`,
      sourceUrl: v.url || `${this.baseUrl}/${videoId}`,
      createdAt: v.publish_date || new Date().toISOString(),
      author: {
        name: 'RedTube Verified',
        verified: true,
      },
    };
  }

  private parseDuration(dur: any): number {
    if (typeof dur === 'number') return dur;
    if (typeof dur === 'string') {
      const parts = dur.split(':').map(Number);
      if (parts.length === 2) return parts[0] * 60 + parts[1];
      if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
    }
    return 0;
  }
}
