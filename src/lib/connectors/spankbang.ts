import * as cheerio from 'cheerio';
import { BaseConnector } from './base';
import { FilterOptions, MediaItem } from '../../types/media';

export class SpankBangConnector extends BaseConnector {
  id = 'spankbang';
  name = 'SpankBang';
  category = 'tube' as const;
  baseUrl = 'https://spankbang.com';
  icon = '💥';

  async search(query: string, page = 1, filters?: FilterOptions): Promise<MediaItem[]> {
    const searchUrl = `${this.baseUrl}/s/${encodeURIComponent(query)}/${page}/?o=trending`;
    const html = await this.fetchHtml(searchUrl);
    if (!html) return [];
    return this.parseHtmlList(html);
  }

  async getTrending(page = 1, category?: string): Promise<MediaItem[]> {
    const url =
      category && category !== 'all'
        ? `${this.baseUrl}/category/${encodeURIComponent(category)}/${page}/?o=trending`
        : `${this.baseUrl}/trending_videos/${page}/`;

    const html = await this.fetchHtml(url);
    if (!html) return [];
    return this.parseHtmlList(html);
  }

  async getLatest(page = 1): Promise<MediaItem[]> {
    const url = `${this.baseUrl}/new_videos/${page}/`;
    const html = await this.fetchHtml(url);
    if (!html) return [];
    return this.parseHtmlList(html);
  }

  async getCategories(): Promise<string[]> {
    return ['4k', 'trending', 'popular', 'amateur', 'blowjob', 'creampie', 'milf', 'pov', 'teen', 'asian'];
  }

  async getVideoDetails(id: string): Promise<MediaItem | null> {
    const originalId = id.includes(':') ? id.split(':')[1] : id;
    const url = `${this.baseUrl}/${originalId}/video/`;
    const html = await this.fetchHtml(url);
    if (!html) return null;

    const $ = cheerio.load(html);
    const title = $('h1').first().text().trim() || 'SpankBang Video';
    const thumbnail = $('meta[property="og:image"]').attr('content') || '';
    const durationStr = $('.duration, [data-duration]').first().text().trim();

    return {
      id: `${this.id}:${originalId}`,
      provider: this.id,
      providerName: this.name,
      title,
      description: `Watch ${title} on SpankBang.`,
      thumbnail,
      duration: this.parseDuration(durationStr),
      type: 'video',
      tags: ['spankbang', 'hd'],
      categories: ['tube'],
      embedUrl: `https://spankbang.com/${originalId}/embed/`,
      sourceUrl: url,
      author: {
        name: 'SpankBang Creator',
        verified: true,
      },
    };
  }

  private parseHtmlList(html: string): MediaItem[] {
    const $ = cheerio.load(html);
    const items: MediaItem[] = [];

    $('.video-item, .item').each((_, el) => {
      const $el = $(el);
      const link = $el.find('a[href*="/video/"]').first().attr('href') || $el.find('a').first().attr('href');
      if (!link) return;

      const match = link.match(/\/([a-zA-Z0-9]+)\/video/);
      const id = match ? match[1] : link.replace(/[^a-zA-Z0-9]/g, '').slice(-10);

      const title = $el.find('.n, .title, img').first().attr('alt') || $el.find('.n, .title').text().trim() || 'SpankBang Video';
      const thumb = $el.find('img').first().attr('data-src') || $el.find('img').first().attr('src') || '';
      const preview = $el.find('img').first().attr('data-preview') || '';
      const durationStr = $el.find('.l, .duration').first().text().trim();
      const viewsStr = $el.find('.v, .views').first().text().trim();

      if (id && title && thumb) {
        items.push({
          id: `${this.id}:${id}`,
          provider: this.id,
          providerName: this.name,
          title,
          description: `SpankBang Video: ${title}`,
          thumbnail: thumb.startsWith('//') ? `https:${thumb}` : thumb,
          preview: preview.startsWith('//') ? `https:${preview}` : preview,
          duration: this.parseDuration(durationStr),
          type: 'video',
          tags: ['spankbang', 'tube'],
          categories: ['tube'],
          views: this.parseViews(viewsStr),
          embedUrl: `https://spankbang.com/${id}/embed/`,
          sourceUrl: link.startsWith('http') ? link : `${this.baseUrl}${link}`,
          createdAt: new Date().toISOString(),
          author: {
            name: 'SpankBang Verified',
            verified: true,
          },
        });
      }
    });

    return items;
  }

  private parseDuration(dur: string): number {
    if (!dur) return 0;
    const parts = dur.split(':').map(Number);
    if (parts.length === 2) return parts[0] * 60 + parts[1];
    if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
    return 0;
  }

  private parseViews(v: string): number {
    if (!v) return 0;
    const clean = v.toLowerCase().replace(/[^0-9.km]/g, '');
    if (clean.endsWith('m')) return parseFloat(clean) * 1000000;
    if (clean.endsWith('k')) return parseFloat(clean) * 1000;
    return parseInt(clean) || 0;
  }
}
