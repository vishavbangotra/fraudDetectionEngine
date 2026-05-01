<script lang="ts">
  import type { Readable } from 'svelte/store';
  import type { FeedItem } from '$lib/streams';

  export let alerts: Readable<FeedItem[]>;

  $: items = $alerts;

  function fmtAmount(n: number): string {
    return n.toLocaleString(undefined, { style: 'currency', currency: 'USD' });
  }
  function fmtTime(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleTimeString();
  }
  function timeAgo(ms: number): string {
    const s = Math.max(0, Math.floor((Date.now() - ms) / 1000));
    if (s < 60) return `${s}s ago`;
    const m = Math.floor(s / 60);
    if (m < 60) return `${m}m ago`;
    return `${Math.floor(m / 60)}h ago`;
  }
</script>

<aside class="alerts card">
  <header>
    <div class="title-row">
      <div class="title-block">
        <h2>Flagged Transactions</h2>
        <span class="sub">high-risk · webhook fired</span>
      </div>
      <span class="count-pill" class:active={items.length > 0}>
        <span class="pulse" class:on={items.length > 0}></span>
        {items.length}
      </span>
    </div>
  </header>
  <ul>
    {#each items as a (a.transaction.transactionId)}
      <li class="alert-item">
        <div class="row1">
          <span class="badge">HIGH · {a.score}</span>
          <span class="amount">{fmtAmount(a.transaction.amount)}</span>
        </div>
        <div class="row2">
          <span class="party"><span class="lbl">customer</span><code>{a.transaction.customerId}</code></span>
          <span class="arrow">→</span>
          <span class="party"><span class="lbl">merchant</span><code>{a.transaction.merchantId}</code></span>
        </div>
        <div class="row3">
          <span class="meta">{a.transaction.country}{a.transaction.city ? ` · ${a.transaction.city}` : ''}</span>
          <span class="meta-time">{fmtTime(a.transaction.timestamp)}</span>
        </div>
        <div class="rules">
          {#each a.triggeredRules as r}<span class="rule">{r}</span>{/each}
        </div>
      </li>
    {/each}
    {#if items.length === 0}
      <li class="empty">
        <span class="shield">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
            <path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z" stroke-linejoin="round"/>
            <path d="M9 12l2 2 4-4" stroke-linecap="round" stroke-linejoin="round"/>
          </svg>
        </span>
        <div>No high-risk transactions.</div>
        <div class="empty-sub">Alerts appear here when <code>riskLevel == HIGH</code>.</div>
      </li>
    {/if}
  </ul>
</aside>

<style>
  .card {
    background: var(--bg2);
    border: 1px solid var(--border);
    border-radius: 14px;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
    overflow: hidden;
  }
  .alerts { display: flex; flex-direction: column; min-height: 0; height: 100%; }
  header {
    padding: 14px 16px;
    border-bottom: 1px solid var(--border);
    background: linear-gradient(180deg, #ffffff, #fff8f8);
  }
  .title-row { display: flex; justify-content: space-between; align-items: center; }
  .title-block { display: flex; flex-direction: column; gap: 1px; }
  h2 { font-size: 14px; font-weight: 600; margin: 0; color: var(--text); }
  .sub { font-size: 11px; color: var(--text3); }
  .count-pill {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 12px;
    font-weight: 600;
    padding: 4px 10px;
    background: var(--bg3);
    color: var(--text3);
    border-radius: 999px;
    font-variant-numeric: tabular-nums;
  }
  .count-pill.active { background: var(--high-bg); color: var(--high); }
  .pulse { width: 7px; height: 7px; border-radius: 50%; background: var(--text3); }
  .pulse.on { background: var(--high); animation: pulse 1.6s ease-in-out infinite; }
  @keyframes pulse {
    0%, 100% { box-shadow: 0 0 0 0 rgba(225, 29, 72, 0.5); }
    70% { box-shadow: 0 0 0 6px rgba(225, 29, 72, 0); }
  }

  ul { list-style: none; overflow-y: auto; flex: 1; margin: 0; padding: 0; }
  .alert-item {
    padding: 12px 16px;
    border-bottom: 1px solid var(--border2);
    font-size: 12px;
    display: flex;
    flex-direction: column;
    gap: 6px;
    border-left: 3px solid var(--high);
    transition: background 0.15s ease;
  }
  .alert-item:hover { background: var(--high-bg); }
  .row1 { display: flex; justify-content: space-between; align-items: center; }
  .badge {
    font-size: 10px;
    font-weight: 700;
    padding: 3px 8px;
    border-radius: 4px;
    color: #fff;
    background: var(--high);
    letter-spacing: 0.04em;
  }
  .amount { font-weight: 700; font-variant-numeric: tabular-nums; color: var(--text); font-size: 14px; }
  .row2 {
    display: flex;
    align-items: center;
    gap: 6px;
    color: var(--text2);
    font-size: 11px;
    flex-wrap: wrap;
  }
  .party { display: inline-flex; align-items: baseline; gap: 4px; }
  .party .lbl { font-size: 9.5px; text-transform: uppercase; letter-spacing: 0.05em; color: var(--text3); }
  .party code { font-family: var(--mono); font-size: 11px; color: var(--text); }
  .arrow { color: var(--text3); }
  .row3 { display: flex; justify-content: space-between; font-size: 11px; color: var(--text3); }
  .meta-time { font-variant-numeric: tabular-nums; }
  .rules { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 2px; }
  .rule {
    font-size: 10px;
    font-family: var(--mono);
    padding: 2px 6px;
    border-radius: 4px;
    background: var(--high-bg);
    color: var(--high);
    font-weight: 500;
    border: 1px solid rgba(225, 29, 72, 0.15);
  }

  .empty {
    padding: 40px 20px;
    text-align: center;
    color: var(--text3);
    font-size: 12px;
    display: flex;
    flex-direction: column;
    gap: 6px;
    align-items: center;
  }
  .empty .shield { color: var(--low); width: 36px; height: 36px; }
  .empty .shield svg { width: 100%; height: 100%; }
  .empty-sub { font-size: 11px; }
  .empty-sub code { font-family: var(--mono); background: var(--bg3); padding: 1px 4px; border-radius: 3px; font-size: 10.5px; }
</style>
