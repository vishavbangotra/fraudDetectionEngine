import { describe, it, expect } from 'vitest';
import { pushFeed, type FeedItem } from './streams';
import { statusFromRisk, type ScoredTransaction } from './types';

function scored(id: string, level: 'LOW' | 'MEDIUM' | 'HIGH' = 'LOW', amount = 100): ScoredTransaction {
  return {
    transaction: {
      transactionId: id,
      customerId: 'c',
      merchantId: 'm',
      amount,
      country: 'US',
      deviceId: 'd',
      timestamp: new Date().toISOString()
    },
    score: level === 'HIGH' ? 90 : level === 'MEDIUM' ? 50 : 10,
    triggeredRules: [],
    riskLevel: level
  };
}

describe('pushFeed', () => {
  it('prepends new items so newest is first', () => {
    let list: FeedItem[] = [];
    list = pushFeed(list, scored('a'), 10);
    list = pushFeed(list, scored('b'), 10);
    expect(list[0].transaction.transactionId).toBe('b');
    expect(list[1].transaction.transactionId).toBe('a');
  });

  it('caps the list to the max length', () => {
    let list: FeedItem[] = [];
    for (let i = 0; i < 25; i++) list = pushFeed(list, scored(`t${i}`), 10);
    expect(list).toHaveLength(10);
    expect(list[0].transaction.transactionId).toBe('t24');
    expect(list[9].transaction.transactionId).toBe('t15');
  });

  it('attaches receivedAt timestamp', () => {
    const before = Date.now();
    const list = pushFeed([], scored('x'), 5);
    expect(list[0].receivedAt).toBeGreaterThanOrEqual(before);
  });
});

describe('statusFromRisk', () => {
  it('maps risk levels to UI status', () => {
    expect(statusFromRisk('LOW')).toBe('approved');
    expect(statusFromRisk('MEDIUM')).toBe('review');
    expect(statusFromRisk('HIGH')).toBe('blocked');
  });
});
