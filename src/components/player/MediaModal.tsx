'use client';

import React, { useEffect, useState } from 'react';
import { useAppStore } from '@/store/useAppStore';
import {
  X,
  Heart,
  Clock,
  ExternalLink,
  Share2,
  Flag,
  Eye,
  CheckCircle2,
  Maximize2,
} from 'lucide-react';
import { MediaItem } from '@/types/media';

export const MediaModal: React.FC = () => {
  const {
    selectedMedia,
    setSelectedMedia,
    isMuted,
    isFavorite,
    addFavorite,
    removeFavorite,
    toggleWatchLater,
    watchLater,
    setSearchQuery,
    setMode,
    setReportItem,
  } = useAppStore();

  const [embedLoaded, setEmbedLoaded] = useState(false);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setSelectedMedia(null);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [setSelectedMedia]);

  if (!selectedMedia) return null;

  const isLiked = isFavorite(selectedMedia.id);
  const isSavedLater = watchLater.some((w) => w.id === selectedMedia.id);

  const handleTagClick = (tag: string) => {
    setSelectedMedia(null);
    setSearchQuery(tag);
    setMode('all18');
  };

  const handleShare = () => {
    if (navigator.share) {
      navigator.share({
        title: selectedMedia.title,
        url: selectedMedia.sourceUrl,
      });
    } else {
      navigator.clipboard.writeText(selectedMedia.sourceUrl);
      alert('Official link copied to clipboard!');
    }
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/85 backdrop-blur-md p-2 sm:p-4 overflow-y-auto"
      onClick={() => setSelectedMedia(null)}
    >
      <div
        className="relative w-full max-w-4xl bg-zinc-950 border border-zinc-800 rounded-2xl overflow-hidden shadow-2xl my-auto animate-fade-in"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Top bar with close button */}
        <div className="flex items-center justify-between px-4 py-2.5 bg-zinc-900/80 border-b border-zinc-800">
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold px-2 py-0.5 rounded bg-tube-primary text-black">
              {selectedMedia.providerName || selectedMedia.provider}
            </span>
            <span className="text-xs text-zinc-400 capitalize">{selectedMedia.type}</span>
          </div>

          <button
            onClick={() => setSelectedMedia(null)}
            className="p-1.5 rounded-full text-zinc-400 hover:text-white hover:bg-zinc-800 transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Media Player Area */}
        <div className="relative aspect-video w-full bg-black flex items-center justify-center overflow-hidden">
          {selectedMedia.streamUrl ? (
            <video
              src={selectedMedia.streamUrl}
              poster={selectedMedia.thumbnail}
              controls
              autoPlay
              playsInline
              muted={isMuted}
              className="w-full h-full object-contain"
            />
          ) : selectedMedia.embedUrl ? (
            <iframe
              src={selectedMedia.embedUrl}
              title={selectedMedia.title}
              allow="autoplay; fullscreen; encrypted-media; picture-in-picture"
              allowFullScreen
              sandbox="allow-scripts allow-same-origin allow-popups allow-forms"
              className="w-full h-full border-0"
              onLoad={() => setEmbedLoaded(true)}
            />
          ) : selectedMedia.preview && selectedMedia.preview.endsWith('.mp4') ? (
            <video
              src={selectedMedia.preview}
              poster={selectedMedia.thumbnail}
              controls
              autoPlay
              loop
              muted={isMuted}
              className="w-full h-full object-contain"
            />
          ) : (
            <img
              src={selectedMedia.preview || selectedMedia.thumbnail}
              alt={selectedMedia.title}
              className="w-full h-full object-contain"
            />
          )}
        </div>

        {/* Info & Metadata Panel */}
        <div className="p-4 sm:p-6 space-y-4">
          <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
            <div className="space-y-1">
              <h2 className="text-base sm:text-lg font-bold text-white leading-snug">
                {selectedMedia.title}
              </h2>

              <div className="flex items-center gap-3 text-xs text-zinc-400">
                {selectedMedia.views !== undefined && selectedMedia.views > 0 && (
                  <span className="flex items-center gap-1">
                    <Eye className="w-3.5 h-3.5" />
                    {selectedMedia.views.toLocaleString()} views
                  </span>
                )}
                {selectedMedia.rating && (
                  <span className="text-emerald-400 font-bold">{selectedMedia.rating}% Rating</span>
                )}
                {selectedMedia.duration && (
                  <span>
                    {Math.floor(selectedMedia.duration / 60)}:
                    {(selectedMedia.duration % 60).toString().padStart(2, '0')}
                  </span>
                )}
              </div>
            </div>

            {/* Action Buttons */}
            <div className="flex items-center gap-2 shrink-0">
              {/* Like / Favorite */}
              <button
                onClick={() =>
                  isLiked ? removeFavorite(selectedMedia.id) : addFavorite(selectedMedia)
                }
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold border transition-all ${
                  isLiked
                    ? 'bg-red-500/10 border-red-500/40 text-red-400'
                    : 'bg-zinc-900 border-zinc-800 text-zinc-300 hover:bg-zinc-800'
                }`}
              >
                <Heart className={`w-4 h-4 ${isLiked ? 'fill-red-400' : ''}`} />
                <span>{isLiked ? 'Favorited' : 'Favorite'}</span>
              </button>

              {/* Watch Later */}
              <button
                onClick={() => toggleWatchLater(selectedMedia)}
                className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold border transition-all ${
                  isSavedLater
                    ? 'bg-tube-primary/10 border-tube-primary/40 text-tube-primary'
                    : 'bg-zinc-900 border-zinc-800 text-zinc-300 hover:bg-zinc-800'
                }`}
              >
                <Clock className="w-4 h-4" />
                <span>{isSavedLater ? 'Saved' : 'Watch Later'}</span>
              </button>

              {/* Share */}
              <button
                onClick={handleShare}
                className="p-2 rounded-xl bg-zinc-900 border border-zinc-800 text-zinc-300 hover:text-white hover:bg-zinc-800 transition-colors"
                title="Share link"
              >
                <Share2 className="w-4 h-4" />
              </button>

              {/* Report broken */}
              <button
                onClick={() => setReportItem(selectedMedia)}
                className="p-2 rounded-xl bg-zinc-900 border border-zinc-800 text-zinc-400 hover:text-amber-400 hover:bg-zinc-800 transition-colors"
                title="Report broken stream"
              >
                <Flag className="w-4 h-4" />
              </button>
            </div>
          </div>

          {/* Description */}
          {selectedMedia.description && (
            <p className="text-xs text-zinc-400 leading-relaxed bg-zinc-900/40 p-3 rounded-xl border border-zinc-800/60">
              {selectedMedia.description}
            </p>
          )}

          {/* Clickable Tags */}
          {selectedMedia.tags && selectedMedia.tags.length > 0 && (
            <div className="space-y-1.5">
              <span className="text-xs font-semibold text-zinc-400">Categories & Tags:</span>
              <div className="flex flex-wrap gap-1.5">
                {selectedMedia.tags.map((t, idx) => (
                  <button
                    key={idx}
                    onClick={() => handleTagClick(t)}
                    className="px-2.5 py-1 bg-zinc-900 hover:bg-zinc-800 border border-zinc-800 hover:border-zinc-700 text-zinc-300 text-xs rounded-lg transition-colors"
                  >
                    #{t}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* Official External Link Footer */}
          <div className="pt-3 border-t border-zinc-800 flex items-center justify-between text-xs text-zinc-500">
            <span>Aggregated by Nexus18 from public web sources</span>
            <a
              href={selectedMedia.sourceUrl}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center gap-1 text-tube-primary hover:underline font-semibold"
            >
              <span>Open on {selectedMedia.providerName || selectedMedia.provider}</span>
              <ExternalLink className="w-3.5 h-3.5" />
            </a>
          </div>
        </div>
      </div>
    </div>
  );
};
