import { NextRequest, NextResponse } from 'next/server';
import { getAllProviders, toggleProviderStatus, updateProviderPriority } from '@/lib/db';

export const dynamic = 'force-dynamic';

export async function GET() {
  try {
    const providers = getAllProviders();
    return NextResponse.json({
      success: true,
      providers,
    });
  } catch (error: any) {
    return NextResponse.json(
      { success: false, error: error.message },
      { status: 500 }
    );
  }
}

export async function POST(request: NextRequest) {
  try {
    const body = await request.json();
    const { id, enabled, priority } = body;

    if (!id) {
      return NextResponse.json({ success: false, error: 'Missing provider id' }, { status: 400 });
    }

    if (typeof enabled === 'boolean') {
      toggleProviderStatus(id, enabled);
    }

    if (typeof priority === 'number') {
      updateProviderPriority(id, priority);
    }

    return NextResponse.json({
      success: true,
      message: `Provider ${id} updated`,
    });
  } catch (error: any) {
    return NextResponse.json(
      { success: false, error: error.message },
      { status: 500 }
    );
  }
}
