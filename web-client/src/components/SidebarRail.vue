<template>
  <!-- Desktop navigation rail (lg+). Mobile keeps the bottom nav; this aside
       is hidden below lg. Expanded it is a vertical rail (w-60); collapsed it
       becomes a horizontal strip of icons — teleported into the library top
       row when a host is registered (see SidebarNav), otherwise a slim top
       bar in the app shell. Mirrors BottomNav's active-state vocabulary: the
       lit tab is the filled glyph on a translucent primary pill. -->
  <aside
    class="glass z-40 hidden shrink-0 rounded-2xl transition-[width] lg:flex"
    :class="collapsed ? 'm-4 h-14 flex-row items-center self-center px-2' : 'm-4 w-60 flex-col self-start'"
  >
    <!-- Brand. In collapsed mode the rail shrinks to a compact strip; clicking
         anywhere on it (or the chevron) re-expands the full nav. -->
    <div
      class="flex h-14 shrink-0 items-center gap-2.5"
      :class="collapsed ? 'cursor-pointer px-1' : 'px-5'"
      role="button"
      :tabindex="collapsed ? 0 : -1"
      :aria-label="collapsed ? 'Expand sidebar' : undefined"
      @click="onBrandClick"
      @keydown.enter.prevent="onBrandClick"
      @keydown.space.prevent="onBrandClick"
    >
      <img :src="logoUrl" alt="Muorg" class="h-8 w-8 shrink-0" />
      <span v-if="!collapsed" class="text-title-md font-bold tracking-tight text-on-surface">Muorg</span>
      <button
        type="button"
        class="rounded p-0.5 text-on-surface-variant/70 hover:text-on-surface"
        :class="collapsed ? '' : 'ml-auto'"
        :aria-label="collapsed ? 'Expand sidebar' : 'Collapse sidebar'"
        :aria-expanded="!collapsed"
        @click.stop="setCollapsed(!collapsed)"
      >
        <MageIcon :name="collapsed ? 'chevron-down' : 'chevron-up'" class="h-4 w-4" />
      </button>
    </div>

    <!-- NER-326: the active listening profile, right under the brand so it is
         always in view. Hidden entirely while only the default profile
         exists; in collapsed mode it stays as a compact chip. -->
    <div v-if="!collapsed" class="px-5 pb-1 empty:hidden">
      <ProfileBadge />
    </div>
    <div v-else class="flex justify-center px-1 pb-1 empty:hidden">
      <ProfileBadge />
    </div>

    <!-- Tabs. Queue sits with them as a plain destination: it is not a tab
         (tabIndexForRoute returns -1 for it), so it lights on its own route.
         Collapsed: icon-only row, labels hidden. -->
    <nav :class="collapsed ? 'flex flex-1 flex-row items-center gap-1 px-1' : 'flex flex-col gap-1 px-3 py-2'">
      <template v-for="(tab, i) in mainTabs" :key="tab.name">
        <RouterLink
          :to="{ name: tab.name }"
          class="flex items-center gap-3 rounded-lg text-label-lg transition-colors"
          :class="[
            collapsed ? 'h-10 w-10 justify-center px-0' : 'h-11 px-3',
            activeIndex === i ? 'bg-primary/[0.15] text-primary' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface',
          ]"
          :aria-current="activeIndex === i ? 'page' : undefined"
          :title="tab.label"
          @click="onTabClick(tab.name)"
        >
          <MageIcon :name="activeIndex === i ? tab.iconActive : tab.icon" class="h-5 w-5 shrink-0" />
          <span v-if="!collapsed" class="font-medium">{{ tab.label }}</span>
          <button
            v-if="tab.name === 'playlists' && !collapsed"
            type="button"
            class="ml-auto rounded p-0.5 text-on-surface-variant/70 hover:text-on-surface"
            :aria-label="playlistsExpanded ? 'Collapse playlists' : 'Expand playlists'"
            :aria-expanded="playlistsExpanded"
            @click.stop.prevent="playlistsExpanded = !playlistsExpanded"
          >
            <MageIcon :name="playlistsExpanded ? 'chevron-up' : 'chevron-down'" class="h-4 w-4" />
          </button>
        </RouterLink>

        <!-- Playlists list: pinned first, each a RouterLink to its detail
             page and a track-drag drop target (desktop sidebar parity).
             Hidden in the collapsed strip. -->
        <div
          v-if="tab.name === 'playlists' && playlistsExpanded && !collapsed"
          class="ml-2 flex flex-col gap-0.5 border-l border-on-surface/10 py-1 pl-3"
        >
          <RouterLink
            v-for="(p, idx) in playlistStore.sortedPlaylists"
            :key="p.id"
            :to="{ name: 'playlist', params: { id: String(p.id) } }"
            class="group/pl relative flex h-9 items-center gap-1.5 truncate rounded-lg px-2 text-label-md transition-colors"
            :class="[
              route.name === 'playlist' && route.params.id === String(p.id)
                ? 'bg-primary/[0.15] text-primary'
                : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface',
              dragOverPlaylistId === p.id ? 'bg-primary/[0.12] ring-1 ring-primary/40' : '',
              idx === playlistDrag.draggingIndex.value ? 'z-10 border border-primary' : '',
            ]"
            :style="playlistRowStyle(idx)"
            @dragover="onPlaylistDragover($event, p.id)"
            @dragleave="onPlaylistDragleave(p.id)"
            @drop="onPlaylistDrop($event, p.id)"
          >
            <!-- Reorder handle: long-press (touch) or press-and-drag (mouse)
                 to reorder; the unpinned tail moves, pins stay pinned-first
                 (desktop sidebar parity). -->
            <span
              class="shrink-0 cursor-grab text-on-surface-variant/50 opacity-0 group-hover/pl:opacity-100"
              style="touch-action: none"
              @pointerdown.stop="playlistDrag.start(idx, $event)"
              @click.stop.prevent
            >
              <MageIcon name="dash-menu" class="h-3.5 w-3.5" />
            </span>
            <PlaylistIcon :icon="p.icon" class="h-4 w-4 shrink-0" />
            <span class="min-w-0 flex-1 truncate">{{ p.name }}</span>
            <MageIcon
              v-if="playlistStore.isPinned(p.id)"
              name="pin"
              class="h-3 w-3 shrink-0 text-primary"
            />
          </RouterLink>
          <p
            v-if="playlistStore.sortedPlaylists.length === 0"
            class="px-2 py-1 text-label-sm text-on-surface-variant/60"
          >No playlists yet</p>
        </div>
      </template>

      <RouterLink
        :to="{ name: 'queue' }"
        class="flex items-center gap-3 rounded-lg text-label-lg transition-colors"
        :class="[
          collapsed ? 'h-10 w-10 justify-center px-0' : 'h-11 px-3',
          isQueue ? 'bg-primary/[0.15] text-primary' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface',
        ]"
        :aria-current="isQueue ? 'page' : undefined"
        title="Queue"
        @click="onQueueClick"
      >
        <MageIcon name="stack" class="h-5 w-5 shrink-0" />
        <span v-if="!collapsed" class="font-medium">Queue</span>
      </RouterLink>

      <RouterLink
        :to="{ name: 'reports' }"
        class="flex items-center gap-3 rounded-lg text-label-lg transition-colors"
        :class="[
          collapsed ? 'relative h-10 w-10 justify-center px-0' : 'h-11 px-3',
          isReports ? 'bg-primary/[0.15] text-primary' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface',
        ]"
        :aria-current="isReports ? 'page' : undefined"
        title="Reports"
      >
        <MageIcon name="note-text" class="h-5 w-5 shrink-0" />
        <span v-if="!collapsed" class="font-medium">Reports</span>
        <span
          v-if="reportIssues > 0"
          class="rounded-full bg-primary/[0.15] px-1.5 py-0.5 text-label-sm tabular-nums text-primary"
          :class="collapsed ? 'absolute -right-1 -top-1 px-1 py-px text-[10px]' : 'ml-auto'"
        >{{ reportIssues }}</span>
      </RouterLink>

      <RouterLink
        :to="{ name: 'stats' }"
        class="flex items-center gap-3 rounded-lg text-label-lg transition-colors"
        :class="[
          collapsed ? 'h-10 w-10 justify-center px-0' : 'h-11 px-3',
          isStats ? 'bg-primary/[0.15] text-primary' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface',
        ]"
        :aria-current="isStats ? 'page' : undefined"
        title="Statistics"
      >
        <MageIcon name="chart-up" class="h-5 w-5 shrink-0" />
        <span v-if="!collapsed" class="font-medium">Statistics</span>
      </RouterLink>

      <!-- Settings is pinned as the bottom-most nav item, below the rest. -->
      <RouterLink
        :to="{ name: 'settings' }"
        class="flex items-center gap-3 rounded-lg text-label-lg transition-colors"
        :class="[
          collapsed ? 'h-10 w-10 justify-center px-0' : 'h-11 px-3',
          isSettings ? 'bg-primary/[0.15] text-primary' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface',
        ]"
        :aria-current="isSettings ? 'page' : undefined"
        title="Settings"
        @click="onTabClick('settings')"
      >
        <MageIcon :name="isSettings ? 'settings-fill' : 'settings'" class="h-5 w-5 shrink-0" />
        <span v-if="!collapsed" class="font-medium">Settings</span>
      </RouterLink>
    </nav>

    <!-- Trailing slot at the right end of the collapsed strip: views teleport
         their own actions here (see collapsedNavTrailing) — e.g. HomeView's
         shuffle-all / play-random-mix buttons. -->
    <div v-if="collapsed" ref="trailingEl" class="flex shrink-0 items-center gap-1 empty:hidden" />

    <template v-if="!collapsed">
      <div class="flex-1" />

      <!-- Volume lives in the rail (desktop); the player has no volume bar. -->
      <div class="mx-3 border-t border-on-surface/10 px-2 py-3">
        <VolumeControl />
        <!-- FS-mode backdrop blur, underneath the volume bar. The slider shows
             the effective value (auto per cover, or the override); "Auto"
             clears a saved override and returns to per-cover auto. -->
        <div v-if="player.artFullscreen" class="mt-3">
          <div class="flex items-center gap-2 text-label-sm text-on-surface-variant">
            <MageIcon name="image" class="h-4 w-4 shrink-0" />
            <span>Backdrop blur</span>
            <button
              v-if="settings.fsBackdropBlur !== null"
              type="button"
              class="rounded px-1.5 py-0.5 text-label-sm text-primary hover:bg-surface-container"
              @click="settings.setFsBackdropBlur(null)"
            >Auto</button>
            <span class="ml-auto tabular-nums">{{ fsBlurDisplay }}px</span>
          </div>
          <input
            type="range"
            min="0"
            max="24"
            step="1"
            :value="fsBlurDisplay"
            class="h-1 w-full cursor-pointer"
            aria-label="Backdrop blur"
            @input="onFsBlurInput"
          />
        </div>
      </div>
    </template>
  </aside>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch, watchEffect } from "vue";
