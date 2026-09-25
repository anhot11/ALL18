import * as cheerio from 'cheerio';
import { BaseConnector } from './base';
import { FilterOptions, MediaItem } from '../../types/media';

export class HentaiMamaConnector extends BaseConnector {
  id = 'hentaimama';
  name = 'HentaiMama';
  category = 'anime' as const;
  baseUrl = 'https://hentaimama.io';
  icon = '🍙';

  async search(query: string, page = 1, filters?: FilterOptions): Promise<MediaItem[]> {
    const url = `${this.baseUrl}/page/${page}/?s=${encodeURIComponent(query)}`;
    const html = await this.fetchHtml(url);
    if (!html) return [];
    return this.parseAnimeList(html);
  }

  async getTrending(page = 1, category?: string): Promise<MediaItem[]> {
    const url = `${this.baseUrl}/ongoing-series/page/${page}/`;
    const html = await this.fetchHtml(url);
    if (!html) return [];
    return this.parseAnimeList(html);
  }

  async getLatest(page = 1): Promise<MediaItem[]> {
    const url = `${this.baseUrl}/episodes/page/${page}/`;
    const html = await this.fetchHtml(url);
    if (!html) return [];
    return this.parseAnimeList(html);
  }

  async getCategories(): Promise<string[]> {
    return ['3D Hentai', 'Uncensored', 'Incest', 'Harem', 'Vanilla', 'Milf', 'Yaoi', 'Yuri', 'Comedy', 'Fantasy'];
  }

  async getVideoDetails(id: string): Promise<MediaItem | null> {
    const originalSlug = id.includes(':') ? id.split(':')[1] : id;
    const url = `${this.baseUrl}/episodes/${originalSlug}/`;
    const html = await this.fetchHtml(url);
    if (!html) return null;

    const $ = cheerio.load(html);
    const title = $('h1').first().text().trim() || 'Hentai Episode';
    const thumbnail = $('meta[property="og:image"]').attr('content') || '';
    const iframeSrc = $('iframe').first().attr('src') || '';

    return {
      id: `${this.id}:${originalSlug}`,
      provider: this.id,
      providerName: this.name,
      title,
      description: `Watch anime episode: ${title}`,
      thumbnail,
      duration: 1200,
      type: 'anime',
      tags: ['anime', 'hentai', 'uncensored'],
      categories: ['anime'],
      embedUrl: iframeSrc.startsWith('//') ? `https:${iframeSrc}` : iframeSrc,
      sourceUrl: url,
    };
  }

  private parseAnimeList(html: string): MediaItem[] {
    const $ = cheerio.load(html);
    const items: MediaItem[] = [];

    $('.hentai_item, article, .post-item, .loop-item').each((_, el) => {
      const $el = $(el);
      const link = $el.find('a').first().attr('href') || '';
      if (!link) return;

      const slugMatch = link.match(/\/episodes\/([^\/]+)/) || link.match(/\/tvshows\/([^\/]+)/);
      const slug = slugMatch ? slugMatch[1] : link.split('/').filter(Boolean).pop() || '';
      if (!slug) return;

      const title = $el.find('h2, h3, .title, a').first().text().trim() || 'Anime Stream';
      const thumb =
        $el.find('img').first().attr('data-src') ||
        $el.find('img').first().attr('src') ||
        '';

      if (slug && thumb) {
        items.push({
          id: `${this.id}:${slug}`,
          provider: this.id,
          providerName: this.name,
          title,
          description: `Streaming anime hentai series: ${title}`,
          thumbnail: thumb.startsWith('//') ? `https:${thumb}` : thumb,
          duration: 1200, // standard anime episode length ~20min
          type: 'anime',
          tags: ['anime', 'hentai', 'japanese'],
          categories: ['anime'],
          sourceUrl: link,
          createdAt: new Date().toISOString(),
          author: {
            name: 'HentaiMama Animation',
            verified: true,
          },
        });
      }
    });

    return items;
  }
}
