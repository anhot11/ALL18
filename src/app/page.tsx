'use client';

import React, { useEffect } from 'react';
import { useAppStore } from '@/store/useAppStore';
import { Header } from '@/components/navigation/Header';
import { BottomNav } from '@/components/navigation/BottomNav';
import { All18Mode } from '@/components/modes/All18Mode';
import { TikTokMode } from '@/components/modes/TikTokMode';
import { XTimelineMode } from '@/components/modes/XTimelineMode';
import { FavoritesMode } from '@/components/modes/FavoritesMode';
import { SettingsMode } from '@/components/modes/SettingsMode';
import { MediaModal } from '@/components/player/MediaModal';
import { AgeGateModal } from '@/components/ui/AgeGateModal';
import { ReportBrokenModal } from '@/components/ui/ReportBrokenModal';

export default function Home() {
  const { mode, hydrateFromLocalStorage } = useAppStore();

  useEffect(() => {
    hydrateFromLocalStorage();
  }, [hydrateFromLocalStorage]);

  return (
    <main className="min-h-screen bg-zinc-950 flex flex-col justify-between">
      {/* Top Header (sticky on grid, x, and settings modes) */}
      <Header />

      {/* Main Content Viewport */}
      <div className="flex-1 w-full">
        {mode === 'all18' && <All18Mode />}
        {mode === 'tiktok' && <TikTokMode />}
        {mode === 'x' && <XTimelineMode />}
        {mode === 'favorites' && <FavoritesMode />}
        {mode === 'settings' && <SettingsMode />}
      </div>

      {/* Overlays & Modals */}
      <MediaModal />
      <AgeGateModal />
      <ReportBrokenModal />

      {/* Bottom Navigation */}
      <BottomNav />
    </main>
  );
}
