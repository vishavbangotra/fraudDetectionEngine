<script lang="ts">
  import type { Readable } from 'svelte/store';
  import type { FeedItem } from '$lib/streams';
  import { statusFromRisk, type UiStatus } from '$lib/types';

  export let transactions: Readable<FeedItem[]>;

  type Filter = 'all' | UiStatus;
  let filter: Filter = 'all';

  $: items = $transactions;
  $: filtered = filter === 'all' ? items : items.filter((i) => statusFromRisk(i.riskLevel) === filter);
  $: counts = (() => {
    let approved = 0, review = 0, blocked = 0;
    for (const i of items) {
      const s = statusFromRisk(i.riskLevel);
      if (s === 'approved') approved++;
      else if (s === 'review') review++;
      else blocked++;
    }
    return { all: items.length, approved, review, blocked };
  })();

  function fmtAmount(n: number): string {
    return n.toLocaleString(undefined, { style: 'currency', currency: 'USD' });
  }
  function fmtTime(iso: string): string {
    const d = new Date(iso);
    return d.toLocaleTimeString();
  }
</script>

<section class="feed card">
  <header class="feed-header">
    <div class="title-row">
      <div class="title-block">
        <h2>All Transactions</h2>
        <span class="sub">scored stream · live</span>
      </div>
      <span class="total-pill">{counts.all}</span>
    </div>
    <div class="tabs">
      {#each [['all','All'],['approved','Approved'],['review','Review'],['blocked','Blocked']] as const as [f, label]}
        <button class:active={filter === f} on:click={() => (filter = f)}>
          <span>{label}</span>
          <span class="count">{counts[f]}</span>
        </button>
      {/each}
    </div>
  </header>
  <div class="rows-wrap">
    <div class="rows-head">
      <span>Status</span>
      <span>Txn</span>
      <span>Merchant</span>
      <span>Amount</span>
      <span>Country</span>
      <span>Score</span>
      <span>Time</span>
    </div>
    <ul class="rows">
      {#each filtered as item (item.transaction.transactionId)}
        {@const status = statusFromRisk(item.riskLevel)}
        <li class="row" class:blocked={status === 'blocked'} class:review={status === 'review'}>
          <span class="status-tag {status}">{status}</span>
          <span class="txn-id" title={item.transaction.transactionId}>{item.transaction.transactionId.slice(0, 10)}…</span>
          <span class="merchant" title={item.transaction.merchantId}>{item.transaction.merchantId}</span>
          <span class="amount">{fmtAmount(item.transaction.amount)}</span>
          <span class="country">{item.transaction.country}</span>
          <span class="score">{item.score}</span>
          <span class="time">{fmtTime(item.transaction.timestamp)}</span>
        </li>
      {/each}
      {#if filtered.length === 0}
        <li class="empty">
          <span class="empty-icon">⏵</span>
          No transactions yet — click <strong>Simulate</strong> to start the stream.
        </li>
      {/if}
    </ul>
  </div>
</section>

<style>
  .card {
    background: var(--bg2);
    border: 1px solid var(--border);
    border-radius: 14px;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
    overflow: hidden;
  }
  .feed { display: flex; flex-direction: column; min-height: 0; height: 100%; }
  .feed-header {
    padding: 14px 16px 10px;
    border-bottom: 1px solid var(--border);
    background: linear-gradient(180deg, #ffffff, #fafbff);
  }
  .title-row { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
  .title-block { display: flex; flex-direction: column; gap: 1px; }
  h2 { font-size: 14px; font-weight: 600; margin: 0; color: var(--text); }
  .sub { font-size: 11px; color: var(--text3); }
  .total-pill {
    font-size: 11px;
    font-weight: 600;
    padding: 3px 9px;
    background: var(--bg3);
    color: var(--text2);
    border-radius: 999px;
    font-variant-numeric: tabular-nums;
  }
  .tabs { display: flex; gap: 4px; }
  .tabs button {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    padding: 5px 10px;
    border-radius: 6px;
    border: 1px solid var(--border);
    background: var(--bg2);
    cursor: pointer;
    color: var(--text2);
    font-weight: 500;
    transition: all 0.15s ease;
  }
  .tabs button:hover { border-color: var(--accent-border); color: var(--accent); }
  .tabs button .count {
    font-size: 10px;
    padding: 1px 5px;
    background: var(--bg3);
    border-radius: 4px;
    color: var(--text3);
    font-variant-numeric: tabular-nums;
  }
  .tabs button.active { background: var(--accent-light); border-color: var(--accent-border); color: var(--accent); }
  .tabs button.active .count { background: var(--accent); color: #fff; }

  .rows-wrap { flex: 1; min-height: 0; display: flex; flex-direction: column; }
  .rows-head {
    display: grid;
    grid-template-columns: 72px 90px 1fr 100px 50px 50px 70px;
    gap: 8px;
    padding: 8px 16px;
    font-size: 10px;
    text-transform: uppercase;
    letter-spacing: 0.06em;
    color: var(--text3);
    background: var(--bg);
    border-bottom: 1px solid var(--border);
    font-weight: 600;
  }
  .rows-head span:nth-child(4), .rows-head span:nth-child(7) { text-align: right; }
  .rows-head span:nth-child(6) { text-align: center; }

  .rows { list-style: none; overflow-y: auto; flex: 1; margin: 0; padding: 0; }
  .row {
    display: grid;
    grid-template-columns: 72px 90px 1fr 100px 50px 50px 70px;
    gap: 8px;
    padding: 9px 16px;
    border-bottom: 1px solid var(--border2);
    font-size: 12px;
    align-items: center;
  }
  .row:hover { background: var(--bg); }
  .row.blocked { background: linear-gradient(90deg, rgba(225, 29, 72, 0.04), transparent 60%); }
  .row.blocked:hover { background: linear-gradient(90deg, rgba(225, 29, 72, 0.08), var(--bg) 80%); }
  .row.review { background: linear-gradient(90deg, rgba(217, 119, 6, 0.04), transparent 60%); }
  .row.review:hover { background: linear-gradient(90deg, rgba(217, 119, 6, 0.08), var(--bg) 80%); }

  .status-tag {
    font-size: 9.5px;
    font-weight: 700;
    padding: 3px 7px;
    border-radius: 4px;
    text-transform: uppercase;
    letter-spacing: 0.04em;
    text-align: center;
  }
  .status-tag.approved { color: var(--low); background: var(--low-bg); }
  .status-tag.review { color: var(--med); background: var(--med-bg); }
  .status-tag.blocked { color: var(--high); background: var(--high-bg); }
  .txn-id { font-family: var(--mono); color: var(--text2); font-size: 11px; }
  .merchant { color: var(--text); font-weight: 500; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .amount { font-variant-numeric: tabular-nums; text-align: right; font-weight: 600; color: var(--text); }
  .country { color: var(--text2); font-family: var(--mono); font-size: 11px; }
  .score { font-variant-numeric: tabular-nums; color: var(--text2); text-align: center; font-weight: 500; }
  .time { font-variant-numeric: tabular-nums; color: var(--text3); text-align: right; font-size: 11px; }

  .empty { padding: 36px 20px; text-align: center; color: var(--text3); font-size: 12px; display: flex; flex-direction: column; gap: 8px; align-items: center; }
  .empty-icon { font-size: 24px; color: var(--accent); opacity: 0.4; }
</style>