import { useRoute, useRouter } from "vue-router";
import MageIcon from "./MageIcon.vue";
import PlaylistIcon from "@shared/components/PlaylistIcon.vue";
import ProfileBadge from "./ProfileBadge.vue";
import VolumeControl from "./VolumeControl.vue";
import { sidebarCollapsed as collapsed, collapsedNavTrailing } from "../composables/sidebarCollapsed";
import { useFsBackdropBlur } from "../composables/useFsBackdropBlur";
import { scrollToActiveSignal } from "../composables/useScrollSignal";
import { searchFocusSignal } from "../composables/useSearchFocus";
import { isTrackDrag, readTrackDrag } from "../composables/useTrackDrag";
import { useDragReorder } from "../composables/useDragReorder";
import { NAV_TABS as TABS, tabIndexForRoute } from "../nav-tabs";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { usePlaylistStore } from "../stores/playlists";
import { loadPref, savePref } from "../stores/prefs";
import { useSettingsStore } from "../stores/settings";
import { reportCounts } from "@shared/reports";
import logoUrl from "../assets/muorg-logo.svg";

const route = useRoute();
const router = useRouter();
const lib = useLibraryStore();
const player = usePlayerStore();
const playlistStore = usePlaylistStore();
const settings = useSettingsStore();

