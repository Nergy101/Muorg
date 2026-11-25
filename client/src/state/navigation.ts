import { computed, ref } from "vue";

/**
 * In-app navigation for the desktop shell. No URL routing (per the design
 * handover): a history array + index drives the back/forward pills in the
 * top bar, and the current view selects what the main pane renders.
 */

export type ViewName =
  | "home"
  | "albums"
  | "list"
  | "artists"
  | "playlists"
  | "queue"
  | "reports"
  | "stats"
  | "settings"
  | "search"
  | "album"
  | "artist"
  | "playlist"
  | "mix";

export interface View {
  name: ViewName;
  /** Album identity key (album detail), artist name (artist detail), playlist id. */
  key?: string;
  id?: number;
}

function viewEquals(a: View, b: View): boolean {
  return a.name === b.name && a.key === b.key && a.id === b.id;
}

const history = ref<View[]>([{ name: "albums" }]);
const index = ref(0);

export const currentView = computed(() => history.value[index.value]);
export const canBack = computed(() => index.value > 0);
export const canForward = computed(() => index.value < history.value.length - 1);

/** Navigate to a view, truncating any forward history. Same view = no-op. */
export function navigate(view: View): void {
  const cur = history.value[index.value];
  if (cur && viewEquals(cur, view)) return;
  history.value = history.value.slice(0, index.value + 1);
  history.value.push(view);
  index.value = history.value.length - 1;
}

export function back(): void {
  if (index.value > 0) index.value -= 1;
}

export function forward(): void {
  if (index.value < history.value.length - 1) index.value += 1;
}

/** Replace the current entry without growing the stack (e.g. search-as-you-type). */
export function replace(view: View): void {
  history.value[index.value] = view;
}

/** Set from anywhere (e.g. album page 3-dots menu) to open the metadata editor overlay. */
export const metadataEditorOpen = ref(false);

/** Sidebar collapsed to a vertical icon rail. */
export const sidebarCollapsed = ref(false);

/** A Home "Mix" card opened as a playlist-style page. */
export interface ActiveMix {
  name: string;
  trackIds: number[];
  coverPaths: string[];
  artists: string;
}
export const activeMix = ref<ActiveMix | null>(null);
