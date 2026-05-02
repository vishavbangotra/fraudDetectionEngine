const API_BASE = import.meta.env.VITE_API_BASE ?? '';

function url(path: string): string {
  return `${API_BASE}${path}`;
}

export async function simulate(count: number): Promise<string> {
  const res = await fetch(url(`/api/transactions/simulate?count=${count}`), { method: 'POST' });
  if (!res.ok) throw new Error(`simulate failed: ${res.status}`);
  return res.text();
}

export async function reset(): Promise<string> {
  const res = await fetch(url('/api/transactions/reset'), { method: 'DELETE' });
  if (!res.ok) throw new Error(`reset failed: ${res.status}`);
  return res.text();
}

export function streamUrl(path: string): string {
  return url(path);
}