// Collapsed rail: the maximized player (player route) and art-fullscreen mode
// collapse the sidebar automatically so the backdrop gets the room; the
// brand-row chevron toggles it manually. The manual choice is persisted
// (muorg-web-sidebar-collapsed) so it survives page navigations and reloads;
// leaving an auto-collapse mode restores that saved choice instead of always
// expanding.
const COLLAPSED_PREF = "muorg-web-sidebar-collapsed";

collapsed.value = loadPref(COLLAPSED_PREF, false);

// Publish the collapsed strip's trailing slot so views can teleport actions
// into the right end of the top bar.
const trailingEl = ref<HTMLElement | null>(null);
watchEffect(() => {
  collapsedNavTrailing.value = collapsed.value ? trailingEl.value : null;
});

watch([() => route.name, () => player.artFullscreen], ([name, fullscreen]) => {
  collapsed.value = name === "player" || fullscreen ? true : loadPref(COLLAPSED_PREF, false);
});

/** Persist a manual collapse/expand so the choice sticks across pages. */
function setCollapsed(v: boolean): void {
  collapsed.value = v;
  savePref(COLLAPSED_PREF, v);
}

/** Clicking the collapsed brand row expands the rail; when expanded the row
 *  is inert and only the chevron toggles. */
function onBrandClick(): void {
  if (collapsed.value) setCollapsed(false);
}

/** Album and playlist detail keep their parent tab lit; -1 when off-tab. */
const activeIndex = computed(() => tabIndexForRoute(String(route.name)));

/** Settings renders separately at the bottom of the rail, so it is excluded
 *  from the main tab loop and gets its own active state. */
const mainTabs = computed(() => TABS.filter((t) => t.name !== "settings"));
const isSettings = computed(() => route.name === "settings");

const isQueue = computed(() => route.name === "queue" || route.name === "player-queue");

const isReports = computed(() => route.name === "reports" || route.name === "report");

const isStats = computed(() => route.name === "stats");

