import { describe, it, expect } from 'vitest';
import { computeKpis } from './metrics';
import type { FeedItem } from './streams';

function item(id: string, level: 'LOW' | 'MEDIUM' | 'HIGH', amount: number, ageMs: number, now: number): FeedItem {
  return {
    transaction: {
      transactionId: id,
      customerId: 'c',
      merchantId: 'm',
      amount,
      country: 'US',
      deviceId: 'd',
      timestamp: new Date(now - ageMs).toISOString()
    },
    score: level === 'HIGH' ? 90 : level === 'MEDIUM' ? 50 : 10,
    triggeredRules: [],
    riskLevel: level,
    receivedAt: now - ageMs
  };
}

describe('computeKpis', () => {
  it('returns zeros when there are no items', () => {
    expect(computeKpis([], Date.now())).toEqual({ tps: 0, volume60s: 0, riskRate: 0, blockedCount: 0 });
  });

  it('counts only items inside the 60s window for tps/volume/riskRate', () => {
    const now = 1_000_000;
    const items: FeedItem[] = [
      item('a', 'LOW', 100, 1_000, now),
      item('b', 'HIGH', 5_000, 30_000, now),
      item('c', 'MEDIUM', 1_000, 90_000, now), // outside window — excluded from window stats
      item('d', 'LOW', 50, 0, now)
    ];

    const kpis = computeKpis(items, now);
    expect(kpis.volume60s).toBe(5150);
    expect(kpis.tps).toBeCloseTo(3 / 60, 5);
    expect(kpis.riskRate).toBeCloseTo(1 / 3, 5); // only HIGH counts as risky in window
  });

  it('blockedCount counts HIGH items across the entire feed (not just window)', () => {
    const now = 1_000_000;
    const items: FeedItem[] = [
      item('a', 'HIGH', 100, 1_000, now),
      item('b', 'HIGH', 100, 90_000, now),
      item('c', 'LOW', 100, 1_000, now)
    ];
    expect(computeKpis(items, now).blockedCount).toBe(2);
  });
});
