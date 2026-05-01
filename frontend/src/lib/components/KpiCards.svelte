<script lang="ts">
  import type { Readable } from 'svelte/store';
  import type { FeedItem } from '$lib/streams';
  import { computeKpis } from '$lib/metrics';
  import { statusFromRisk } from '$lib/types';
  import { onMount, onDestroy } from 'svelte';

  export let transactions: Readable<FeedItem[]>;

  const BUCKETS = 30;
  const BUCKET_MS = 2_000;

  let now = Date.now();
  let timer: ReturnType<typeof setInterval>;
  onMount(() => {
    timer = setInterval(() => (now = Date.now()), 1000);
  });
  onDestroy(() => clearInterval(timer));

  $: items = $transactions;
  $: kpis = computeKpis(items, now);

  type B = { count: number; volume: number; flagged: number; blocked: number };
  function buckets(arr: FeedItem[], nowTs: number): B[] {
    const out: B[] = Array.from({ length: BUCKETS }, () => ({ count: 0, volume: 0, flagged: 0, blocked: 0 }));
    for (const i of arr) {
      const age = nowTs - i.receivedAt;
      if (age < 0 || age >= BUCKETS * BUCKET_MS) continue;
      const idx = BUCKETS - 1 - Math.floor(age / BUCKET_MS);
      out[idx].count++;
      out[idx].volume += i.transaction.amount ?? 0;
      const s = statusFromRisk(i.riskLevel);
      if (s !== 'approved') out[idx].flagged++;
      if (s === 'blocked') out[idx].blocked++;
    }
    return out;
  }
  $: bs = buckets(items, now);

  function trend(values: number[]): number {
    const half = Math.floor(values.length / 2);
    const first = values.slice(0, half).reduce((a, b) => a + b, 0);
    const second = values.slice(half).reduce((a, b) => a + b, 0);
    if (first === 0 && second === 0) return 0;
    if (first === 0) return 100;
    return ((second - first) / first) * 100;
  }

  $: tpsSeries = bs.map((b) => b.count / (BUCKET_MS / 1000));
  $: volumeSeries = bs.map((b) => b.volume);
  $: riskSeries = bs.map((b) => (b.count === 0 ? 0 : b.flagged / b.count));
  $: blockedSeries = bs.map((b) => b.blocked);

  $: tpsTrend = trend(tpsSeries);
  $: volumeTrend = trend(volumeSeries);
  $: riskTrend = trend(riskSeries);
  $: blockedTrend = trend(blockedSeries);

  function fmtMoney(n: number): string {
    if (n >= 1_000_000) return `$${(n / 1_000_000).toFixed(1)}M`;
    if (n >= 10_000) return `$${(n / 1_000).toFixed(1)}K`;
    return n.toLocaleString(undefined, { style: 'currency', currency: 'USD', maximumFractionDigits: 0 });
  }
  function fmtTrend(t: number): string {
    if (!isFinite(t)) return '—';
    if (Math.abs(t) < 0.5) return '±0%';
    const sign = t > 0 ? '+' : '';
    return `${sign}${t.toFixed(0)}%`;
  }
  function trendClass(t: number, invert = false): string {
    if (!isFinite(t) || Math.abs(t) < 0.5) return 'flat';
    const positive = invert ? t < 0 : t > 0;
    return positive ? 'up' : 'down';
  }

  function spark(values: number[]): string {
    const max = Math.max(...values, 0.0001);
    const w = 100;
    const h = 28;
    const step = w / (values.length - 1);
    return values
      .map((v, i) => {
        const x = (i * step).toFixed(2);
        const y = (h - (v / max) * h).toFixed(2);
        return `${i === 0 ? 'M' : 'L'}${x},${y}`;
      })
      .join(' ');
  }
</script>

