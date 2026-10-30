<template>
  <!-- Desktop navigation rail (lg+). Mobile keeps the bottom nav; this aside is
       hidden below lg. Mirrors BottomNav's active-state vocabulary: the lit tab
       is the filled glyph on a translucent primary pill. -->
  <aside
    class="glass hidden m-4 w-60 shrink-0 flex-col self-start rounded-2xl lg:flex"
  >
    <!-- Brand -->
    <div class="flex h-14 shrink-0 items-center gap-2.5 px-5">
      <img :src="logoUrl" alt="Muorg" class="h-8 w-8 shrink-0" />
      <span class="text-title-md font-bold tracking-tight text-on-surface">Muorg</span>
    </div>

    <!-- Tabs. Queue sits with them as a plain destination: it is not a tab
         (tabIndexForRoute returns -1 for it), so it lights on its own route. -->
    <nav class="flex flex-col gap-1 px-3 py-2">
      <template v-for="(tab, i) in TABS" :key="tab.name">
        <RouterLink
          :to="{ name: tab.name }"
          class="flex h-11 items-center gap-3 rounded-lg px-3 text-label-lg transition-colors"
          :class="activeIndex === i ? 'bg-primary/[0.15] text-primary' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'"
          :aria-current="activeIndex === i ? 'page' : undefined"
          @click="onTabClick(tab.name)"
        >
          <MageIcon :name="activeIndex === i ? tab.iconActive : tab.icon" class="h-5 w-5 shrink-0" />
          <span class="font-medium">{{ tab.label }}</span>
          <button
            v-if="tab.name === 'playlists'"
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
             page and a track-drag drop target (desktop sidebar parity). -->
        <div
          v-if="tab.name === 'playlists' && playlistsExpanded"
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
            <span class="shrink-0 text-sm leading-none">{{ p.icon ?? "🎵" }}</span>
            <span class="min-w-0 flex-1 truncate">{{ p.name }}</span>
          </RouterLink>
          <p
            v-if="playlistStore.sortedPlaylists.length === 0"
            class="px-2 py-1 text-label-sm text-on-surface-variant/60"
          >No playlists yet</p>
        </div>
      </template>

      <RouterLink
        :to="{ name: 'queue' }"
        class="flex h-11 items-center gap-3 rounded-lg px-3 text-label-lg transition-colors"
        :class="isQueue ? 'bg-primary/[0.15] text-primary' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'"
        :aria-current="isQueue ? 'page' : undefined"
        @click="onQueueClick"
      >
        <MageIcon name="stack" class="h-5 w-5 shrink-0" />
        <span class="font-medium">Queue</span>
      </RouterLink>

      <RouterLink
        :to="{ name: 'reports' }"
        class="flex h-11 items-center gap-3 rounded-lg px-3 text-label-lg transition-colors"
        :class="isReports ? 'bg-primary/[0.15] text-primary' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'"
        :aria-current="isReports ? 'page' : undefined"
      >
        <MageIcon name="note-text" class="h-5 w-5 shrink-0" />
        <span class="font-medium">Reports</span>
        <span
          v-if="reportIssues > 0"
          class="ml-auto rounded-full bg-primary/[0.15] px-1.5 py-0.5 text-label-sm tabular-nums text-primary"
        >{{ reportIssues }}</span>
      </RouterLink>

      <RouterLink
        :to="{ name: 'stats' }"
        class="flex h-11 items-center gap-3 rounded-lg px-3 text-label-lg transition-colors"
        :class="isStats ? 'bg-primary/[0.15] text-primary' : 'text-on-surface-variant hover:bg-surface-container hover:text-on-surface'"
        :aria-current="isStats ? 'page' : undefined"
      >
        <MageIcon name="chart-up" class="h-5 w-5 shrink-0" />
        <span class="font-medium">Statistics</span>
      </RouterLink>
    </nav>

    <div class="flex-1" />

    <p class="px-5 pb-4 text-label-sm text-on-surface-variant/70">Muorg Web</p>
  </aside>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import MageIcon from "./MageIcon.vue";
import { scrollToActiveSignal } from "../composables/useScrollSignal";
import { searchFocusSignal } from "../composables/useSearchFocus";
import { isTrackDrag, readTrackDrag } from "../composables/useTrackDrag";
import { useDragReorder } from "../composables/useDragReorder";
import { NAV_TABS as TABS, tabIndexForRoute } from "../nav-tabs";
import { useLibraryStore } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import { loadPref, savePref } from "../stores/prefs";
import { useSettingsStore } from "../stores/settings";
import { reportCounts } from "@shared/reports";
import logoUrl from "../assets/muorg-logo.svg";

const route = useRoute();
const router = useRouter();
const lib = useLibraryStore();
const playlistStore = usePlaylistStore();
const settings = useSettingsStore();

/** Album and playlist detail keep their parent tab lit; -1 when off-tab. */
const activeIndex = computed(() => tabIndexForRoute(String(route.name)));

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

function onTabClick(name: string): void {
  if (name === "library" && route.name === "library") scrollToActiveSignal.value++;
  // Re-tapping Search jumps straight back into the input.
  if (name === "search" && route.name === "search") searchFocusSignal.value++;
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

// Pointer-driven reorder of the unpinned tail (pins stay pinned-first via
// sortedPlaylists regardless) — mirrors the desktop sidebar's drag reorder.
const SIDEBAR_PLAYLIST_ROW_HEIGHT = 36; // h-9

const playlistDrag = useDragReorder({
  itemCount: () => playlistStore.sortedPlaylists.length,
  rowHeight: SIDEBAR_PLAYLIST_ROW_HEIGHT,
  immediate: false,
  onCommit: (from, to) => {
    if (from === to) return;
    const list = [...playlistStore.sortedPlaylists];
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
