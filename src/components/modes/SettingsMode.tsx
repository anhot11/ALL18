'use client';

import React, { useState, useEffect } from 'react';
import { ProviderInfo, ScrapedThePornDudeSite } from '@/types/provider';
import {
  ShieldCheck,
  RefreshCw,
  Power,
  Sliders,
  ExternalLink,
  Database,
  CheckCircle,
  AlertTriangle,
  Globe,
  Sparkles,
} from 'lucide-react';

export const SettingsMode: React.FC = () => {
  const [providers, setProviders] = useState<ProviderInfo[]>([]);
  const [scrapedSites, setScrapedSites] = useState<ScrapedThePornDudeSite[]>([]);
  const [loading, setLoading] = useState(true);
  const [scraping, setScraping] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<'providers' | 'theporndude' | 'legal'>('providers');

  const loadData = async () => {
    setLoading(true);
    try {
      const [provRes, scrapeRes] = await Promise.all([
        fetch('/api/providers'),
        fetch('/api/admin/scrape'),
      ]);
      const provData = await provRes.json();
      const scrapeData = await scrapeRes.json();

      if (provData.providers) setProviders(provData.providers);
      if (scrapeData.sites) setScrapedSites(scrapeData.sites);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleToggle = async (id: string, currentEnabled: boolean) => {
    try {
      const res = await fetch('/api/providers', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ id, enabled: !currentEnabled }),
      });
      if (res.ok) {
        setProviders((prev) =>
          prev.map((p) => (p.id === id ? { ...p, enabled: !currentEnabled } : p))
        );
      }
    } catch (err) {
      console.error(err);
    }
  };

  const handleTriggerScraper = async () => {
    setScraping(true);
    setMessage('Scraping free categories from ThePornDude... Please wait.');
    try {
      const res = await fetch('/api/admin/scrape', { method: 'POST' });
      const data = await res.json();
      if (data.success) {
        setMessage(`Scrape complete! Saved ${data.totalSites} verified free sites.`);
        loadData();
      } else {
        setMessage('Scraping error: ' + data.error);
      }
    } catch (e: any) {
      setMessage('Failed to execute scraper: ' + e.message);
    } finally {
      setScraping(false);
    }
  };

  return (
    <div className="max-w-4xl mx-auto px-4 py-4 pb-28">
      {/* Header */}
      <div className="flex items-center justify-between mb-4 border-b border-zinc-800 pb-3">
        <div>
          <h2 className="text-xl font-black text-white flex items-center gap-2">
            <Sliders className="w-5 h-5 text-tube-primary" />
            <span>Provider Registry & Settings</span>
          </h2>
          <p className="text-xs text-zinc-400 mt-0.5">
            Manage connectors, scrape ThePornDude free lists, and inspect system integrity.
          </p>
        </div>

        {/* Refresh button */}
        <button
          onClick={loadData}
          disabled={loading}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-zinc-900 hover:bg-zinc-800 border border-zinc-700 rounded-lg text-xs font-semibold text-zinc-300 transition-colors"
        >
          <RefreshCw className={`w-3.5 h-3.5 ${loading ? 'animate-spin' : ''}`} />
          <span>Sync</span>
        </button>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-2 mb-6 border-b border-zinc-800">
        <button
          onClick={() => setActiveTab('providers')}
          className={`pb-2.5 px-3 text-xs font-bold transition-all relative ${
            activeTab === 'providers' ? 'text-tube-primary' : 'text-zinc-400 hover:text-zinc-200'
          }`}
        >
          <span>Active Connectors ({providers.length})</span>
          {activeTab === 'providers' && (
            <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-tube-primary" />
          )}
        </button>
        <button
          onClick={() => setActiveTab('theporndude')}
          className={`pb-2.5 px-3 text-xs font-bold transition-all relative ${
            activeTab === 'theporndude' ? 'text-tube-primary' : 'text-zinc-400 hover:text-zinc-200'
          }`}
        >
          <span>ThePornDude Curation ({scrapedSites.length})</span>
          {activeTab === 'theporndude' && (
            <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-tube-primary" />
          )}
        </button>
        <button
          onClick={() => setActiveTab('legal')}
          className={`pb-2.5 px-3 text-xs font-bold transition-all relative ${
            activeTab === 'legal' ? 'text-tube-primary' : 'text-zinc-400 hover:text-zinc-200'
          }`}
        >
          <span>Legal & Ethical</span>
          {activeTab === 'legal' && (
            <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-tube-primary" />
          )}
        </button>
      </div>

      {/* TAB 1: ACTIVE CONNECTORS */}
      {activeTab === 'providers' && (
        <div className="space-y-3">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            {providers.map((p) => (
              <div
                key={p.id}
                className="bg-zinc-900/70 border border-zinc-800/80 rounded-xl p-3.5 flex items-center justify-between gap-3 hover:border-zinc-700/80 transition-colors"
              >
                <div className="flex items-center gap-3 min-w-0">
                  <div className="w-10 h-10 rounded-xl bg-zinc-800 border border-zinc-700 flex items-center justify-center text-lg shrink-0">
                    {p.icon || '🌐'}
                  </div>
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <h4 className="text-sm font-bold text-white truncate">{p.name}</h4>
                      <span className="text-[10px] uppercase font-bold px-1.5 py-0.5 rounded bg-zinc-800 text-zinc-400 border border-zinc-700">
                        {p.category}
                      </span>
                    </div>
                    <p className="text-xs text-zinc-400 truncate mt-0.5">{p.description}</p>
                    <div className="flex items-center gap-2 text-[10px] text-zinc-500 mt-1">
                      <span>Priority: {p.priority}</span>
                      <span>•</span>
                      <span>{p.hasApi ? 'Official API' : 'Scraped Embed'}</span>
                    </div>
                  </div>
                </div>

                {/* Toggle switch */}
                <button
                  onClick={() => handleToggle(p.id, p.enabled)}
                  className={`p-2 rounded-xl transition-all ${
                    p.enabled
                      ? 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/30'
                      : 'bg-zinc-800 text-zinc-500 border border-zinc-700'
                  }`}
                  title={p.enabled ? 'Enabled' : 'Disabled'}
                >
                  <Power className="w-4 h-4" />
                </button>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* TAB 2: THEPORNDUDE CURATION & SCRAPER */}
      {activeTab === 'theporndude' && (
        <div className="space-y-4">
          <div className="bg-zinc-900 border border-zinc-800 rounded-xl p-4 flex flex-col sm:flex-row sm:items-center justify-between gap-4">
            <div>
              <h3 className="text-sm font-bold text-white flex items-center gap-1.5">
                <Globe className="w-4 h-4 text-tube-primary" />
                <span>ThePornDude.com Automated Free Scraper</span>
              </h3>
              <p className="text-xs text-zinc-400 mt-1 max-w-xl">
                Scrapes verified free tube sites, GIF platforms, anime/hentai repositories, and photo boards.
                Strictly ignores paid, cam, and membership services.
              </p>
            </div>

            <button
              onClick={handleTriggerScraper}
              disabled={scraping}
              className="flex items-center justify-center gap-2 px-4 py-2 bg-tube-primary hover:bg-tube-secondary text-black font-bold text-xs rounded-xl shadow-md transition-all shrink-0 disabled:opacity-50"
            >
              <RefreshCw className={`w-3.5 h-3.5 ${scraping ? 'animate-spin' : ''}`} />
              <span>{scraping ? 'Scraping...' : 'Run Scraper Now'}</span>
            </button>
          </div>

          {message && (
            <div className="p-3 bg-zinc-900 border border-tube-primary/30 rounded-lg text-xs text-tube-primary">
              {message}
            </div>
          )}

          {/* Scraped sites listing */}
          <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 gap-3">
            {scrapedSites.map((site, idx) => (
              <div
                key={idx}
                className="bg-zinc-900/50 border border-zinc-800/80 rounded-xl p-3 flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-center justify-between gap-2">
                    <span className="font-bold text-sm text-zinc-100 truncate">{site.name}</span>
                    <span className="text-[10px] text-zinc-400 bg-zinc-800 px-1.5 py-0.5 rounded">
                      {site.category}
                    </span>
                  </div>
                  <p className="text-xs text-zinc-400 mt-1 line-clamp-2">{site.description}</p>
                </div>

                <div className="flex items-center justify-between text-[11px] text-zinc-500 mt-3 pt-2 border-t border-zinc-800/60">
                  <span className="text-emerald-400 font-semibold">100% Free Public</span>
                  <a
                    href={site.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="flex items-center gap-1 hover:text-tube-primary transition-colors"
                  >
                    <span>Visit</span>
                    <ExternalLink className="w-3 h-3" />
                  </a>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* TAB 3: LEGAL & ETHICAL */}
      {activeTab === 'legal' && (
        <div className="bg-zinc-900 border border-zinc-800 rounded-2xl p-6 space-y-4">
          <div className="flex items-center gap-2 text-tube-primary font-bold">
            <ShieldCheck className="w-6 h-6" />
            <h3 className="text-base text-white">Legal & Ethical Compliance Statement</h3>
          </div>

          <div className="text-xs text-zinc-300 space-y-3 leading-relaxed">
            <p>
              <strong>1. Aggregation-Only Architecture:</strong> Nexus18 / All18 operates solely as an indexer and
              aggregator of publicly accessible adult media. We do NOT host, store, mirror, or re-encode full video
              files on any proprietary servers. All media playback occurs through the official public embed players or
              direct content delivery networks provided by the original host sites.
            </p>
            <p>
              <strong>2. DMCA & Copyright:</strong> All intellectual property, trademarks, logos, and audiovisual content
              remain the sole property of their respective creators and hosting providers. If you are a copyright owner
              seeking removal of content, please contact the origin host directly, as removing content from their service
              will automatically propagate to our aggregation engine.
            </p>
            <p>
              <strong>3. Age Restriction (18+ Only):</strong> This application is strictly intended for consenting adults
              aged 18 and older (or the applicable age of majority in your jurisdiction). Minors are strictly prohibited
              from accessing this software.
            </p>
            <p>
              <strong>4. Free Public Sites Only:</strong> In accordance with our curation guidelines, this aggregator
              strictly excludes paid subscription services, private cam sessions, and non-consensual materials.
            </p>
          </div>
        </div>
      )}
    </div>
  );
};
