'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { useAppStore } from '@/store/useAppStore';
import { MediaItem } from '@/types/media';
import {
  Heart,
  Repeat2,
  MessageCircle,
  Bookmark,
  Share,
  MoreHorizontal,
  CheckCircle2,
  Play,
  Volume2,
  VolumeX,
  ExternalLink,
} from 'lucide-react';

export const XTimelineMode: React.FC = () => {
  const {
    isMuted,
    toggleMute,
    favorites,
    addFavorite,
    removeFavorite,
    isFavorite,
    followedProviders,
    toggleFollowProvider,
    setSelectedMedia,
  } = useAppStore();

  const [activeTab, setActiveTab] = useState<'forYou' | 'following'>('forYou');
  const [items, setItems] = useState<MediaItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(1);
  const [retweetedMap, setRetweetedMap] = useState<Record<string, boolean>>({});

  const fetchTimeline = useCallback(async (pageNum: number, tab: 'forYou' | 'following') => {
    setLoading(true);
    try {
      const res = await fetch(`/api/media?mode=x&page=${pageNum}`);
      const data = await res.json();
      if (data.items) {
        let list: MediaItem[] = data.items;
        if (tab === 'following') {
          list = list.filter((i) => followedProviders.includes(i.provider));
        }
        setItems(list);
      }
    } catch (e) {
      console.error('Failed to load X timeline', e);
    } finally {
      setLoading(false);
    }
  }, [followedProviders]);

  useEffect(() => {
    fetchTimeline(page, activeTab);
  }, [page, activeTab, fetchTimeline]);

  const toggleRetweet = (id: string) => {
    setRetweetedMap((prev) => ({ ...prev, [id]: !prev[id] }));
  };

  const getRelativeTime = (index: number) => {
    const times = ['4m', '18m', '42m', '1h', '2h', '4h', '7h', '1d'];
    return times[index % times.length];
  };

  return (
    <div className="max-w-2xl mx-auto border-x border-zinc-800 min-h-screen pb-24">
      {/* Top Tabs: For You / Following */}
      <div className="sticky top-12 z-20 bg-zinc-950/90 backdrop-blur-md border-b border-zinc-800 flex items-center justify-around">
        <button
          onClick={() => setActiveTab('forYou')}
          className={`flex-1 py-3 text-sm font-bold text-center relative transition-colors ${
            activeTab === 'forYou' ? 'text-white' : 'text-zinc-500 hover:text-zinc-300'
          }`}
        >
          <span>For you</span>
          {activeTab === 'forYou' && (
            <div className="absolute bottom-0 left-1/2 transform -translate-x-1/2 w-12 h-1 bg-x-blue rounded-full" />
          )}
        </button>
        <button
          onClick={() => setActiveTab('following')}
          className={`flex-1 py-3 text-sm font-bold text-center relative transition-colors ${
            activeTab === 'following' ? 'text-white' : 'text-zinc-500 hover:text-zinc-300'
          }`}
        >
          <span>Following</span>
          {activeTab === 'following' && (
            <div className="absolute bottom-0 left-1/2 transform -translate-x-1/2 w-16 h-1 bg-x-blue rounded-full" />
          )}
        </button>
      </div>

      {/* Tweet Cards Feed */}
      {loading ? (
        <div className="p-4 space-y-6">
          {Array.from({ length: 4 }).map((_, i) => (
            <div key={i} className="animate-pulse flex gap-3 border-b border-zinc-800 pb-6">
              <div className="w-10 h-10 rounded-full bg-zinc-800 shrink-0" />
              <div className="flex-1 space-y-3">
                <div className="h-4 bg-zinc-800 rounded w-1/3" />
                <div className="h-4 bg-zinc-800 rounded w-5/6" />
                <div className="h-56 bg-zinc-800 rounded-2xl w-full" />
              </div>
            </div>
          ))}
        </div>
      ) : items.length === 0 ? (
        <div className="text-center py-20 px-4">
          <p className="text-zinc-400 font-medium">
            {activeTab === 'following'
              ? 'No tweets from followed providers yet. Follow providers or switch to "For you"!'
              : 'No tweets found at the moment.'}
          </p>
        </div>
      ) : (
        <div className="divide-y divide-zinc-800">
          {items.map((item, index) => {
            const isLiked = isFavorite(item.id);
            const isRetweeted = !!retweetedMap[item.id];
            const isFollowing = followedProviders.includes(item.provider);
            const timeAgo = getRelativeTime(index);

            return (
              <article
                key={item.id}
                className="p-4 hover:bg-zinc-900/30 transition-colors flex gap-3 select-none"
              >
                {/* Author Avatar */}
                <div className="shrink-0">
                  <div className="w-10 h-10 rounded-full bg-zinc-800 border border-zinc-700/80 flex items-center justify-center overflow-hidden">
                    {item.author?.avatar ? (
                      <img src={item.author.avatar} alt="" className="w-full h-full object-cover" />
                    ) : (
                      <span className="text-sm font-bold text-x-blue">
                        {(item.providerName || item.provider).slice(0, 2).toUpperCase()}
                      </span>
                    )}
                  </div>
                </div>

                {/* Tweet Content Body */}
                <div className="flex-1 min-w-0">
                  {/* Header Row */}
                  <div className="flex items-center justify-between gap-1">
                    <div className="flex items-center gap-1.5 flex-wrap min-w-0">
                      <span className="font-bold text-sm text-zinc-100 truncate hover:underline cursor-pointer">
                        {item.author?.name || item.providerName || item.provider}
                      </span>
                      <CheckCircle2 className="w-4 h-4 text-x-blue fill-x-blue shrink-0" />
                      <span className="text-xs text-zinc-500">
                        @{item.provider.toLowerCase()}
                      </span>
                      <span className="text-zinc-600 text-xs">·</span>
                      <span className="text-xs text-zinc-500">{timeAgo}</span>
                    </div>

                    {/* Follow / Unfollow */}
                    <button
                      onClick={() => toggleFollowProvider(item.provider)}
                      className={`text-xs px-2.5 py-1 rounded-full font-bold transition-all ${
                        isFollowing
                          ? 'border border-zinc-700 text-zinc-300 hover:border-red-500/50 hover:text-red-400'
                          : 'bg-white text-black hover:bg-zinc-200'
                      }`}
                    >
                      {isFollowing ? 'Following' : 'Follow'}
                    </button>
                  </div>

                  {/* Tweet Text */}
                  <p className="text-sm text-zinc-200 mt-1 mb-2 leading-relaxed">
                    {item.title}
                  </p>

                  {/* Hashtags */}
                  {item.tags && item.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1 mb-2 text-xs text-x-blue">
                      {item.tags.slice(0, 4).map((t, idx) => (
                        <span key={idx} className="hover:underline cursor-pointer">
                          #{t.replace(/[^a-zA-Z0-9]/g, '')}
                        </span>
                      ))}
                    </div>
                  )}

                  {/* Media Embed / Preview Container */}
                  <div
                    onClick={() => setSelectedMedia(item)}
                    className="relative mt-2 rounded-2xl overflow-hidden border border-zinc-800 bg-zinc-950 aspect-video group cursor-pointer"
                  >
                    <img
                      src={item.thumbnail}
                      alt={item.title}
                      loading="lazy"
                      className="w-full h-full object-cover group-hover:scale-102 transition-transform duration-300"
                    />

                    {/* Overlay Play Action */}
                    <div className="absolute inset-0 bg-black/25 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                      <div className="p-3 bg-x-blue text-white rounded-full shadow-lg">
                        <Play className="w-6 h-6 fill-white translate-x-0.5" />
                      </div>
                    </div>

                    {/* Type and duration badges */}
                    {item.duration ? (
                      <span className="absolute bottom-2 right-2 bg-black/80 text-white text-[11px] font-bold px-2 py-0.5 rounded-md">
                        {Math.floor(item.duration / 60)}:{(item.duration % 60).toString().padStart(2, '0')}
                      </span>
                    ) : null}

                    <span className="absolute top-2 left-2 bg-zinc-900/80 backdrop-blur-sm text-zinc-200 text-[10px] font-semibold px-2 py-0.5 rounded-full border border-zinc-700/50">
                      {item.providerName || item.provider}
                    </span>
                  </div>

                  {/* Engagement Bar (Twitter Buttons) */}
                  <div className="flex items-center justify-between text-zinc-500 mt-3 pt-1 text-xs max-w-md">
                    {/* Reply */}
                    <button className="flex items-center gap-1.5 hover:text-x-blue transition-colors group">
                      <div className="p-1.5 rounded-full group-hover:bg-x-blue/10">
                        <MessageCircle className="w-4 h-4" />
                      </div>
                      <span>{(index * 7 + 12) % 45}</span>
                    </button>

                    {/* Repost */}
                    <button
                      onClick={() => toggleRetweet(item.id)}
                      className={`flex items-center gap-1.5 transition-colors group ${
                        isRetweeted ? 'text-emerald-400' : 'hover:text-emerald-400'
                      }`}
                    >
                      <div className="p-1.5 rounded-full group-hover:bg-emerald-400/10">
                        <Repeat2 className="w-4 h-4" />
                      </div>
                      <span>{(index * 13 + 5) % 89 + (isRetweeted ? 1 : 0)}</span>
                    </button>

                    {/* Like */}
                    <button
                      onClick={() => (isLiked ? removeFavorite(item.id) : addFavorite(item))}
                      className={`flex items-center gap-1.5 transition-colors group ${
                        isLiked ? 'text-pink-500' : 'hover:text-pink-500'
                      }`}
                    >
                      <div className="p-1.5 rounded-full group-hover:bg-pink-500/10">
                        <Heart className={`w-4 h-4 ${isLiked ? 'fill-pink-500' : ''}`} />
                      </div>
                      <span>{(item.likes || 120) + (isLiked ? 1 : 0)}</span>
                    </button>

                    {/* Bookmark */}
                    <button
                      onClick={() => (isLiked ? removeFavorite(item.id) : addFavorite(item))}
                      className={`hover:text-x-blue transition-colors p-1.5 rounded-full hover:bg-x-blue/10 ${
                        isLiked ? 'text-x-blue' : ''
                      }`}
                    >
                      <Bookmark className={`w-4 h-4 ${isLiked ? 'fill-x-blue' : ''}`} />
                    </button>

                    {/* Share */}
                    <button
                      onClick={() => {
                        if (navigator.share) {
                          navigator.share({ title: item.title, url: item.sourceUrl });
                        } else {
                          navigator.clipboard.writeText(item.sourceUrl);
                          alert('Copied link!');
                        }
                      }}
                      className="hover:text-x-blue transition-colors p-1.5 rounded-full hover:bg-x-blue/10"
                    >
                      <Share className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </article>
            );
          })}
        </div>
      )}
    </div>
  );
};
