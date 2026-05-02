import type { FeedItem } from './streams';
import { statusFromRisk } from './types';

export interface Kpis {
  tps: number;
  volume60s: number;
  riskRate: number;
  blockedCount: number;
}

export function computeKpis(items: FeedItem[], now: number = Date.now()): Kpis {
  const windowStart = now - 60_000;
  const recent = items.filter((i) => i.receivedAt >= windowStart);
  const volume60s = recent.reduce((sum, i) => sum + (i.transaction.amount ?? 0), 0);
  const flagged = recent.filter((i) => i.riskLevel !== 'LOW').length;
  const blockedCount = items.filter((i) => statusFromRisk(i.riskLevel) === 'blocked').length;
  const tps = recent.length / 60;
  const riskRate = recent.length === 0 ? 0 : flagged / recent.length;
  return { tps, volume60s, riskRate, blockedCount };
}
