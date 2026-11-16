import type { icons as IconsMap } from "@lucide/vue";

export type LucideIcons = typeof IconsMap;

let promise: Promise<LucideIcons> | null = null;

/**
 * The full lucide icon map (~2k components), loaded as its own async chunk so
 * the app shell does not pay for it. Callers get the same map instance.
 */
export function loadLucideIcons(): Promise<LucideIcons> {
  return (promise ??= import("@lucide/vue").then((m) => m.icons));
}
