<script lang="ts">
  import type { Readable } from 'svelte/store';
  import type { FeedItem } from '$lib/streams';
  import { statusFromRisk } from '$lib/types';
  import { onMount, onDestroy } from 'svelte';

  export let transactions: Readable<FeedItem[]>;
  export let alerts: Readable<FeedItem[]>;

  let now = Date.now();
  let timer: ReturnType<typeof setInterval>;
  onMount(() => {
    timer = setInterval(() => (now = Date.now()), 1000);
  });
  onDestroy(() => clearInterval(timer));

  $: items = $transactions;
  $: alertItems = $alerts;
  $: recent = items.filter((i) => now - i.receivedAt < 60_000);
  $: tps = recent.length / 60;

  function counts(arr: FeedItem[]) {
    let approved = 0, review = 0, blocked = 0;
    for (const i of arr) {
      const s = statusFromRisk(i.riskLevel);
      if (s === 'approved') approved++;
      else if (s === 'review') review++;
      else blocked++;
    }
    return { approved, review, blocked, total: approved + review + blocked };
  }

  $: c = counts(items);
  $: approvedPct = c.total === 0 ? 0 : Math.round((c.approved / c.total) * 100);
  $: flaggedPct = c.total === 0 ? 0 : Math.round(((c.review + c.blocked) / c.total) * 100);
  $: flowing = tps > 0;
</script>

<section class="arch">
  <header class="arch-head">
    <div>
      <h2>Pipeline Topology</h2>
      <span class="hint">Live data flow · <code>transactions.raw → scored → flagged</code></span>
    </div>
    <div class="legend">
      <span class="leg approved"><span class="sw"></span>Approved</span>
      <span class="leg review"><span class="sw"></span>Review</span>
      <span class="leg blocked"><span class="sw"></span>Blocked</span>
    </div>
  </header>

  <div class="stages">
    <div class="stage">
      <div class="icon ingest">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <path d="M3 12h13M11 7l5 5-5 5" stroke-linecap="round" stroke-linejoin="round" />
          <rect x="17" y="4" width="4" height="16" rx="1" />
        </svg>
      </div>
      <div class="title">Ingest</div>
      <div class="tech">Spring REST</div>
      <div class="metric"><span class="num">{tps.toFixed(1)}</span><span class="unit">tx/s</span></div>
    </div>

    <div class="pipe" class:active={flowing}>
      <span class="particle p1 approved"></span>
      <span class="particle p2 review"></span>
      <span class="particle p3 approved"></span>
    </div>

    <div class="stage">
      <div class="icon stream">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <ellipse cx="12" cy="5" rx="8" ry="2.5" />
          <path d="M4 5v6c0 1.4 3.6 2.5 8 2.5s8-1.1 8-2.5V5" />
          <path d="M4 11v6c0 1.4 3.6 2.5 8 2.5s8-1.1 8-2.5v-6" />
        </svg>
      </div>
      <div class="title">Stream</div>
      <div class="tech">Kafka · 3 partitions</div>
      <div class="metric"><span class="num">{c.total}</span><span class="unit">queued</span></div>
    </div>

    <div class="pipe" class:active={flowing}>
      <span class="particle p1 review"></span>
      <span class="particle p2 approved"></span>
      <span class="particle p3 blocked"></span>
    </div>

    <div class="stage emphasis">
      <div class="icon score">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <circle cx="12" cy="12" r="3" />
          <path d="M12 2v3M12 19v3M2 12h3M19 12h3M5 5l2 2M17 17l2 2M5 19l2-2M17 7l2-2" stroke-linecap="round" />
        </svg>
      </div>
      <div class="title">Score</div>
      <div class="tech">Rules + optional ML</div>
      <div class="metric"><span class="num">{c.total}</span><span class="unit">scored</span></div>
    </div>

    <div class="pipe split" class:active={flowing}>
      <span class="particle p1 approved"></span>
      <span class="particle p2 blocked"></span>
    </div>

    <div class="stage">
      <div class="icon branch">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <circle cx="6" cy="6" r="2" />
          <circle cx="6" cy="18" r="2" />
          <circle cx="18" cy="6" r="2" />
          <path d="M6 8v4a4 4 0 004 4h6M6 16V8" stroke-linecap="round" />
        </svg>
      </div>
      <div class="title">Route</div>
      <div class="tech">Risk branch</div>
      <div class="ratios">
        <div class="ratio approved"><span class="bar" style="width: {approvedPct}%"></span><span class="label">{approvedPct}% approved</span></div>
        <div class="ratio flagged"><span class="bar" style="width: {flaggedPct}%"></span><span class="label">{flaggedPct}% flagged</span></div>
      </div>
    </div>

    <div class="pipe" class:active={flowing}>
      <span class="particle p1 blocked"></span>
      <span class="particle p2 approved"></span>
      <span class="particle p3 review"></span>
    </div>

    <div class="stage">
      <div class="icon sink">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
          <ellipse cx="12" cy="5" rx="8" ry="2.5" />
          <path d="M4 5v14c0 1.4 3.6 2.5 8 2.5s8-1.1 8-2.5V5" />
        </svg>
      </div>
      <div class="title">Persist · Alert</div>
      <div class="tech">Postgres · Webhook · SSE</div>
      <div class="metric"><span class="num blocked-num">{alertItems.length}</span><span class="unit">alerts fired</span></div>
    </div>
  </div>
</section>

