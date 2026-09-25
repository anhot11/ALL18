import axios from 'axios';
import * as cheerio from 'cheerio';
import { ScrapedThePornDudeSite } from '../types/provider';
import { saveScrapedSites } from './db';

const USER_AGENTS = [
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36',
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36',
  'Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36',
];

export const FREE_SECTIONS = [
  { name: 'Free Tube Sites', path: '/free-porn-sites', category: 'tube' },
  { name: 'Free GIF Sites', path: '/free-porn-gifs', category: 'gif' },
  { name: 'Free Image Sites', path: '/free-porn-pics', category: 'image' },
  { name: 'Free Hentai & Anime', path: '/hentai-sites', category: 'anime' },
];

export async function scrapeThePornDudeCategory(
  categoryName: string,
  categoryUrl: string
): Promise<ScrapedThePornDudeSite[]> {
  const ua = USER_AGENTS[Math.floor(Math.random() * USER_AGENTS.length)];
  const fullUrl = categoryUrl.startsWith('http') ? categoryUrl : `https://theporndude.com${categoryUrl}`;

  try {
    const response = await axios.get(fullUrl, {
      headers: {
        'User-Agent': ua,
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.9',
        'Cache-Control': 'no-cache',
      },
      timeout: 10000,
    });

    const $ = cheerio.load(response.data);
    const results: ScrapedThePornDudeSite[] = [];

    // ThePornDude uses item cards with site details
    $('.site, .box, .listing-item, [data-domain]').each((index, el) => {
      const $el = $(el);
      const name =
        $el.find('.site-name, .title, h3, .name').first().text().trim() ||
        $el.attr('data-title') ||
        $el.attr('data-domain');
      let url =
        $el.find('a[href*="/out/"], a.link, a.visit').first().attr('href') ||
        $el.find('a').first().attr('href') ||
        '';
      const description = $el.find('.desc, .site-desc, p').first().text().trim();
      const badge = $el.find('.badge, .rating, .tag').first().text().trim();

      // Collect tags
      const tags: string[] = [];
      $el.find('.tags a, .tag').each((_, tagEl) => {
        const t = $(tagEl).text().trim();
        if (t && !tags.includes(t)) tags.push(t);
      });

      // Filter paid / cam / dating keywords
      const isPaid = /onlyfans|cam|token|credit card|membership|escort|dating/i.test(
        `${name} ${description} ${tags.join(' ')}`
      );

      if (name && url && !isPaid) {
        results.push({
          name,
          url,
          description: description || `Popular free site featured in ${categoryName}`,
          category: categoryName,
          tags,
          rank: index + 1,
          badge,
          isFree: true,
        });
      }
    });

    return results;
  } catch (error: any) {
    console.warn(`[ThePornDude Scraper] Failed to fetch ${fullUrl}:`, error.message);
    return [];
  }
}

export async function scrapeAllFreeCategories(): Promise<{
  scrapedCount: number;
  sites: ScrapedThePornDudeSite[];
}> {
  const allSites: ScrapedThePornDudeSite[] = [];

  for (const sec of FREE_SECTIONS) {
    console.log(`[ThePornDude Scraper] Scraping section: ${sec.name}...`);
    const sites = await scrapeThePornDudeCategory(sec.name, sec.path);

    if (sites.length > 0) {
      allSites.push(...sites);
    } else {
      // Provide robust fallback curated entries if scraping is blocked by cloudflare
      allSites.push(...getCuratedFallbackSites(sec.name, sec.category));
    }

    // Rate-limiting delay between requests (1.5 seconds)
    await new Promise((res) => setTimeout(res, 1500));
  }

  const savedCount = saveScrapedSites(allSites);
  return { scrapedCount: savedCount, sites: allSites };
}

function getCuratedFallbackSites(categoryName: string, category: string): ScrapedThePornDudeSite[] {
  const fallbackMap: Record<string, ScrapedThePornDudeSite[]> = {
    tube: [
      {
        name: 'Pornhub',
        url: 'https://www.pornhub.com',
        description: 'World top free video tube with high quality mobile streaming.',
        category: categoryName,
        tags: ['tube', 'popular', 'amateur', 'hd'],
        rank: 1,
        isFree: true,
      },
      {
        name: 'XVideos',
        url: 'https://www.xvideos.com',
        description: 'High volume free tube with extensive library.',
        category: categoryName,
        tags: ['tube', 'free', 'massive'],
        rank: 2,
        isFree: true,
      },
      {
        name: 'RedTube',
        url: 'https://www.redtube.com',
        description: 'Clean modern interface and responsive video player.',
        category: categoryName,
        tags: ['tube', 'hd', 'fast'],
        rank: 3,
        isFree: true,
      },
      {
        name: 'SpankBang',
        url: 'https://spankbang.com',
        description: 'Excellent free tube with 4K resolution options.',
        category: categoryName,
        tags: ['tube', '4k', 'trending'],
        rank: 4,
        isFree: true,
      },
      {
        name: 'EPORNER',
        url: 'https://www.eporner.com',
        description: 'Ultra HD 4K streaming with zero ads in player.',
        category: categoryName,
        tags: ['tube', '4k', '60fps'],
        rank: 5,
        isFree: true,
      },
    ],
    gif: [
      {
        name: 'RedGIFs',
        url: 'https://www.redgifs.com',
        description: 'Top adult animated GIF and short video platform with audio.',
        category: categoryName,
        tags: ['gif', 'short', 'loop', 'audio'],
        rank: 1,
        isFree: true,
      },
      {
        name: 'PornGIFs',
        url: 'https://porngifs.com',
        description: 'Curated collection of high frame-rate adult clips and GIFs.',
        category: categoryName,
        tags: ['gif', 'clip', 'hd'],
        rank: 2,
        isFree: true,
      },
    ],
    image: [
      {
        name: 'Danbooru',
        url: 'https://danbooru.donmai.us',
        description: 'Vast searchable image board with high resolution art.',
        category: categoryName,
        tags: ['image', 'art', 'tags'],
        rank: 1,
        isFree: true,
      },
      {
        name: 'Gelbooru',
        url: 'https://gelbooru.com',
        description: 'Free image and animation repository.',
        category: categoryName,
        tags: ['image', 'anime', 'art'],
        rank: 2,
        isFree: true,
      },
    ],
    anime: [
      {
        name: 'Rule34',
        url: 'https://rule34.xxx',
        description: 'World largest adult anime and 3D animation community repository.',
        category: categoryName,
        tags: ['anime', 'hentai', '3d', 'animated'],
        rank: 1,
        isFree: true,
      },
      {
        name: 'HentaiMama',
        url: 'https://hentaimama.io',
        description: 'Streaming platform for full animated hentai episodes.',
        category: categoryName,
        tags: ['anime', 'hentai', 'episodes', 'uncensored'],
        rank: 2,
        isFree: true,
      },
    ],
  };

  return fallbackMap[category] || [];
}
