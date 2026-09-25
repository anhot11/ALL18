import Database from 'better-sqlite3';
import path from 'path';
import fs from 'fs';
import { ProviderInfo, ScrapedThePornDudeSite } from '../types/provider';
import { SEED_PROVIDERS } from './seed-providers';

let dbInstance: Database.Database | null = null;

export function getDb(): Database.Database {
  if (dbInstance) {
    return dbInstance;
  }

  const dbDir = path.resolve(process.cwd(), 'data');
  if (!fs.existsSync(dbDir)) {
    fs.mkdirSync(dbDir, { recursive: true });
  }

  const dbPath = path.join(dbDir, 'nexus18.db');
  dbInstance = new Database(dbPath);
  dbInstance.pragma('journal_mode = WAL');

  initTables(dbInstance);
  return dbInstance;
}

function initTables(db: Database.Database) {
  // 1. Providers Table
  db.exec(`
    CREATE TABLE IF NOT EXISTS providers (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      category TEXT NOT NULL,
      baseUrl TEXT NOT NULL,
      icon TEXT,
      enabled INTEGER NOT NULL DEFAULT 1,
      priority INTEGER NOT NULL DEFAULT 50,
      quality TEXT NOT NULL DEFAULT 'high',
      description TEXT,
      tags TEXT,
      hasApi INTEGER NOT NULL DEFAULT 0,
      sourceCategory TEXT,
      lastVerified TEXT,
      createdAt TEXT NOT NULL
    );
  `);

  // 2. Scraped Sites from ThePornDude
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

  // 3. Media Metadata Cache
  db.exec(`
    CREATE TABLE IF NOT EXISTS media_cache (
      id TEXT PRIMARY KEY,
      provider TEXT NOT NULL,
      title TEXT,
      data TEXT NOT NULL,
      type TEXT NOT NULL,
      expiresAt INTEGER NOT NULL,
      cachedAt TEXT NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_cache_expires ON media_cache(expiresAt);
  `);

  // 4. Favorites & History table (optional SQLite backend)
  db.exec(`
    CREATE TABLE IF NOT EXISTS user_favorites (
      id TEXT PRIMARY KEY,
      itemData TEXT NOT NULL,
      savedAt TEXT NOT NULL
    );
  `);

  // Seed default providers if empty
  const countStmt = db.prepare('SELECT COUNT(*) as count FROM providers');
  const result = countStmt.get() as { count: number };
  if (result.count === 0) {
    const insertStmt = db.prepare(`
      INSERT INTO providers (id, name, category, baseUrl, icon, enabled, priority, quality, description, tags, hasApi, sourceCategory, lastVerified, createdAt)
      VALUES (@id, @name, @category, @baseUrl, @icon, @enabled, @priority, @quality, @description, @tags, @hasApi, @sourceCategory, @lastVerified, @createdAt)
    `);

    const insertMany = db.transaction((providers: ProviderInfo[]) => {
      const now = new Date().toISOString();
      for (const p of providers) {
        insertStmt.run({
          id: p.id,
          name: p.name,
          category: p.category,
          baseUrl: p.baseUrl,
          icon: p.icon || '🌐',
          enabled: p.enabled ? 1 : 0,
          priority: p.priority || 50,
          quality: p.quality || 'high',
          description: p.description || '',
          tags: JSON.stringify(p.tags || []),
          hasApi: p.hasApi ? 1 : 0,
          sourceCategory: p.sourceCategory || 'Curated',
          lastVerified: p.lastVerified || now,
          createdAt: now,
        });
      }
    });

    insertMany(SEED_PROVIDERS);
  }
}

export function getAllProviders(): ProviderInfo[] {
  const db = getDb();
  const rows = db.prepare('SELECT * FROM providers ORDER BY priority DESC').all() as any[];
  return rows.map((row) => ({
    id: row.id,
    name: row.name,
    category: row.category,
    baseUrl: row.baseUrl,
    icon: row.icon,
    enabled: Boolean(row.enabled),
    priority: row.priority,
    quality: row.quality,
    description: row.description,
    tags: JSON.parse(row.tags || '[]'),
    hasApi: Boolean(row.hasApi),
    sourceCategory: row.sourceCategory,
    lastVerified: row.lastVerified,
  }));
}

export function toggleProviderStatus(id: string, enabled: boolean): boolean {
  const db = getDb();
  const stmt = db.prepare('UPDATE providers SET enabled = ? WHERE id = ?');
  const res = stmt.run(enabled ? 1 : 0, id);
  return res.changes > 0;
}

export function updateProviderPriority(id: string, priority: number): boolean {
  const db = getDb();
  const stmt = db.prepare('UPDATE providers SET priority = ? WHERE id = ?');
  const res = stmt.run(priority, id);
  return res.changes > 0;
}

export function saveScrapedSites(sites: ScrapedThePornDudeSite[]): number {
  const db = getDb();
  const insertOrReplace = db.prepare(`
    INSERT OR REPLACE INTO scraped_theporndude (url, name, category, description, tags, rank, badge, isFree, scrapedAt)
    VALUES (@url, @name, @category, @description, @tags, @rank, @badge, @isFree, @scrapedAt)
  `);

  const tx = db.transaction((items: ScrapedThePornDudeSite[]) => {
    let inserted = 0;
    const now = new Date().toISOString();
    for (const site of items) {
      insertOrReplace.run({
        url: site.url,
        name: site.name,
        category: site.category,
        description: site.description,
        tags: JSON.stringify(site.tags || []),
        rank: site.rank || 0,
        badge: site.badge || '',
        isFree: site.isFree ? 1 : 0,
        scrapedAt: now,
      });
      inserted++;
    }
    return inserted;
  });

  return tx(sites);
}

export function getScrapedSites(category?: string): ScrapedThePornDudeSite[] {
  const db = getDb();
  let query = 'SELECT * FROM scraped_theporndude WHERE isFree = 1';
  const params: any[] = [];
  if (category) {
    query += ' AND category = ?';
    params.push(category);
  }
  query += ' ORDER BY rank ASC, name ASC';

  const rows = db.prepare(query).all(...params) as any[];
  return rows.map((r) => ({
    name: r.name,
    url: r.url,
    description: r.description,
    category: r.category,
    tags: JSON.parse(r.tags || '[]'),
    rank: r.rank,
    badge: r.badge,
    isFree: Boolean(r.isFree),
  }));
}
