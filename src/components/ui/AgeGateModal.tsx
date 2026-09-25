'use client';

import React from 'react';
import { useAppStore } from '@/store/useAppStore';
import { ShieldAlert, CheckCircle2 } from 'lucide-react';

export const AgeGateModal: React.FC = () => {
  const { ageGateAccepted, acceptAgeGate } = useAppStore();

  if (ageGateAccepted) return null;

  const handleExit = () => {
    window.location.href = 'https://www.google.com';
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/95 backdrop-blur-xl p-4 select-none">
      <div className="max-w-md w-full bg-zinc-950 border border-zinc-800 rounded-3xl p-6 sm:p-8 text-center space-y-5 shadow-2xl">
        <div className="w-16 h-16 rounded-2xl bg-tube-primary/10 border border-tube-primary/30 text-tube-primary mx-auto flex items-center justify-center">
          <ShieldAlert className="w-8 h-8" />
        </div>

        <div>
          <div className="flex items-center justify-center gap-1.5 mb-2">
            <span className="text-2xl font-black text-white">Nexus</span>
            <span className="bg-tube-primary text-black font-black text-sm px-2 py-0.5 rounded-md">
              18+
            </span>
          </div>
          <h2 className="text-lg font-bold text-zinc-100">Adult Content & Age Verification</h2>
        </div>

        <div className="text-xs text-zinc-400 space-y-2.5 text-left bg-zinc-900/60 p-4 rounded-2xl border border-zinc-800/80 leading-relaxed">
          <p>
            This website and application provides an aggregation portal for publicly indexed adult videos,
            animated GIFs, and artwork.
          </p>
          <p>
            By entering, you confirm and warrant under penalty of perjury that:
          </p>
          <ul className="list-disc list-inside space-y-1 text-zinc-300">
            <li>You are at least 18 years of age (or age of legal majority in your country).</li>
            <li>You agree to viewing adult media for personal enjoyment.</li>
            <li>Nexus18 does not host or upload any video files directly.</li>
          </ul>
        </div>

        <div className="flex flex-col sm:flex-row gap-3 pt-2">
          <button
            onClick={acceptAgeGate}
            className="flex-1 py-3 px-4 bg-tube-primary hover:bg-tube-secondary text-black font-extrabold text-sm rounded-xl transition-all shadow-lg shadow-tube-primary/20 active:scale-95"
          >
            I am 18 or Older — Enter
          </button>
          <button
            onClick={handleExit}
            className="py-3 px-4 bg-zinc-900 hover:bg-zinc-800 text-zinc-400 hover:text-white font-semibold text-sm rounded-xl border border-zinc-800 transition-colors"
          >
            Exit (Leave)
          </button>
        </div>
      </div>
    </div>
  );
};
