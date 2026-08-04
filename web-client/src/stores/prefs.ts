/** localStorage-backed preference helpers shared by every store. */

export function loadPref<T>(key: string, fallback: T): T {
  try {
    const v = localStorage.getItem(key);
    return v != null ? (JSON.parse(v) as T) : fallback;
  } catch {
    return fallback;
  }
}

export function savePref(key: string, value: unknown): void {
  localStorage.setItem(key, JSON.stringify(value));
}
