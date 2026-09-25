import { NextResponse } from 'next/server';
import { scrapeAllFreeCategories } from '@/lib/theporndude-scraper';
import { getScrapedSites } from '@/lib/db';

export const dynamic = 'force-dynamic';

export async function GET() {
  try {
    const scraped = getScrapedSites();
    return NextResponse.json({
      success: true,
      count: scraped.length,
      sites: scraped,
    });
  } catch (error: any) {
    return NextResponse.json(
      { success: false, error: error.message },
      { status: 500 }
    );
  }
}

export async function POST() {
  try {
    const result = await scrapeAllFreeCategories();
    return NextResponse.json({
      success: true,
      scrapedCount: result.scrapedCount,
      totalSites: result.sites.length,
    });
  } catch (error: any) {
    return NextResponse.json(
      { success: false, error: error.message },
      { status: 500 }
    );
  }
}