/**
 * The badge counts the two reports that mean something is wrong with the
 * files. Recently/most played are not problems, and including them would sit a
 * permanent number next to the link.
 */
const reportIssues = computed(() => {
  const counts = reportCounts(lib.tracks, settings.missingMetadataFields);
  return counts.missing_metadata + counts.duplicates + counts.missing_album_cover;
});

const { blurPx: fsBlurPx } = useFsBackdropBlur(computed(() => player.currentCoverUrl));
/** Effective blur for the slider: override if set, else the per-cover auto. */
const fsBlurDisplay = computed(() => fsBlurPx.value ?? 12);

function onFsBlurInput(e: Event): void {
  const raw = (e.target as HTMLInputElement).valueAsNumber;
  if (!Number.isFinite(raw)) return;
  settings.setFsBackdropBlur(Math.min(24, Math.max(0, Math.round(raw))));
}

async function onTabClick(name: string): Promise<void> {
  if (name === "library" && route.name === "library") scrollToActiveSignal.value++;
  // Clicking Search always lands in the input — arriving at the tab or
  // re-tapping it. Same pattern as the "/" shortcut in useKeyboardShortcuts.
  if (name === "search") {
    if (route.name !== "search") await router.push({ name: "search" });
    await nextTick();
    searchFocusSignal.value++;
  }
}

/** Queue doubles as a toggle: already on a queue screen → back to the last one. */
function onQueueClick(e: MouseEvent): void {
  if (!isQueue.value) return;
  e.preventDefault();
  if (window.history.state?.back) router.back();
  else void router.push({ name: "home" });
}

// ── Playlists list: expand state + track-drag drop targets ───────────────
const playlistsExpanded = ref(loadPref("muorg-web-sidebar-playlists-expanded", true));
watch(playlistsExpanded, (v) => savePref("muorg-web-sidebar-playlists-expanded", v));

onMounted(() => {
  void playlistStore.loadPlaylists();
});

const dragOverPlaylistId = ref<number | null>(null);

function onPlaylistDragover(e: DragEvent, playlistId: number): void {
  if (!isTrackDrag(e)) return;
  e.preventDefault();
  if (e.dataTransfer) e.dataTransfer.dropEffect = "copy";
  dragOverPlaylistId.value = playlistId;
}

function onPlaylistDragleave(playlistId: number): void {
  if (dragOverPlaylistId.value === playlistId) dragOverPlaylistId.value = null;
}

async function onPlaylistDrop(e: DragEvent, playlistId: number): Promise<void> {
  dragOverPlaylistId.value = null;
  const ids = readTrackDrag(e);
  if (!ids?.length) return;
  e.preventDefault();
  await playlistStore.addTracksChecked(playlistId, ids);
}

// Pointer-driven reorder — mirrors the desktop sidebar's drag reorder.
// Pinned playlists (the sortedPlaylists prefix) can be reordered among
// themselves (that rewrites the pin order); the unpinned tail reorders via
// the server's playlist order. Drags across the pin boundary are no-ops —
// pins always stay pinned-first.
const SIDEBAR_PLAYLIST_ROW_HEIGHT = 36; // h-9

const playlistDrag = useDragReorder({
  itemCount: () => playlistStore.sortedPlaylists.length,
  rowHeight: SIDEBAR_PLAYLIST_ROW_HEIGHT,
  immediate: false,
  onCommit: (from, to) => {
    if (from === to) return;
    const list = [...playlistStore.sortedPlaylists];
    const pinnedCount = list.reduce((n, p) => n + (playlistStore.isPinned(p.id) ? 1 : 0), 0);
    if (from < pinnedCount && to < pinnedCount) {
      const pinned = list.slice(0, pinnedCount);
      const [moved] = pinned.splice(from, 1);
      pinned.splice(to, 0, moved);
      void playlistStore.reorderPinnedPlaylists(pinned.map((p) => p.id));
      return;
    }
    if (from < pinnedCount || to < pinnedCount) return;
    const [moved] = list.splice(from, 1);
    list.splice(to, 0, moved);
    void playlistStore.reorderPlaylists(list.map((p) => p.id));
  },
});

function playlistRowStyle(i: number): Record<string, string> {
  if (i === playlistDrag.draggingIndex.value) {
    return { transform: `translateY(${playlistDrag.offsetY.value}px)` };
  }
  const from = playlistDrag.draggingIndex.value;
  const to = playlistDrag.dropIndex.value;
  if (from === null || to === null || from === to) return {};
  if (from < to && i > from && i <= to) return { transform: `translateY(-${SIDEBAR_PLAYLIST_ROW_HEIGHT}px)` };
  if (from > to && i < from && i >= to) return { transform: `translateY(${SIDEBAR_PLAYLIST_ROW_HEIGHT}px)` };
  return {};
}
</script>
