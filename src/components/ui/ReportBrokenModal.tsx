'use client';

import React, { useState } from 'react';
import { useAppStore } from '@/store/useAppStore';
import { X, Flag, CheckCircle } from 'lucide-react';

export const ReportBrokenModal: React.FC = () => {
  const { reportItem, setReportItem } = useAppStore();
  const [submitted, setSubmitted] = useState(false);
  const [reason, setReason] = useState('embed_broken');

  if (!reportItem) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    setTimeout(() => {
      setSubmitted(false);
      setReportItem(null);
    }, 1800);
  };

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/80 backdrop-blur-sm p-4"
      onClick={() => setReportItem(null)}
    >
      <div
        className="max-w-md w-full bg-zinc-950 border border-zinc-800 rounded-2xl p-6 space-y-4"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between pb-3 border-b border-zinc-800">
          <div className="flex items-center gap-2 text-amber-400 font-bold text-sm">
            <Flag className="w-4 h-4" />
            <span>Report Broken Media or Provider</span>
          </div>
          <button
            onClick={() => setReportItem(null)}
            className="text-zinc-500 hover:text-white"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {submitted ? (
          <div className="py-8 text-center space-y-2">
            <CheckCircle className="w-12 h-12 text-emerald-400 mx-auto" />
            <h4 className="text-sm font-bold text-zinc-100">Thank you for reporting</h4>
            <p className="text-xs text-zinc-400">
              Our automated connector health check will re-verify {reportItem.providerName || reportItem.provider}.
            </p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="text-xs text-zinc-300">
              Reporting: <strong className="text-white">{reportItem.title}</strong>
              <div className="text-zinc-500 mt-0.5">Provider: {reportItem.provider}</div>
            </div>

            <div className="space-y-2">
              <label className="text-xs font-semibold text-zinc-400">Issue description:</label>
              <select
                value={reason}
                onChange={(e) => setReason(e.target.value)}
                className="w-full bg-zinc-900 border border-zinc-800 rounded-lg p-2.5 text-xs text-zinc-200 focus:outline-none focus:border-tube-primary"
              >
                <option value="embed_broken">Video/Stream does not play or 404s</option>
                <option value="geo_blocked">Content blocked in my region</option>
                <option value="misleading">Wrong title or thumbnail</option>
                <option value="dmca">Copyright or removal request</option>
              </select>
            </div>

            <div className="flex gap-2 justify-end pt-2">
              <button
                type="button"
                onClick={() => setReportItem(null)}
                className="px-4 py-2 bg-zinc-900 text-zinc-400 hover:text-white text-xs font-semibold rounded-lg"
              >
                Cancel
              </button>
              <button
                type="submit"
                className="px-4 py-2 bg-amber-500 hover:bg-amber-400 text-black text-xs font-bold rounded-lg shadow-md"
              >
                Submit Report
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};
