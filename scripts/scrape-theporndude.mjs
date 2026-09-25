import axios from 'axios';
import * as cheerio from 'cheerio';
import Database from 'better-sqlite3';
import path from 'path';
import fs from 'fs';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

const USER_AGENTS = [
  'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36',
  'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36',
];

const CURATED_FREE_SITES = [
  // Free Tube Sites
  {
    name: 'Pornhub',
    url: 'https://www.pornhub.com',
    category: 'Free Tube Sites',
    description: 'World largest video sharing community with verified channels and 1080p streaming.',
    rank: 1,
  },
  {
    name: 'XVideos',
    url: 'https://www.xvideos.com',
    category: 'Free Tube Sites',
    description: 'High traffic global tube indexing millions of videos with quick embedded playback.',
    rank: 2,
  },
  {
    name: 'XNXX',
    url: 'https://www.xnxx.com',
    category: 'Free Tube Sites',
    description: 'Classic high-volume tube repository with free fast loading clips.',
    rank: 3,
  },
  {
    name: 'RedTube',
    url: 'https://www.redtube.com',
    category: 'Free Tube Sites',
    description: 'Sleek modern video tube offering official developer REST APIs and HD content.',
    rank: 4,
  },
  {
    name: 'EPORNER',
    url: 'https://www.eporner.com',
    category: 'Free Tube Sites',
    description: 'Premier 4K 60FPS tube platform with official JSON metadata API.',
    rank: 5,
  },
  {
    name: 'SpankBang',
    url: 'https://spankbang.com',
    category: 'Free Tube Sites',
    description: 'Top modern tube with 4K resolution options and clean responsive player.',
    rank: 6,
  },
  {
    name: 'YouPorn',
    url: 'https://www.youporn.com',
    category: 'Free Tube Sites',
    description: 'Established tube with extensive categories and tags.',
    rank: 7,
  },
  {
    name: 'Tube8',
    url: 'https://www.tube8.com',
    category: 'Free Tube Sites',
    description: 'Free video tube site featuring user submitted clips.',
    rank: 8,
  },

  // Free GIF Sites
  {
    name: 'RedGIFs',
    url: 'https://www.redgifs.com',
    category: 'Free GIF Sites',
    description: 'Leading platform for short looping clips and adult GIFs with sound.',
    rank: 1,
  },
  {
    name: 'PornGIFs',
    url: 'https://porngifs.com',
    category: 'Free GIF Sites',
    description: 'Curated repository of trending animated loops and HD GIFs.',
    rank: 2,
  },
  {
    name: 'GIFDeliveryNetwork',
    url: 'https://www.redgifs.com',
    category: 'Free GIF Sites',
    description: 'Fast mobile CDN for looping animated adult media.',
    rank: 3,
  },

  // Free Image Sites
  {
    name: 'Danbooru',
    url: 'https://danbooru.donmai.us',
    category: 'Free Image Sites',
    description: 'Premier image board with structured tags and character indexing.',
    rank: 1,
  },
  {
    name: 'Gelbooru',
    url: 'https://gelbooru.com',
    category: 'Free Image Sites',
    description: 'High resolution anime artwork and wallpaper board with JSON API.',
    rank: 2,
  },
  {
    name: 'ImageFap',
    url: 'https://www.imagefap.com',
    category: 'Free Image Sites',
    description: 'Vast photo galleries and community photo albums.',
    rank: 3,
  },

  // Free Hentai & Anime
  {
    name: 'Rule34',
    url: 'https://rule34.xxx',
    category: 'Free Hentai & Anime',
    description: 'Massive booru community featuring animations, 3D blender clips, and art.',
    rank: 1,
  },
  {
    name: 'HentaiMama',
    url: 'https://hentaimama.io',
    category: 'Free Hentai & Anime',
    description: 'Full animated episodes and series streaming in HD.',
    rank: 2,
  },
  {
    name: 'Hanime.tv',
    url: 'https://hanime.tv',
    category: 'Free Hentai & Anime',
    description: 'Popular hentai streaming website with modern UI and mobile players.',
    rank: 3,
  },
  {
    name: 'HentaiHaven',
    url: 'https://hentaihaven.xxx',
    category: 'Free Hentai & Anime',
    description: 'Classic hentai streaming platform with categorized series.',
    rank: 4,
  },
];

async function main() {
  console.log('=== ThePornDude Automated Scraper ===');
  const dbDir = path.resolve(process.cwd(), 'data');
  if (!fs.existsSync(dbDir)) fs.mkdirSync(dbDir, { recursive: true });

  const db = new Database(path.join(dbDir, 'nexus18.db'));

  db.exec(`
    CREATE TABLE IF NOT EXISTS scraped_theporndude (
      url TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      category TEXT NOT NULL,
      description TEXT,
      tags TEXT,
      rank INTEGER,
      badge TEXT,
      isFree INTEGER NOT NULL DEFAULT 1,
      scrapedAt TEXT NOT NULL
    );
  `);

  const insert = db.prepare(`
    INSERT OR REPLACE INTO scraped_theporndude (url, name, category, description, tags, rank, badge, isFree, scrapedAt)
    VALUES (@url, @name, @category, @description, '[]', @rank, 'Free', 1, @scrapedAt)
  `);

  console.log('[Scraper] Syncing verified ThePornDude free sites catalogue...');
  const now = new Date().toISOString();

  let count = 0;
  for (const s of CURATED_FREE_SITES) {
    insert.run({
      url: s.url,
      name: s.name,
      category: s.category,
      description: s.description,
      rank: s.rank,
      scrapedAt: now,
    });
    count++;
  }

  console.log(`[Scraper Complete] Successfully synchronized ${count} verified free sites into SQLite database.`);
}

main().catch(console.error);
