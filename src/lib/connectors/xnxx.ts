import * as cheerio from 'cheerio';
import { BaseConnector } from './base';
import { FilterOptions, MediaItem } from '../../types/media';

export class XnxxConnector extends BaseConnector {
  id = 'xnxx';
  name = 'XNXX';
  category = 'tube' as const;
  baseUrl = 'https://www.xnxx.com';
  icon = '✖️';

  async search(query: string, page = 1, filters?: FilterOptions): Promise<MediaItem[]> {
    const url = `${this.baseUrl}/search/${encodeURIComponent(query)}/${page}`;
    const html = await this.fetchHtml(url);
    if (!html) return [];
    return this.parseHtml(html);
  }

  async getTrending(page = 1, category?: string): Promise<MediaItem[]> {
    const url =
      category && category !== 'all'
        ? `${this.baseUrl}/search/${encodeURIComponent(category)}/${page}`
        : `${this.baseUrl}/best/${page}`;
    const html = await this.fetchHtml(url);
    if (!html) return [];
    return this.parseHtml(html);
  }

  async getLatest(page = 1): Promise<MediaItem[]> {
    const url = `${this.baseUrl}/new/${page}`;
    const html = await this.fetchHtml(url);
    if (!html) return [];
    return this.parseHtml(html);
  }

  async getCategories(): Promise<string[]> {
    return ['amateur', 'blowjob', 'creampie', 'milf', 'pov', 'teen', 'asian', 'latina'];
  }

  async getVideoDetails(id: string): Promise<MediaItem | null> {
    const originalId = id.includes(':') ? id.split(':')[1] : id;
    return {
      id: `${this.id}:${originalId}`,
      provider: this.id,
      providerName: this.name,
      title: `XNXX Video #${originalId}`,
      description: `Watch on XNXX: #${originalId}`,
      thumbnail: `https://img-hw.xvideos-cdn.com/videos/thumbs169poster/${originalId.slice(0, 3)}/${originalId.slice(3, 6)}/${originalId}/${originalId}.jpg`,
      type: 'video',
      tags: ['xnxx', 'tube'],
      categories: ['tube'],
      embedUrl: `https://www.xnxx.com/embedframe/${originalId}`,
      sourceUrl: `${this.baseUrl}/video-${originalId}/`,
    };
  }

  private parseHtml(html: string): MediaItem[] {
    const $ = cheerio.load(html);
    const items: MediaItem[] = [];

    $('.thumb-block, .mozaique .thumb-inside').each((_, el) => {
      const $el = $(el);
      const link = $el.find('a[href*="/video"]').first().attr('href');
      if (!link) return;

      const match = link.match(/\/video-?([a-zA-Z0-9]+)\//) || link.match(/\/video([0-9]+)\//);
      const videoId = match ? match[1] : '';
      if (!videoId) return;

      const title =
        $el.find('.title a, p.title a, a[title]').first().attr('title') ||
        $el.find('.title a, p.title a').first().text().trim() ||
        'XNXX Video';

      const thumb =
        $el.find('img').first().attr('data-src') ||
        $el.find('img').first().attr('src') ||
        '';

      const durationStr = $el.find('.duration').first().text().trim();

      if (videoId && thumb) {
        items.push({
          id: `${this.id}:${videoId}`,
          provider: this.id,
          providerName: this.name,
          title,
          description: `Watch ${title} on XNXX.`,
          thumbnail: thumb,
          duration: this.parseDuration(durationStr),
          type: 'video',
          tags: ['tube', 'amateur'],
          categories: ['tube'],
          embedUrl: `https://www.xnxx.com/embedframe/${videoId}`,
          sourceUrl: link.startsWith('http') ? link : `${this.baseUrl}${link}`,
          createdAt: new Date().toISOString(),
          author: {
            name: 'XNXX Free',
            verified: true,
          },
        });
      }
    });

    return items;
  }

  private parseDuration(dur: string): number {
    if (!dur) return 0;
    const clean = dur.replace(/[^0-9:]/g, '');
    const parts = clean.split(':').map(Number);
    if (parts.length === 2) return parts[0] * 60 + parts[1];
    if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
    return 0;
  }
}
