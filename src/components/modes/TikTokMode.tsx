'use client';

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useAppStore } from '@/store/useAppStore';
import { MediaItem } from '@/types/media';
import {
  Heart,
  Bookmark,
  Share2,
  Volume2,
  VolumeX,
  Play,
  Pause,
  ExternalLink,
  Info,
  Disc3,
  CheckCircle2,
  Flag,
} from 'lucide-react';

export const TikTokMode: React.FC = () => {
  const {
    isMuted,
    toggleMute,
    favorites,
    addFavorite,
    removeFavorite,
    isFavorite,
    setSelectedMedia,
    setReportItem,
  } = useAppStore();

  const [items, setItems] = useState<MediaItem[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(true);
  const [heartBurst, setHeartBurst] = useState<{ x: number; y: number } | null>(null);
  const [direction, setDirection] = useState<'up' | 'down'>('down');
  const [loading, setLoading] = useState(true);

  const videoRef = useRef<HTMLVideoElement | null>(null);
  const touchStartY = useRef<number | null>(null);
  const lastTapTime = useRef<number>(0);

  // Fetch TikTok-optimized items
  useEffect(() => {
    async function loadTikTokFeed() {
      setLoading(true);
      try {
        const res = await fetch('/api/media?mode=tiktok&page=1');
        const data = await res.json();
        if (data.items && data.items.length > 0) {
          setItems(data.items);
        }
      } catch (e) {
        console.error('Failed to load TikTok feed', e);
      } finally {
        setLoading(false);
      }
    }
    loadTikTokFeed();
  }, []);

  const currentItem = items[currentIndex];

  // Navigate next / prev
  const goToNext = useCallback(() => {
    if (currentIndex < items.length - 1) {
      setDirection('down');
      setCurrentIndex((prev) => prev + 1);
      setIsPlaying(true);
    }
  }, [currentIndex, items.length]);

  const goToPrev = useCallback(() => {
    if (currentIndex > 0) {
      setDirection('up');
      setCurrentIndex((prev) => prev - 1);
      setIsPlaying(true);
    }
  }, [currentIndex]);

  // Keyboard navigation
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'ArrowDown' || e.key === 'j') {
        goToNext();
      } else if (e.key === 'ArrowUp' || e.key === 'k') {
        goToPrev();
      } else if (e.key === 'm') {
        toggleMute();
      } else if (e.key === ' ') {
        e.preventDefault();
        setIsPlaying((p) => !p);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [goToNext, goToPrev, toggleMute]);

  // Touch swipe handling
  const handleTouchStart = (e: React.TouchEvent) => {
    touchStartY.current = e.touches[0].clientY;
  };

  const handleTouchEnd = (e: React.TouchEvent) => {
    if (touchStartY.current === null) return;
    const touchEndY = e.changedTouches[0].clientY;
    const diff = touchStartY.current - touchEndY;

    if (diff > 50) {
      // Swiped UP -> Go next
      goToNext();
    } else if (diff < -50) {
      // Swiped DOWN -> Go prev
      goToPrev();
    }
    touchStartY.current = null;
  };

  // Double tap to like with heart burst animation
  const handleContainerClick = (e: React.MouseEvent) => {
    const now = Date.now();
    const rect = e.currentTarget.getBoundingClientRect();
    const x = e.clientX - rect.left;
    const y = e.clientY - rect.top;

    if (now - lastTapTime.current < 300) {
      // Double tap detected!
      if (currentItem) {
        addFavorite(currentItem);
      }
      setHeartBurst({ x, y });
      setTimeout(() => setHeartBurst(null), 800);
    } else {
      // Single tap -> toggle playback
      setIsPlaying((p) => !p);
    }
    lastTapTime.current = now;
  };

  const isLiked = currentItem ? isFavorite(currentItem.id) : false;

  const handleLikeToggle = (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!currentItem) return;
    if (isLiked) {
      removeFavorite(currentItem.id);
    } else {
      addFavorite(currentItem);
    }
  };

  const handleShare = async (e: React.MouseEvent) => {
    e.stopPropagation();
    if (!currentItem) return;
    if (navigator.share) {
      try {
        await navigator.share({
          title: currentItem.title,
          url: currentItem.sourceUrl,
        });
      } catch {}
    } else {
      navigator.clipboard.writeText(currentItem.sourceUrl);
      alert('Link copied to clipboard!');
    }
  };

  if (loading) {
    return (
      <div className="h-[calc(100dvh-56px)] flex flex-col items-center justify-center bg-black text-zinc-400">
        <div className="w-10 h-10 border-4 border-tiktok-cyan border-t-tiktok-red rounded-full animate-spin mb-3" />
        <span className="text-sm font-semibold tracking-wide">Loading TikTok Feed...</span>
      </div>
    );
  }

  if (!currentItem) {
    return (
      <div className="h-[calc(100dvh-56px)] flex flex-col items-center justify-center bg-black text-zinc-400">
        <p>No vertical media items available right now.</p>
      </div>
    );
  }

  return (
    <div
      className="relative w-full h-[calc(100dvh-56px)] bg-black overflow-hidden select-none touch-none"
      onTouchStart={handleTouchStart}
      onTouchEnd={handleTouchEnd}
      onClick={handleContainerClick}
    >
      <AnimatePresence initial={false} custom={direction}>
        <motion.div
          key={currentItem.id}
          initial={{ y: direction === 'down' ? '100%' : '-100%', opacity: 0.9 }}
          animate={{ y: '0%', opacity: 1 }}
          exit={{ y: direction === 'down' ? '-100%' : '100%', opacity: 0.9 }}
          transition={{ duration: 0.28, ease: [0.32, 0.72, 0, 1] }}
          className="absolute inset-0 flex items-center justify-center bg-black"
        >
          {/* Media Player */}
          {currentItem.streamUrl ? (
            <video
              ref={videoRef}
              src={currentItem.streamUrl}
              poster={currentItem.thumbnail}
              autoPlay
              playsInline
              loop
              muted={isMuted}
              className="w-full h-full object-contain md:object-cover"
            />
          ) : currentItem.preview && currentItem.preview.endsWith('.mp4') ? (
            <video
              ref={videoRef}
              src={currentItem.preview}
              poster={currentItem.thumbnail}
              autoPlay
              playsInline
              loop
              muted={isMuted}
              className="w-full h-full object-contain md:object-cover"
            />
          ) : (
            <img
              src={currentItem.preview || currentItem.thumbnail}
              alt={currentItem.title}
              className="w-full h-full object-contain md:object-cover"
            />
          )}

          {/* Pause Indicator overlay */}
          {!isPlaying && (
            <div className="absolute inset-0 flex items-center justify-center bg-black/30 pointer-events-none">
              <div className="p-4 bg-black/60 rounded-full text-white backdrop-blur-sm">
                <Play className="w-12 h-12 fill-white translate-x-1" />
              </div>
            </div>
          )}

          {/* Animated Heart Burst on Double Tap */}
          {heartBurst && (
            <div
              className="absolute pointer-events-none transform -translate-x-1/2 -translate-y-1/2 animate-heart-burst"
              style={{ left: heartBurst.x, top: heartBurst.y }}
            >
              <Heart className="w-24 h-24 fill-tiktok-red text-tiktok-red drop-shadow-2xl" />
            </div>
          )}

          {/* Gradient Overlays for contrast */}
          <div className="absolute inset-0 bg-gradient-to-b from-black/40 via-transparent to-black/80 pointer-events-none" />

          {/* Bottom Left Info Details */}
          <div className="absolute bottom-4 left-4 right-20 z-20 text-white pointer-events-auto">
            {/* Provider and Author */}
            <div className="flex items-center gap-2 mb-2">
              <div className="w-8 h-8 rounded-full bg-zinc-800 border border-zinc-600 flex items-center justify-center overflow-hidden">
                {currentItem.author?.avatar ? (
                  <img src={currentItem.author.avatar} alt="" className="w-full h-full object-cover" />
                ) : (
                  <span className="text-xs font-bold text-tiktok-cyan">
                    {(currentItem.providerName || currentItem.provider).slice(0, 2).toUpperCase()}
                  </span>
                )}
              </div>
              <div className="flex items-center gap-1.5">
                <span className="text-sm font-bold text-zinc-100">
                  @{currentItem.author?.name || currentItem.providerName || currentItem.provider}
                </span>
                <CheckCircle2 className="w-3.5 h-3.5 text-tiktok-cyan fill-tiktok-cyan" />
              </div>
            </div>

            {/* Title */}
            <p className="text-sm font-medium line-clamp-2 text-zinc-200 mb-2 drop-shadow">
              {currentItem.title}
            </p>

            {/* Tags / Categories */}
            <div className="flex flex-wrap gap-1.5 mb-2">
              {currentItem.tags.slice(0, 3).map((tag, idx) => (
                <span
                  key={idx}
                  className="text-xs font-semibold text-zinc-300 hover:text-white transition-colors"
                >
                  #{tag.replace(/\s+/g, '')}
                </span>
              ))}
            </div>

            {/* Sound waveform & disc */}
            <div className="flex items-center gap-2 text-xs text-zinc-300">
              <Disc3 className="w-4 h-4 animate-spin text-tiktok-red" />
              <span className="truncate max-w-[180px]">
                Original Sound • {currentItem.providerName || currentItem.provider}
              </span>
            </div>
          </div>

          {/* Right Action Bar (TikTok style) */}
          <div className="absolute right-3 bottom-6 z-20 flex flex-col items-center gap-4 text-white">
            {/* Like Button */}
            <button
              onClick={handleLikeToggle}
              className="flex flex-col items-center group transition-transform active:scale-90"
            >
              <div
                className={`p-2.5 rounded-full transition-colors ${
                  isLiked ? 'bg-tiktok-red/20 text-tiktok-red' : 'bg-zinc-900/60 text-white hover:bg-zinc-800'
                }`}
              >
                <Heart className={`w-7 h-7 ${isLiked ? 'fill-tiktok-red text-tiktok-red' : ''}`} />
              </div>
              <span className="text-[11px] font-bold mt-1 drop-shadow">
                {isLiked ? (currentItem.likes || 120) + 1 : currentItem.likes || 120}
              </span>
            </button>

            {/* Save / Favorite */}
            <button
              onClick={handleLikeToggle}
              className="flex flex-col items-center transition-transform active:scale-90"
            >
              <div className="p-2.5 bg-zinc-900/60 rounded-full text-white hover:bg-zinc-800">
                <Bookmark className={`w-7 h-7 ${isLiked ? 'fill-tube-primary text-tube-primary' : ''}`} />
              </div>
              <span className="text-[11px] font-bold mt-1 drop-shadow">Save</span>
            </button>

            {/* Share */}
            <button
              onClick={handleShare}
              className="flex flex-col items-center transition-transform active:scale-90"
            >
              <div className="p-2.5 bg-zinc-900/60 rounded-full text-white hover:bg-zinc-800">
                <Share2 className="w-7 h-7" />
              </div>
              <span className="text-[11px] font-bold mt-1 drop-shadow">Share</span>
            </button>

            {/* Audio Mute/Unmute */}
            <button
              onClick={(e) => {
                e.stopPropagation();
                toggleMute();
              }}
              className="flex flex-col items-center transition-transform active:scale-90"
            >
              <div className="p-2.5 bg-zinc-900/60 rounded-full text-white hover:bg-zinc-800">
                {isMuted ? <VolumeX className="w-7 h-7 text-zinc-400" /> : <Volume2 className="w-7 h-7 text-emerald-400" />}
              </div>
              <span className="text-[11px] font-bold mt-1 drop-shadow">
                {isMuted ? 'Muted' : 'Sound'}
              </span>
            </button>

            {/* Full Details Modal */}
            <button
              onClick={(e) => {
                e.stopPropagation();
                setSelectedMedia(currentItem);
              }}
              className="flex flex-col items-center transition-transform active:scale-90"
            >
              <div className="p-2.5 bg-zinc-900/60 rounded-full text-white hover:bg-zinc-800">
                <Info className="w-7 h-7" />
              </div>
              <span className="text-[11px] font-bold mt-1 drop-shadow">Info</span>
            </button>
          </div>
        </motion.div>
      </AnimatePresence>

      {/* Progress Line */}
      <div className="absolute bottom-0 left-0 right-0 h-1 bg-zinc-800/80 z-30">
        <div
          className="h-full bg-tiktok-cyan transition-all duration-300"
          style={{ width: `${((currentIndex + 1) / items.length) * 100}%` }}
        />
      </div>
    </div>
  );
};
