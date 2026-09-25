import { NextRequest, NextResponse } from 'next/server';
import { registry } from '@/lib/connectors/registry';

export const dynamic = 'force-dynamic';

export async function GET(
  request: NextRequest,
  { params }: { params: { id: string } }
) {
  try {
    const id = decodeURIComponent(params.id);
    const item = await registry.getVideoDetails(id);

    if (!item) {
      return NextResponse.json(
        { success: false, error: 'Media not found' },
        { status: 404 }
      );
    }

    return NextResponse.json({
      success: true,
      item,
    });
  } catch (error: any) {
    return NextResponse.json(
      { success: false, error: error.message || 'Failed to fetch media details' },
      { status: 500 }
    );
  }
}
