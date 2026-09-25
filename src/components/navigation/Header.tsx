'use client';

import React, { useState, useEffect } from 'react';
import { useAppStore } from '@/store/useAppStore';
import { Search, X, Volume2, VolumeX, ShieldAlert, Sparkles } from 'lucide-react';

export const Header: React.FC = () => {
  const {
    mode,
    setMode,
    searchQuery,
    setSearchQuery,
    isMuted,
    toggleMute,
    activeProvider,
    setActiveProvider,
  } = useAppStore();

  const [inputVal, setInputVal] = useState(searchQuery);

  useEffect(() => {
    setInputVal(searchQuery);
  }, [searchQuery]);

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSearchQuery(inputVal.trim());
  };

  const handleClear = () => {
    setInputVal('');
    setSearchQuery('');
  };

  // Only render standard top header when not in full-screen TikTok mode on small screens
  return (
    <header className="sticky top-0 z-30 bg-zinc-950/90 backdrop-blur-md border-b border-zinc-800/80 px-4 py-2.5 transition-all">
      <div className="max-w-7xl mx-auto flex items-center justify-between gap-3">
        {/* Logo */}
        <div
          onClick={() => {
            setSearchQuery('');
            setMode('all18');
          }}
          className="flex items-center gap-1 cursor-pointer select-none group"
        >
          <span className="text-xl font-black tracking-tight text-white group-hover:text-zinc-200">
            Nexus
          </span>
          <span className="bg-tube-primary text-black font-black text-xs px-2 py-0.5 rounded-md uppercase tracking-wider shadow-sm group-hover:bg-tube-secondary transition-colors">
            18
          </span>
        </div>

        {/* Global Search Bar */}
        <form onSubmit={handleSearchSubmit} className="flex-1 max-w-lg relative">
          <div className="relative flex items-center">
            <Search className="w-4 h-4 text-zinc-400 absolute left-3 pointer-events-none" />
            <input
              type="text"
              value={inputVal}
              onChange={(e) => setInputVal(e.target.value)}
              placeholder="Search across all tubes, GIFs & anime..."
              className="w-full bg-zinc-900/90 text-sm text-zinc-100 placeholder-zinc-500 rounded-full pl-9 pr-9 py-1.5 border border-zinc-700 focus:outline-none focus:border-tube-primary focus:ring-1 focus:ring-tube-primary transition-all"
            />
            {inputVal && (
              <button
                type="button"
                onClick={handleClear}
                className="absolute right-3 text-zinc-400 hover:text-white"
              >
                <X className="w-4 h-4" />
              </button>
            )}
          </div>
        </form>

        {/* Right Actions: Mute (Total Silence 0dB), Desktop Mode Pills */}
        <div className="flex items-center gap-2">
          {/* Audio Safety Toggle */}
          <button
            onClick={toggleMute}
            title={isMuted ? 'Muted (0 dB Safe Mode)' : 'Sound Enabled'}
            className={`flex items-center gap-1.5 px-2.5 py-1.5 rounded-full text-xs font-medium border transition-all ${
              isMuted
                ? 'bg-zinc-900 border-zinc-700 text-zinc-300 hover:bg-zinc-800'
                : 'bg-emerald-500/10 border-emerald-500/40 text-emerald-400 hover:bg-emerald-500/20'
            }`}
          >
            {isMuted ? <VolumeX className="w-3.5 h-3.5 text-zinc-400" /> : <Volume2 className="w-3.5 h-3.5 text-emerald-400" />}
            <span className="hidden sm:inline">{isMuted ? '0 dB Muted' : 'Audio On'}</span>
          </button>

          {/* Desktop Mode Quick Switch */}
          <div className="hidden md:flex items-center bg-zinc-900 p-0.5 rounded-full border border-zinc-800">
            <button
              onClick={() => setMode('all18')}
              className={`px-3 py-1 text-xs font-semibold rounded-full transition-colors ${
                mode === 'all18' ? 'bg-tube-primary text-black' : 'text-zinc-400 hover:text-white'
              }`}
            >
              Tube Grid
            </button>
            <button
              onClick={() => setMode('tiktok')}
              className={`px-3 py-1 text-xs font-semibold rounded-full transition-colors ${
                mode === 'tiktok' ? 'bg-tiktok-red text-white' : 'text-zinc-400 hover:text-white'
              }`}
            >
              TikTok
            </button>
            <button
              onClick={() => setMode('x')}
              className={`px-3 py-1 text-xs font-semibold rounded-full transition-colors ${
                mode === 'x' ? 'bg-x-blue text-white' : 'text-zinc-400 hover:text-white'
              }`}
            >
              𝕏 Feed
            </button>
          </div>
        </div>
      </div>
    </header>
  );
};
