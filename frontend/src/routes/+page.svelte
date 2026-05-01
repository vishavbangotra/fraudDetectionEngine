<script lang="ts">
  import { onMount, onDestroy } from 'svelte';
  import { connectLiveFeed, type LiveFeed } from '$lib/streams';
  import KpiCards from '$lib/components/KpiCards.svelte';
  import TransactionFeed from '$lib/components/TransactionFeed.svelte';
  import AlertsPanel from '$lib/components/AlertsPanel.svelte';
  import ArchitectureDiagram from '$lib/components/ArchitectureDiagram.svelte';
  import ControlBar from '$lib/components/ControlBar.svelte';

  let feed: LiveFeed | null = null;

  onMount(() => {
    feed = connectLiveFeed();
  });
  onDestroy(() => feed?.close());
</script>

{#if feed}
  <ControlBar txConnection={feed.txConnection} alertConnection={feed.alertConnection} />
  <main class="page">
    <section class="row kpis">
      <KpiCards transactions={feed.transactions} />
    </section>

    <section class="row arch-row">
      <ArchitectureDiagram transactions={feed.transactions} alerts={feed.alerts} />
    </section>

    <section class="row split">
      <TransactionFeed transactions={feed.transactions} />
      <AlertsPanel alerts={feed.alerts} />
    </section>
  </main>
{:else}
  <div class="loading">
    <div class="loading-spinner"></div>
    <span>Connecting to live feed…</span>
  </div>
{/if}

<style>
  :global(:root) {
    --bg: #f5f7fb;
    --bg2: #ffffff;
    --bg3: #eef1f8;
    --border: #e1e5ee;
    --border2: #edf0f6;
    --text: #0f172a;
    --text2: #475569;
    --text3: #94a3b8;
    --low: #059669;
    --low-bg: #ecfdf5;
    --med: #d97706;
    --med-bg: #fffbeb;
    --high: #e11d48;
    --high-bg: #fff1f2;
    --accent: #4f46e5;
    --accent-light: #eef2ff;
    --accent-border: #c7d2fe;
    --mono: 'JetBrains Mono', ui-monospace, monospace;
  }
  :global(html), :global(body) {
    height: 100%;
    margin: 0;
    background: var(--bg);
    color: var(--text);
    font-family: 'Inter', system-ui, -apple-system, sans-serif;
    font-size: 13px;
    -webkit-font-smoothing: antialiased;
    -moz-osx-font-smoothing: grayscale;
  }
  :global(*) { box-sizing: border-box; }
  :global(::-webkit-scrollbar) { width: 8px; height: 8px; }
  :global(::-webkit-scrollbar-track) { background: transparent; }
  :global(::-webkit-scrollbar-thumb) { background: var(--border); border-radius: 4px; }
  :global(::-webkit-scrollbar-thumb:hover) { background: var(--text3); }

  .page {
    display: flex;
    flex-direction: column;
    gap: 16px;
    padding: 18px;
    height: calc(100vh - 56px);
    overflow-y: auto;
  }
  .row { flex-shrink: 0; }
  .row.split {
    display: grid;
    grid-template-columns: 1.4fr 1fr;
    gap: 16px;
    flex: 1;
    min-height: 360px;
  }

  @media (max-width: 1100px) {
    .row.split { grid-template-columns: 1fr; }
  }

  .loading {
    height: 100vh;
    display: flex;
    flex-direction: column;
    gap: 14px;
    align-items: center;
    justify-content: center;
    color: var(--text3);
    font-size: 13px;
  }
  .loading-spinner {
    width: 28px;
    height: 28px;
    border: 2px solid var(--border);
    border-top-color: var(--accent);
    border-radius: 50%;
    animation: spin 0.8s linear infinite;
  }
  @keyframes spin {
    to { transform: rotate(360deg); }
  }
</style>
