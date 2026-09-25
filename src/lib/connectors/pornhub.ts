import * as cheerio from 'cheerio';
import { BaseConnector } from './base';
import { FilterOptions, MediaItem } from '../../types/media';

export class PornhubConnector extends BaseConnector {
  id = 'pornhub_free';
  name = 'Pornhub (Public)';
  category = 'tube' as const;
  baseUrl = 'https://www.pornhub.com';
  icon = '⬛';

  async search(query: string, page = 1, filters?: FilterOptions): Promise<MediaItem[]> {
    const url = `${this.baseUrl}/video/search?search=${encodeURIComponent(query)}&page=${page}`;
    const html = await this.fetchHtml(url);
    if (!html) return [];
    return this.parseHtml(html);
  }

  async getTrending(page = 1, category?: string): Promise<MediaItem[]> {
    const url =
      category && category !== 'all'
        ? `${this.baseUrl}/video?c=${encodeURIComponent(category)}&page=${page}`
        : `${this.baseUrl}/video?o=ht&page=${page}`;
    const html = await this.fetchHtml(url);
    if (!html) return [];
    return this.parseHtml(html);
  }

  async getLatest(page = 1): Promise<MediaItem[]> {
    const url = `${this.baseUrl}/video?o=mr&page=${page}`;
    const html = await this.fetchHtml(url);
    if (!html) return [];
    return this.parseHtml(html);
  }

  async getCategories(): Promise<string[]> {
    return ['amateur', 'blowjob', 'brunette', 'ebony', 'hd-porn', 'japanese', 'lesbian', 'milf', 'pov', 'teen'];
  }

  async getVideoDetails(id: string): Promise<MediaItem | null> {
    const originalViewkey = id.includes(':') ? id.split(':')[1] : id;
    const url = `${this.baseUrl}/view_video.php?viewkey=${originalViewkey}`;
    const html = await this.fetchHtml(url);
    if (!html) return null;

    const $ = cheerio.load(html);
    const title = $('h1.title span').text().trim() || $('meta[property="og:title"]').attr('content') || 'Pornhub Video';
    const thumbnail = $('meta[property="og:image"]').attr('content') || '';

    return {
      id: `${this.id}:${originalViewkey}`,
      provider: this.id,
      providerName: this.name,
      title,
      description: `Watch ${title} on Pornhub.`,
      thumbnail,
      duration: 600,
      type: 'video',
      tags: ['pornhub', 'hd'],
      categories: ['tube'],
      embedUrl: `https://www.pornhub.com/embed/${originalViewkey}`,
      sourceUrl: url,
    };
  }

  private parseHtml(html: string): MediaItem[] {
    const $ = cheerio.load(html);
    const items: MediaItem[] = [];

    $('li.videoblock, .videoBox').each((_, el) => {
      const $el = $(el);
      const link = $el.find('a[href*="viewkey="]').first().attr('href');
      if (!link) return;

      const match = link.match(/viewkey=([a-zA-Z0-9]+)/);
      const viewkey = match ? match[1] : '';
      if (!viewkey) return;

      const title =
        $el.find('.title a').first().attr('title') ||
        $el.find('.title a').first().text().trim() ||
        $el.find('img').first().attr('alt') ||
        'Pornhub Video';

      const thumb =
        $el.find('img').first().attr('data-thumb_url') ||
        $el.find('img').first().attr('data-mediumthumb') ||
        $el.find('img').first().attr('src') ||
        '';

      const durationStr = $el.find('.duration').first().text().trim();
      const viewsStr = $el.find('.views var').first().text().trim();

      if (viewkey && thumb) {
        items.push({
          id: `${this.id}:${viewkey}`,
          provider: this.id,
          providerName: this.name,
          title,
          description: `Watch ${title} on Pornhub.`,
          thumbnail: thumb,
          duration: this.parseDuration(durationStr),
          type: 'video',
          tags: ['pornhub', 'tube', 'verified'],
          categories: ['tube'],
          views: this.parseViews(viewsStr),
          embedUrl: `https://www.pornhub.com/embed/${viewkey}`,
          sourceUrl: link.startsWith('http') ? link : `${this.baseUrl}${link}`,
          createdAt: new Date().toISOString(),
          author: {
            name: $el.find('.usernameWrap a').text().trim() || 'Pornhub Model',
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
