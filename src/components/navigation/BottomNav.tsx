'use client';

import React from 'react';
import { useAppStore } from '@/store/useAppStore';
import { LayoutGrid, Flame, MessageSquare, Heart, Settings } from 'lucide-react';
import { UIMode } from '@/types/media';

export const BottomNav: React.FC = () => {
  const { mode, setMode, favorites } = useAppStore();

  const navItems: { id: UIMode; label: string; icon: React.ComponentType<{ className?: string }> }[] = [
    { id: 'all18', label: 'All18', icon: LayoutGrid },
    { id: 'tiktok', label: 'TikTok', icon: Flame },
    { id: 'x', label: '𝕏 Feed', icon: MessageSquare },
    { id: 'favorites', label: 'Saved', icon: Heart },
    { id: 'settings', label: 'Providers', icon: Settings },
  ];

  return (
    <nav className="fixed bottom-0 left-0 right-0 z-40 bg-zinc-950/90 backdrop-blur-lg border-t border-zinc-800/80 px-2 py-1 md:py-2 select-none">
      <div className="max-w-xl mx-auto flex items-center justify-around">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = mode === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setMode(item.id)}
              className={`relative flex flex-col items-center justify-center py-1 px-3 rounded-xl transition-all duration-200 ${
                isActive
                  ? item.id === 'tiktok'
                    ? 'text-tiktok-cyan'
                    : item.id === 'x'
                    ? 'text-x-blue'
                    : 'text-tube-primary'
                  : 'text-zinc-400 hover:text-zinc-200'
              }`}
            >
              <div className="relative">
                <Icon className={`w-6 h-6 transition-transform ${isActive ? 'scale-110' : 'scale-100'}`} />
                {item.id === 'favorites' && favorites.length > 0 && (
                  <span className="absolute -top-1 -right-2 bg-tube-primary text-black font-bold text-[10px] w-4 h-4 rounded-full flex items-center justify-center">
                    {favorites.length > 99 ? '99+' : favorites.length}
                  </span>
                )}
                {item.id === 'tiktok' && (
                  <span className="absolute -top-1 -right-2 flex h-2 w-2">
                    <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-tiktok-red opacity-75"></span>
                    <span className="relative inline-flex rounded-full h-2 w-2 bg-tiktok-red"></span>
                  </span>
                )}
              </div>
              <span className={`text-[11px] font-medium tracking-tight mt-0.5 ${isActive ? 'font-semibold' : ''}`}>
                {item.label}
              </span>
            </button>
          );
        })}
      </div>
    </nav>
  );
};
