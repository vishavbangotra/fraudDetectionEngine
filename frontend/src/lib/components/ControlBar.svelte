<script lang="ts">
  import type { Readable } from 'svelte/store';
  import type { ConnectionState } from '$lib/streams';
  import { simulate, reset } from '$lib/api';

  export let txConnection: Readable<ConnectionState>;
  export let alertConnection: Readable<ConnectionState>;

  let count = 50;
  let busy = false;
  let lastMessage = '';
  let messageTimer: ReturnType<typeof setTimeout> | null = null;

  $: connected = $txConnection.connected && $alertConnection.connected;

  function flashMessage(msg: string) {
    lastMessage = msg;
    if (messageTimer) clearTimeout(messageTimer);
    messageTimer = setTimeout(() => (lastMessage = ''), 4000);
  }

  async function handleSimulate() {
    busy = true;
    try {
      flashMessage(await simulate(count));
    } catch (e) {
      flashMessage((e as Error).message);
    } finally {
      busy = false;
    }
  }

  async function handleReset() {
    busy = true;
    try {
      flashMessage(await reset());
    } catch (e) {
      flashMessage((e as Error).message);
    } finally {
      busy = false;
    }
  }
</script>

<div class="bar">
  <div class="brand">
    <span class="logo">
      <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M12 3l8 3v6c0 5-3.5 8-8 9-4.5-1-8-4-8-9V6l8-3z" stroke-linejoin="round"/>
        <path d="M8 12l3 3 5-6" stroke-linecap="round" stroke-linejoin="round"/>
      </svg>
    </span>
    <div class="brand-text">
      <span class="title">Fraud Detection</span>
      <span class="subtitle">Live Pipeline · Kafka Streams</span>
    </div>
  </div>

  <div class="connections">
    <div class="conn" class:on={$txConnection.connected}>
      <span class="dot"></span>
      <span class="conn-label">scored</span>
    </div>
    <div class="conn" class:on={$alertConnection.connected}>
      <span class="dot"></span>
      <span class="conn-label">flagged</span>
    </div>
  </div>

  <div class="spacer"></div>

  {#if lastMessage}
    <span class="msg" class:err={lastMessage.toLowerCase().includes('fail')}>{lastMessage}</span>
  {/if}

  <label class="count-input">
    <span>Count</span>
    <input type="number" min="1" max="1000" bind:value={count} disabled={busy} />
  </label>
  <button class="primary" on:click={handleSimulate} disabled={busy}>
    {#if busy}…{:else}Simulate{/if}
  </button>
  <button class="reset" on:click={handleReset} disabled={busy}>Reset</button>
</div>

<style>
  .bar {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 0 18px;
    height: 56px;
    background: var(--bg2);
    border-bottom: 1px solid var(--border);
    box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
  }
  .brand { display: flex; align-items: center; gap: 10px; }
  .logo {
    width: 32px; height: 32px;
    border-radius: 9px;
    background: linear-gradient(135deg, #4338ca, #6366f1);
    color: #fff;
    display: grid;
    place-items: center;
    box-shadow: 0 2px 6px rgba(79, 70, 229, 0.3);
  }
  .logo svg { width: 18px; height: 18px; }
  .brand-text { display: flex; flex-direction: column; line-height: 1.1; }
  .title { font-weight: 700; font-size: 14px; color: var(--text); letter-spacing: -0.01em; }
  .subtitle { font-size: 11px; color: var(--text3); margin-top: 2px; }

  .connections {
    display: flex;
    gap: 10px;
    margin-left: 18px;
    padding-left: 18px;
    border-left: 1px solid var(--border);
  }
  .conn {
    display: flex;
    align-items: center;
    gap: 6px;
    font-size: 11px;
    color: var(--text3);
    padding: 4px 9px;
    background: var(--bg3);
    border-radius: 6px;
    font-family: var(--mono);
  }
  .conn .dot { width: 7px; height: 7px; border-radius: 50%; background: var(--high); }
  .conn.on { color: var(--low); background: var(--low-bg); }
  .conn.on .dot {
    background: var(--low);
    box-shadow: 0 0 0 3px rgba(5, 150, 105, 0.18);
    animation: ping 2.4s ease-in-out infinite;
  }
  @keyframes ping {
    0%, 100% { box-shadow: 0 0 0 3px rgba(5, 150, 105, 0.18); }
    50% { box-shadow: 0 0 0 5px rgba(5, 150, 105, 0.08); }
  }

  .spacer { flex: 1; }
  .msg {
    font-size: 11px;
    color: var(--text2);
    font-family: var(--mono);
    background: var(--bg3);
    padding: 5px 10px;
    border-radius: 6px;
    border: 1px solid var(--border);
    animation: slideIn 0.3s ease;
  }
  .msg.err { color: var(--high); background: var(--high-bg); border-color: rgba(225, 29, 72, 0.2); }
  @keyframes slideIn {
    from { opacity: 0; transform: translateY(-2px); }
    to { opacity: 1; transform: translateY(0); }
  }

  .count-input {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 11px;
    color: var(--text2);
    text-transform: uppercase;
    letter-spacing: 0.05em;
    font-weight: 500;
  }
  .count-input input {
    width: 72px;
    padding: 6px 10px;
    border: 1px solid var(--border);
    border-radius: 6px;
    font-size: 12px;
    background: var(--bg2);
    color: var(--text);
    font-variant-numeric: tabular-nums;
    transition: border-color 0.15s ease;
  }
  .count-input input:focus { outline: none; border-color: var(--accent); }
  button {
    padding: 7px 16px;
    border-radius: 7px;
    font-size: 12px;
    font-weight: 600;
    cursor: pointer;
    transition: all 0.15s ease;
    border: 1px solid transparent;
  }
  button.primary {
    border-color: var(--accent);
    background: var(--accent);
    color: #fff;
    box-shadow: 0 1px 3px rgba(79, 70, 229, 0.25);
  }
  button.primary:hover:not(:disabled) {
    background: #4338ca;
    box-shadow: 0 2px 6px rgba(79, 70, 229, 0.35);
  }
  button.reset {
    background: transparent;
    color: var(--high);
    border-color: rgba(225, 29, 72, 0.25);
  }
  button.reset:hover:not(:disabled) {
    background: var(--high-bg);
    border-color: var(--high);
  }
  button:disabled { opacity: 0.55; cursor: not-allowed; box-shadow: none; }
</style>
