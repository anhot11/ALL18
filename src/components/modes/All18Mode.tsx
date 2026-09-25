'use client';

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useAppStore } from '@/store/useAppStore';
import { MediaItem, MediaType } from '@/types/media';
import { Play, Eye, ThumbsUp, Sparkles, Filter, ChevronRight, Loader2 } from 'lucide-react';

export const All18Mode: React.FC = () => {
  const {
    searchQuery,
    activeCategory,
    setActiveCategory,
    activeType,
    setActiveType,
    activeDuration,
    setActiveDuration,
    activeProvider,
    setActiveProvider,
    setSelectedMedia,
  } = useAppStore();

  const [items, setItems] = useState<MediaItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadingMore, setLoadingMore] = useState(false);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const [categories, setCategories] = useState<string[]>([
    'Trending',
    '4K Ultra HD',
    'Amateur',
    'Blowjob',
    'Brunette',
    'POV',
    'MILF',
    'Anime & Hentai',
    'Animated GIFs',
    'Cosplay',
    'Lesbian',
    'Hardcore',
    'VR',
  ]);

  // Load categories
  useEffect(() => {
    fetch('/api/categories')
      .then((res) => res.json())
      .then((data) => {
        if (data.categories && data.categories.length > 0) {
          setCategories(['Trending', ...data.categories.filter((c: string) => c !== 'Trending').slice(0, 20)]);
        }
      })
      .catch(() => {});
  }, []);

  // Fetch feed or search
  const fetchItems = useCallback(
    async (pageNum: number, isInitial = false) => {
      if (isInitial) setLoading(true);
      else setLoadingMore(true);

      try {
        const params = new URLSearchParams();
        params.set('mode', 'all18');
        params.set('page', String(pageNum));
        if (searchQuery) params.set('q', searchQuery);
        if (activeCategory && activeCategory !== 'Trending') params.set('category', activeCategory);
        if (activeType && activeType !== 'all') params.set('type', activeType);
        if (activeDuration && activeDuration !== 'all') params.set('duration', activeDuration);
        if (activeProvider && activeProvider !== 'all') params.set('provider', activeProvider);

        const res = await fetch(`/api/media?${params.toString()}`);
        const data = await res.json();

        if (data.items) {
          if (pageNum === 1) {
            setItems(data.items);
          } else {
            setItems((prev) => {
              const ids = new Set(prev.map((i) => i.id));
              const unique = data.items.filter((i: MediaItem) => !ids.has(i.id));
              return [...prev, ...unique];
            });
          }
          setHasMore(data.items.length > 0);
        }
      } catch (err) {
        console.error('Failed to fetch media', err);
      } finally {
        setLoading(false);
        setLoadingMore(false);
      }
    },
    [searchQuery, activeCategory, activeType, activeDuration, activeProvider]
  );

  useEffect(() => {
    setPage(1);
    fetchItems(1, true);
  }, [fetchItems]);

  const loadMore = () => {
    if (loadingMore || !hasMore) return;
    const nextPage = page + 1;
    setPage(nextPage);
    fetchItems(nextPage, false);
  };

  const formatDuration = (seconds?: number) => {
    if (!seconds) return '';
    const mins = Math.floor(seconds / 60);
    const secs = seconds % 60;
    return `${mins}:${secs < 10 ? '0' : ''}${secs}`;
  };

  const formatViews = (views?: number) => {
    if (!views) return '';
    if (views >= 1000000) return `${(views / 1000000).toFixed(1)}M`;
    if (views >= 1000) return `${Math.floor(views / 1000)}K`;
    return String(views);
  };

  return (
    <div className="pb-24 pt-2">
      {/* Category Chips Horizontal Scroller */}
      <div className="px-4 mb-3">
        <div className="flex items-center gap-1.5 overflow-x-auto no-scrollbar py-1">
          {categories.map((cat) => {
            const isSelected = activeCategory === cat;
            return (
              <button
                key={cat}
                onClick={() => setActiveCategory(cat)}
                className={`whitespace-nowrap px-3.5 py-1.5 rounded-full text-xs font-semibold tracking-wide transition-all ${
                  isSelected
                    ? 'bg-tube-primary text-black shadow-md shadow-tube-primary/20 scale-105'
                    : 'bg-zinc-900 text-zinc-300 hover:bg-zinc-800 hover:text-white border border-zinc-800/80'
                }`}
              >
                {cat}
              </button>
            );
          })}
        </div>
      </div>

      {/* Filter Row: Type & Duration Chips */}
      <div className="px-4 mb-4 flex flex-wrap items-center justify-between gap-2 text-xs">
        <div className="flex items-center gap-1 overflow-x-auto no-scrollbar">
          {(['all', 'video', 'gif', 'anime', 'image'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setActiveType(t)}
              className={`px-2.5 py-1 rounded-md capitalize font-medium transition-colors ${
                activeType === t
                  ? 'bg-zinc-800 text-tube-primary font-bold border border-tube-primary/40'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              {t === 'all' ? 'All Formats' : `${t}s`}
            </button>
          ))}
        </div>

        <div className="flex items-center gap-1">
          {(['all', 'short', 'medium', 'long'] as const).map((d) => (
            <button
              key={d}
              onClick={() => setActiveDuration(d)}
              className={`px-2 py-0.5 rounded text-[11px] font-medium transition-colors ${
                activeDuration === d
                  ? 'bg-zinc-800 text-zinc-100 font-bold border border-zinc-600'
                  : 'text-zinc-500 hover:text-zinc-300'
              }`}
            >
              {d === 'all' ? 'Any Time' : d === 'short' ? '<5m' : d === 'medium' ? '5-20m' : '20m+'}
            </button>
          ))}
        </div>
      </div>

      {/* Grid of Thumbnails (Pornhub style) */}
      <div className="px-4">
        {loading ? (
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            {Array.from({ length: 12 }).map((_, i) => (
              <div key={i} className="animate-pulse bg-zinc-900 rounded-lg overflow-hidden border border-zinc-800">
                <div className="aspect-video bg-zinc-800 w-full" />
                <div className="p-2.5 space-y-2">
                  <div className="h-4 bg-zinc-800 rounded w-3/4" />
                  <div className="h-3 bg-zinc-800 rounded w-1/2" />
                </div>
              </div>
            ))}
          </div>
        ) : items.length === 0 ? (
          <div className="text-center py-16 px-4 bg-zinc-900/50 rounded-2xl border border-zinc-800/80 max-w-lg mx-auto">
            <Filter className="w-12 h-12 text-zinc-600 mx-auto mb-3" />
            <h3 className="text-base font-bold text-zinc-200">No media items found</h3>
            <p className="text-xs text-zinc-400 mt-1">
              Try adjusting your search keywords, category, or duration filters.
            </p>
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
            {items.map((item) => (
              <div
                key={item.id}
                onClick={() => setSelectedMedia(item)}
                className="group cursor-pointer bg-zinc-900/70 hover:bg-zinc-800/90 rounded-xl overflow-hidden border border-zinc-800/60 hover:border-zinc-700/80 transition-all duration-200 flex flex-col"
              >
                {/* Thumbnail Container */}
                <div className="relative aspect-video w-full bg-zinc-950 overflow-hidden">
                  <img
                    src={item.thumbnail || '/placeholder.png'}
                    alt={item.title}
                    loading="lazy"
                    onError={(e) => {
                      // Fallback thumbnail if provider image is blocked
                      (e.target as HTMLImageElement).src =
                        'https://images.unsplash.com/photo-1518770660439-4636190af475?w=500&auto=format&fit=crop&q=60';
                    }}
                    className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                  />

                  {/* Play overlay on hover */}
                  <div className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                    <div className="bg-tube-primary/90 text-black p-3 rounded-full shadow-lg transform group-hover:scale-110 transition-transform">
                      <Play className="w-6 h-6 fill-black translate-x-0.5" />
                    </div>
                  </div>

                  {/* Duration Badge */}
                  {item.duration ? (
                    <span className="absolute bottom-1.5 right-1.5 bg-black/85 text-zinc-100 text-[11px] font-bold px-1.5 py-0.5 rounded">
                      {formatDuration(item.duration)}
                    </span>
                  ) : null}

                  {/* Type Badge (GIF, 4K, Anime) */}
                  {item.type && item.type !== 'video' && (
                    <span className="absolute top-1.5 left-1.5 bg-tube-primary text-black text-[10px] font-extrabold uppercase px-1.5 py-0.5 rounded shadow">
                      {item.type}
                    </span>
                  )}

                  {/* Provider Pill */}
                  <span className="absolute bottom-1.5 left-1.5 bg-black/80 backdrop-blur-sm text-zinc-300 text-[10px] font-medium px-2 py-0.5 rounded-full flex items-center gap-1 border border-zinc-700/50">
                    {item.providerName || item.provider}
                  </span>
                </div>

                {/* Info Metadata */}
                <div className="p-3 flex-1 flex flex-col justify-between">
                  <h4 className="text-sm font-semibold text-zinc-100 line-clamp-2 group-hover:text-tube-primary transition-colors leading-snug">
                    {item.title}
                  </h4>

                  <div className="flex items-center justify-between text-[11px] text-zinc-400 mt-2.5 pt-1.5 border-t border-zinc-800/40">
                    <div className="flex items-center gap-2">
                      {item.views !== undefined && item.views > 0 && (
                        <span className="flex items-center gap-1">
                          <Eye className="w-3 h-3 text-zinc-500" />
                          {formatViews(item.views)}
                        </span>
                      )}
                      {item.rating !== undefined && (
                        <span className="text-emerald-400 font-semibold">{item.rating}%</span>
                      )}
                    </div>
                    {item.categories && item.categories[0] && (
                      <span className="text-zinc-500 truncate max-w-[100px]">
                        #{item.categories[0]}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Load More Button */}
        {items.length > 0 && (
          <div className="mt-8 flex justify-center">
            <button
              onClick={loadMore}
              disabled={loadingMore || !hasMore}
              className="flex items-center gap-2 px-6 py-2.5 bg-zinc-900 hover:bg-zinc-800 border border-zinc-700/80 rounded-full text-sm font-semibold text-zinc-200 transition-all shadow-md disabled:opacity-50"
            >
              {loadingMore ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin text-tube-primary" />
                  <span>Loading more videos...</span>
                </>
              ) : hasMore ? (
                <>
                  <span>Load More</span>
                  <ChevronRight className="w-4 h-4" />
                </>
              ) : (
                <span>You have reached the end</span>
              )}
            </button>
          </div>
        )}
      </div>
    </div>
  );
};
