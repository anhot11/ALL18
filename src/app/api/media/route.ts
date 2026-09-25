import { NextRequest, NextResponse } from 'next/server';
import { registry } from '@/lib/connectors/registry';
import { FilterOptions } from '@/types/media';

export const dynamic = 'force-dynamic';

export async function GET(request: NextRequest) {
  try {
    const { searchParams } = new URL(request.url);
    const query = searchParams.get('q') || '';
    const mode = (searchParams.get('mode') || 'all18') as 'all18' | 'tiktok' | 'x' | 'trending' | 'latest';
    const page = parseInt(searchParams.get('page') || '1', 10);
    const category = searchParams.get('category') || undefined;
    const type = (searchParams.get('type') as any) || undefined;
    const provider = searchParams.get('provider') || undefined;
    const duration = (searchParams.get('duration') as any) || undefined;

    const filters: FilterOptions = {
      category,
      type,
      provider,
      duration,
    };

    let items = [];
    if (query.trim()) {
      items = await registry.searchAll(query, page, filters);
    } else {
      items = await registry.getAggregatedFeed(mode, page, filters);
    }

    return NextResponse.json({
      success: true,
      mode,
      page,
      count: items.length,
      items,
    });
  } catch (error: any) {
    console.error('API /api/media error:', error);
    return NextResponse.json(
      { success: false, error: error.message || 'Internal server error', items: [] },
      { status: 500 }
    );
  }
}