<div class="kpi-grid">
  <div class="kpi">
    <div class="head">
      <div class="label">
        <span class="dot tps"></span>Throughput
      </div>
      <span class="trend {trendClass(tpsTrend)}">{fmtTrend(tpsTrend)}</span>
    </div>
    <div class="value-row">
      <div class="value">{kpis.tps.toFixed(2)}<span class="suffix">tx/s</span></div>
      <svg class="spark" viewBox="0 0 100 28" preserveAspectRatio="none">
        <defs>
          <linearGradient id="g-tps" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stop-color="#4f46e5" stop-opacity="0.25" />
            <stop offset="100%" stop-color="#4f46e5" stop-opacity="0" />
          </linearGradient>
        </defs>
        <path d="{spark(tpsSeries)} L100,28 L0,28 Z" fill="url(#g-tps)" />
        <path d={spark(tpsSeries)} fill="none" stroke="#4f46e5" stroke-width="1.5" />
      </svg>
    </div>
    <div class="sub">last 60s · {items.length} total</div>
  </div>

  <div class="kpi">
    <div class="head">
      <div class="label"><span class="dot vol"></span>Volume <span class="muted">(60s)</span></div>
      <span class="trend {trendClass(volumeTrend)}">{fmtTrend(volumeTrend)}</span>
    </div>
    <div class="value-row">
      <div class="value">{fmtMoney(kpis.volume60s)}</div>
      <svg class="spark" viewBox="0 0 100 28" preserveAspectRatio="none">
        <defs>
          <linearGradient id="g-vol" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stop-color="#0ea5e9" stop-opacity="0.25" />
            <stop offset="100%" stop-color="#0ea5e9" stop-opacity="0" />
          </linearGradient>
        </defs>
        <path d="{spark(volumeSeries)} L100,28 L0,28 Z" fill="url(#g-vol)" />
        <path d={spark(volumeSeries)} fill="none" stroke="#0ea5e9" stroke-width="1.5" />
      </svg>
    </div>
    <div class="sub">avg ticket {kpis.tps > 0 ? fmtMoney(kpis.volume60s / Math.max(1, kpis.tps * 60)) : '$0'}</div>
  </div>

  <div class="kpi">
    <div class="head">
      <div class="label"><span class="dot risk"></span>Risk Rate</div>
      <span class="trend {trendClass(riskTrend, true)}">{fmtTrend(riskTrend)}</span>
    </div>
    <div class="value-row">
      <div class="value">{(kpis.riskRate * 100).toFixed(1)}<span class="suffix">%</span></div>
      <svg class="spark" viewBox="0 0 100 28" preserveAspectRatio="none">
        <defs>
          <linearGradient id="g-risk" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stop-color="#d97706" stop-opacity="0.25" />
            <stop offset="100%" stop-color="#d97706" stop-opacity="0" />
          </linearGradient>
        </defs>
        <path d="{spark(riskSeries)} L100,28 L0,28 Z" fill="url(#g-risk)" />
        <path d={spark(riskSeries)} fill="none" stroke="#d97706" stroke-width="1.5" />
      </svg>
    </div>
    <div class="sub">flagged / scored</div>
  </div>

  <div class="kpi">
    <div class="head">
      <div class="label"><span class="dot blk"></span>Blocked</div>
      <span class="trend {trendClass(blockedTrend, true)}">{fmtTrend(blockedTrend)}</span>
    </div>
    <div class="value-row">
      <div class="value">{kpis.blockedCount}</div>
      <svg class="spark" viewBox="0 0 100 28" preserveAspectRatio="none">
        <defs>
          <linearGradient id="g-blk" x1="0" x2="0" y1="0" y2="1">
            <stop offset="0%" stop-color="#e11d48" stop-opacity="0.25" />
            <stop offset="100%" stop-color="#e11d48" stop-opacity="0" />
          </linearGradient>
        </defs>
        <path d="{spark(blockedSeries)} L100,28 L0,28 Z" fill="url(#g-blk)" />
        <path d={spark(blockedSeries)} fill="none" stroke="#e11d48" stroke-width="1.5" />
      </svg>
    </div>
    <div class="sub">high-risk · webhook fired</div>
  </div>
</div>

<style>
  .kpi-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 14px;
  }
  .kpi {
    background: var(--bg2);
    border: 1px solid var(--border);
    border-radius: 14px;
    padding: 16px 18px;
    display: flex;
    flex-direction: column;
    gap: 10px;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
    transition: transform 0.18s ease, box-shadow 0.18s ease, border-color 0.18s ease;
  }
  .kpi:hover {
    transform: translateY(-1px);
    box-shadow: 0 4px 14px rgba(15, 23, 42, 0.06);
    border-color: var(--accent-border);
  }
  .head {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .label {
    display: flex;
    align-items: center;
    gap: 7px;
    font-size: 11.5px;
    font-weight: 500;
    color: var(--text2);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }
  .label .muted { color: var(--text3); text-transform: none; letter-spacing: 0; font-weight: 400; }
  .label .dot { width: 7px; height: 7px; border-radius: 50%; display: inline-block; }
  .label .dot.tps { background: #4f46e5; }
  .label .dot.vol { background: #0ea5e9; }
  .label .dot.risk { background: #d97706; }
  .label .dot.blk { background: #e11d48; }
  .trend {
    font-size: 11px;
    font-weight: 600;
    padding: 2px 7px;
    border-radius: 999px;
    font-variant-numeric: tabular-nums;
  }
  .trend.up { background: var(--low-bg); color: var(--low); }
  .trend.down { background: var(--high-bg); color: var(--high); }
  .trend.flat { background: var(--bg3); color: var(--text3); }
  .value-row {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    gap: 10px;
  }
  .value {
    font-size: 26px;
    font-weight: 600;
    color: var(--text);
    font-variant-numeric: tabular-nums;
    letter-spacing: -0.02em;
    line-height: 1;
  }
  .suffix {
    font-size: 13px;
    font-weight: 500;
    color: var(--text3);
    margin-left: 4px;
  }
  .spark {
    width: 88px;
    height: 28px;
    flex-shrink: 0;
  }
  .sub {
    font-size: 11px;
    color: var(--text3);
  }
</style>
