import { getDb } from './db';

interface CacheEntry<T> {
  value: T;
  expiresAt: number;
}

class CacheManager {
  private memCache = new Map<string, CacheEntry<any>>();
  private maxMemEntries = 2000;

  get<T>(key: string): T | null {
    const now = Date.now();
    // 1. Check memory cache first
    const mem = this.memCache.get(key);
    if (mem) {
      if (mem.expiresAt > now) {
        return mem.value as T;
      }
      this.memCache.delete(key);
    }

    // 2. Check SQLite persistent cache
    try {
      const db = getDb();
      const row = db.prepare('SELECT data, expiresAt FROM media_cache WHERE id = ?').get(key) as
        | { data: string; expiresAt: number }
        | undefined;

      if (row) {
        if (row.expiresAt > now) {
          const parsed = JSON.parse(row.data) as T;
          // Hydrate memory cache
          this.memCache.set(key, { value: parsed, expiresAt: row.expiresAt });
          return parsed;
        } else {
          // Expired
          db.prepare('DELETE FROM media_cache WHERE id = ?').run(key);
        }
      }
    } catch (err) {
      // Fallback cleanly if DB is unavailable
    }

    return null;
  }

  set<T>(key: string, value: T, ttlSeconds: number = 1800, provider: string = 'general', type: string = 'feed'): void {
    const now = Date.now();
    const expiresAt = now + ttlSeconds * 1000;

    // Prune mem cache if too large
    if (this.memCache.size >= this.maxMemEntries) {
      const firstKey = this.memCache.keys().next().value;
      if (firstKey) this.memCache.delete(firstKey);
    }

    this.memCache.set(key, { value, expiresAt });

    // Save to SQLite
    try {
      const db = getDb();
      const stmt = db.prepare(`
        INSERT OR REPLACE INTO media_cache (id, provider, data, type, expiresAt, cachedAt)
        VALUES (?, ?, ?, ?, ?, ?)
      `);
      stmt.run(key, provider, JSON.stringify(value), type, expiresAt, new Date().toISOString());
    } catch (err) {
      // Non-fatal
    }
  }

  clearExpired(): void {
    const now = Date.now();
    this.memCache.forEach((v, k) => {
      if (v.expiresAt <= now) {
        this.memCache.delete(k);
      }
    });
    try {
      const db = getDb();
      db.prepare('DELETE FROM media_cache WHERE expiresAt <= ?').run(now);
    } catch (e) {
      // ignore
    }
  }
}

export const cache = new CacheManager();
