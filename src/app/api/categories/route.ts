import { NextResponse } from 'next/server';
import { registry } from '@/lib/connectors/registry';

export const dynamic = 'force-dynamic';

export async function GET() {
  try {
    const connectors = registry.getEnabledConnectors();
    const categoriesSet = new Set<string>();

    for (const c of connectors) {
      try {
        const cats = await c.getCategories();
        cats.forEach((cat) => categoriesSet.add(cat));
      } catch {
        // ignore
      }
    }

    const defaultCats = [
      'Trending',
      '4K Ultra HD',
      'Amateur',
      'Blowjob',
      'Brunette',
      'POV',
      'MILF',
      'Anime & Hentai',
      'Animated GIFs',
      'Cosplay',
      'Lesbian',
      'Hardcore',
      'VR',
    ];

    defaultCats.forEach((c) => categoriesSet.add(c));

    return NextResponse.json({
      success: true,
      categories: Array.from(categoriesSet),
    });
  } catch (error: any) {
    return NextResponse.json(
      { success: false, error: error.message },
      { status: 500 }
    );
  }
}
