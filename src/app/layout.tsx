import type { Metadata, Viewport } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'Nexus18 (All18) — High-Performance Adult Media Aggregator',
  description:
    'Multi-mode aggregation portal for free tube sites, animated GIFs, anime/hentai, and photo boards. Pornhub grid, TikTok vertical feed, and 𝕏 timeline views.',
  icons: {
    icon: '/favicon.ico',
  },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  themeColor: '#09090b',
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" className="dark">
      <body className="bg-zinc-950 text-zinc-100 min-h-screen antialiased select-none">
        {children}
      </body>
    </html>
  );
}
