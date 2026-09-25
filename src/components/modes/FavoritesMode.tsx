'use client';

import React, { useState } from 'react';
import { useAppStore } from '@/store/useAppStore';
import { Heart, Clock, History, Trash2, Play } from 'lucide-react';
import { MediaItem } from '@/types/media';

export const FavoritesMode: React.FC = () => {
  const {
    favorites,
    removeFavorite,
    watchLater,
    toggleWatchLater,
    history,
    setSelectedMedia,
  } = useAppStore();

  const [activeSubTab, setActiveSubTab] = useState<'favorites' | 'watchLater' | 'history'>('favorites');

  const currentList =
    activeSubTab === 'favorites'
      ? favorites
      : activeSubTab === 'watchLater'
      ? watchLater
      : history;

  return (
    <div className="max-w-5xl mx-auto px-4 py-4 pb-24">
      <div className="flex items-center justify-between mb-4 border-b border-zinc-800 pb-3">
        <h2 className="text-xl font-black text-white flex items-center gap-2">
          <span>Library & Saved</span>
        </h2>

        {/* Sub tabs */}
        <div className="flex items-center gap-1 bg-zinc-900 p-1 rounded-xl border border-zinc-800">
          <button
            onClick={() => setActiveSubTab('favorites')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
              activeSubTab === 'favorites'
                ? 'bg-tube-primary text-black'
                : 'text-zinc-400 hover:text-white'
            }`}
          >
            <Heart className="w-3.5 h-3.5" />
            <span>Favorites ({favorites.length})</span>
          </button>

          <button
            onClick={() => setActiveSubTab('watchLater')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
              activeSubTab === 'watchLater'
                ? 'bg-tube-primary text-black'
                : 'text-zinc-400 hover:text-white'
            }`}
          >
            <Clock className="w-3.5 h-3.5" />
            <span>Watch Later ({watchLater.length})</span>
          </button>

          <button
            onClick={() => setActiveSubTab('history')}
            className={`flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold transition-colors ${
              activeSubTab === 'history'
                ? 'bg-tube-primary text-black'
                : 'text-zinc-400 hover:text-white'
            }`}
          >
            <History className="w-3.5 h-3.5" />
            <span>History ({history.length})</span>
          </button>
        </div>
      </div>

      {currentList.length === 0 ? (
        <div className="text-center py-24 bg-zinc-900/40 rounded-2xl border border-zinc-800/80">
          <Heart className="w-12 h-12 text-zinc-600 mx-auto mb-3" />
          <p className="text-sm font-semibold text-zinc-300">No items saved here yet</p>
          <p className="text-xs text-zinc-500 mt-1">
            Tap the heart or save icon on any video in All18, TikTok, or 𝕏 to keep it handy!
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-4">
          {currentList.map((item) => (
            <div
              key={item.id}
              className="group relative bg-zinc-900 rounded-xl overflow-hidden border border-zinc-800 hover:border-zinc-700 transition-all flex flex-col cursor-pointer"
              onClick={() => setSelectedMedia(item)}
            >
              <div className="relative aspect-video w-full bg-zinc-950">
                <img
                  src={item.thumbnail}
                  alt={item.title}
                  className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300"
                />
                <div className="absolute inset-0 bg-black/30 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                  <Play className="w-8 h-8 fill-white text-white" />
                </div>
                {item.duration ? (
                  <span className="absolute bottom-2 right-2 bg-black/85 text-white text-[10px] font-bold px-1.5 py-0.5 rounded">
                    {Math.floor(item.duration / 60)}:{(item.duration % 60).toString().padStart(2, '0')}
                  </span>
                ) : null}
              </div>

              <div className="p-3 flex-1 flex flex-col justify-between">
                <h4 className="text-xs font-semibold text-zinc-200 line-clamp-2 leading-snug">
                  {item.title}
                </h4>

                <div className="flex items-center justify-between text-[11px] text-zinc-400 mt-2 pt-2 border-t border-zinc-800">
                  <span>{item.providerName || item.provider}</span>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      if (activeSubTab === 'favorites') removeFavorite(item.id);
                      else if (activeSubTab === 'watchLater') toggleWatchLater(item);
                    }}
                    className="text-zinc-500 hover:text-red-400 p-1"
                    title="Remove item"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