<style>
  .arch {
    background: var(--bg2);
    border: 1px solid var(--border);
    border-radius: 14px;
    padding: 18px 20px 22px;
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
  }
  .arch-head {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    margin-bottom: 18px;
    gap: 16px;
    flex-wrap: wrap;
  }
  h2 {
    font-size: 14px;
    font-weight: 600;
    margin: 0;
    color: var(--text);
  }
  .hint {
    font-size: 11.5px;
    color: var(--text3);
    margin-top: 2px;
    display: inline-block;
  }
  .hint code {
    font-family: var(--mono);
    font-size: 11px;
    background: var(--bg3);
    padding: 1px 5px;
    border-radius: 3px;
    color: var(--text2);
  }
  .legend { display: flex; gap: 14px; font-size: 11px; color: var(--text2); }
  .leg { display: flex; align-items: center; gap: 5px; }
  .leg .sw { width: 8px; height: 8px; border-radius: 2px; display: inline-block; }
  .leg.approved .sw { background: var(--low); }
  .leg.review .sw { background: var(--med); }
  .leg.blocked .sw { background: var(--high); }

  .stages {
    display: grid;
    grid-template-columns: minmax(140px, 1fr) 60px minmax(140px, 1fr) 60px minmax(160px, 1fr) 60px minmax(170px, 1fr) 60px minmax(170px, 1fr);
    align-items: center;
  }

  .stage {
    background: linear-gradient(180deg, #ffffff 0%, #fafbff 100%);
    border: 1px solid var(--border);
    border-radius: 12px;
    padding: 14px 14px 12px;
    text-align: left;
    position: relative;
    transition: transform 0.2s ease, box-shadow 0.2s ease;
  }
  .stage.emphasis {
    border-color: var(--accent-border);
    background: linear-gradient(180deg, #fbfaff 0%, #f3f1ff 100%);
    box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.05);
  }
  .icon {
    width: 30px;
    height: 30px;
    border-radius: 8px;
    display: grid;
    place-items: center;
    margin-bottom: 8px;
    color: #fff;
    background: linear-gradient(135deg, #4f46e5, #6366f1);
    box-shadow: 0 1px 3px rgba(79, 70, 229, 0.25);
  }
  .icon.stream { background: linear-gradient(135deg, #0ea5e9, #38bdf8); box-shadow: 0 1px 3px rgba(14, 165, 233, 0.25); }
  .icon.score { background: linear-gradient(135deg, #7c3aed, #a78bfa); box-shadow: 0 1px 3px rgba(124, 58, 237, 0.3); }
  .icon.branch { background: linear-gradient(135deg, #f59e0b, #fbbf24); box-shadow: 0 1px 3px rgba(245, 158, 11, 0.25); }
  .icon.sink { background: linear-gradient(135deg, #059669, #10b981); box-shadow: 0 1px 3px rgba(5, 150, 105, 0.25); }
  .icon svg { width: 18px; height: 18px; }
  .title { font-size: 13px; font-weight: 600; color: var(--text); }
  .tech { font-size: 10.5px; color: var(--text3); margin-top: 2px; letter-spacing: 0.01em; }
  .metric { margin-top: 10px; display: flex; align-items: baseline; gap: 4px; }
  .num { font-size: 18px; font-weight: 600; color: var(--text); font-variant-numeric: tabular-nums; }
  .num.blocked-num { color: var(--high); }
  .unit { font-size: 10.5px; color: var(--text3); text-transform: lowercase; }

  .ratios { margin-top: 8px; display: flex; flex-direction: column; gap: 6px; }
  .ratio { position: relative; height: 16px; background: var(--bg3); border-radius: 4px; overflow: hidden; }
  .ratio .bar { position: absolute; inset: 0 auto 0 0; opacity: 0.85; }
  .ratio.approved .bar { background: var(--low); }
  .ratio.flagged .bar { background: var(--high); }
  .ratio .label { position: relative; z-index: 1; padding-left: 6px; line-height: 16px; font-size: 10px; font-weight: 600; color: #fff; mix-blend-mode: difference; }

  .pipe {
    height: 2px;
    background: linear-gradient(90deg, var(--border) 0%, var(--border2) 50%, var(--border) 100%);
    position: relative;
    margin: 0 -1px;
    align-self: center;
  }
  .pipe.split {
    height: 2px;
    background: linear-gradient(90deg, var(--accent-border), var(--border));
  }
  .particle {
    position: absolute;
    top: 50%;
    width: 6px;
    height: 6px;
    border-radius: 50%;
    margin-top: -3px;
    transform: translateX(-100%);
    opacity: 0;
    will-change: transform;
  }
  .particle.approved { background: var(--low); box-shadow: 0 0 6px rgba(5, 150, 105, 0.5); }
  .particle.review { background: var(--med); box-shadow: 0 0 6px rgba(217, 119, 6, 0.5); }
  .particle.blocked { background: var(--high); box-shadow: 0 0 6px rgba(225, 29, 72, 0.5); }

  .pipe.active .p1 { animation: flow 2s linear infinite; }
  .pipe.active .p2 { animation: flow 2s linear infinite; animation-delay: 0.66s; }
  .pipe.active .p3 { animation: flow 2s linear infinite; animation-delay: 1.32s; }

  @keyframes flow {
    0% { left: 0; opacity: 0; }
    10% { opacity: 1; }
    90% { opacity: 1; }
    100% { left: 100%; opacity: 0; }
  }

  @media (max-width: 1180px) {
    .stages {
      grid-template-columns: 1fr;
      gap: 8px;
    }
    .pipe { height: 24px; width: 2px; justify-self: center; background: linear-gradient(180deg, var(--border), var(--border2), var(--border)); }
    .pipe .particle { animation: none !important; }
  }
</style>
