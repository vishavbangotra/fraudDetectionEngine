import { writable, type Readable, type Writable } from 'svelte/store';
import type { ScoredTransaction } from './types';
import { streamUrl } from './api';

export interface FeedItem extends ScoredTransaction {
  receivedAt: number;
}

export interface ConnectionState {
  connected: boolean;
  lastError: string | null;
}

const TX_CAP = 200;
const ALERT_CAP = 50;

function cap<T>(arr: T[], n: number): T[] {
  return arr.length > n ? arr.slice(0, n) : arr;
}

export function pushFeed(list: FeedItem[], scored: ScoredTransaction, max: number): FeedItem[] {
  const next = [{ ...scored, receivedAt: Date.now() }, ...list];
  return cap(next, max);
}

export interface LiveFeed {
  transactions: Readable<FeedItem[]>;
  alerts: Readable<FeedItem[]>;
  txConnection: Readable<ConnectionState>;
  alertConnection: Readable<ConnectionState>;
  close: () => void;
}

export function connectLiveFeed(): LiveFeed {
  const transactions: Writable<FeedItem[]> = writable([]);
  const alerts: Writable<FeedItem[]> = writable([]);
  const txConnection: Writable<ConnectionState> = writable({ connected: false, lastError: null });
  const alertConnection: Writable<ConnectionState> = writable({ connected: false, lastError: null });

  const txEs = new EventSource(streamUrl('/api/stream/transactions'));
  txEs.addEventListener('ready', () => txConnection.set({ connected: true, lastError: null }));
  txEs.addEventListener('scored', (ev: MessageEvent) => {
    try {
      const scored: ScoredTransaction = JSON.parse(ev.data);
      transactions.update((list) => pushFeed(list, scored, TX_CAP));
      txConnection.set({ connected: true, lastError: null });
    } catch (e) {
      txConnection.update((s) => ({ ...s, lastError: (e as Error).message }));
    }
  });
  txEs.onerror = () => txConnection.set({ connected: false, lastError: 'connection error' });

  const alertEs = new EventSource(streamUrl('/api/stream/alerts'));
  alertEs.addEventListener('ready', () => alertConnection.set({ connected: true, lastError: null }));
  alertEs.addEventListener('flagged', (ev: MessageEvent) => {
    try {
      const scored: ScoredTransaction = JSON.parse(ev.data);
      alerts.update((list) => pushFeed(list, scored, ALERT_CAP));
      alertConnection.set({ connected: true, lastError: null });
    } catch (e) {
      alertConnection.update((s) => ({ ...s, lastError: (e as Error).message }));
    }
  });
  alertEs.onerror = () => alertConnection.set({ connected: false, lastError: 'connection error' });

  return {
    transactions,
    alerts,
    txConnection,
    alertConnection,
    close: () => {
      txEs.close();
      alertEs.close();
    }
  };
}
