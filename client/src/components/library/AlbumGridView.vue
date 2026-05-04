<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import AlbumGridCard from "./AlbumGridCard.vue";
import { useOverlayScrollbars } from "../../composables/useOverlayScrollbars";

export type AlbumGridItem = {
  key: string;
  album: string;
  albumArtist: string;
  year: number | null;
  trackCount: number;
  totalDurationSecs: number;
  coverPath: string;
  hasCover: boolean;
  trackIds: number[];
};

const props = defineProps<{
  albums: AlbumGridItem[];
}>();

const emit = defineEmits<{
  (e: "openAlbum", albumKey: string): void;
  (e: "albumContextMenu", event: MouseEvent, albumKey: string): void;
}>();

const store = useCatalogStore();
const { tracks, currentPlayingTrackId } = storeToRefs(store);

const playingAlbumKey = computed(() => {
  const id = currentPlayingTrackId.value;
  if (id == null) return null;
  const track = tracks.value.find((t) => t.id === id);
  if (!track) return null;
  const album = (track.album ?? "Unknown Album").trim() || "Unknown Album";
  return album.toLocaleLowerCase();
});

// ── Virtual scroll constants ──────────────────────────────────────────────────
const CARD_MIN_WIDTH = 190; // matches minmax(190px, 1fr)
const CARD_HEIGHT    = 220; // min-h-[220px] on AlbumGridCard
const GAP            = 12;  // gap-3
const PAD            = 12;  // p-3
const OVERSCAN       = 4;   // extra rows rendered above/below viewport

// ── Container measurement ─────────────────────────────────────────────────────
const containerRef     = ref<HTMLElement | null>(null);
const containerWidth   = ref(0);
const containerHeight  = ref(0);
const scrollTopVal     = ref(0);

const { viewportRef } = useOverlayScrollbars(containerRef);

function getScrollEl(): HTMLElement | null {
  return viewportRef.value ?? containerRef.value;
}

function measure() {
  const el = getScrollEl();
  if (!el) return;
  containerWidth.value  = el.clientWidth;
  containerHeight.value = el.clientHeight;
}

function onScroll() {
  scrollTopVal.value = getScrollEl()?.scrollTop ?? 0;
}

let ro: ResizeObserver | null = null;
let scrollElForCleanup: HTMLElement | null = null;

watch(viewportRef, (el) => {
  if (scrollElForCleanup) {
    scrollElForCleanup.removeEventListener("scroll", onScroll);
    scrollElForCleanup = null;
  }
  ro?.disconnect();
  ro = null;
  if (el) {
    el.addEventListener("scroll", onScroll, { passive: true });
    scrollElForCleanup = el;
    measure();
    ro = new ResizeObserver(measure);
    ro.observe(el);
  }
}, { flush: "post" });

onUnmounted(() => {
  scrollElForCleanup?.removeEventListener("scroll", onScroll);
  ro?.disconnect();
});

// ── Derived grid geometry ─────────────────────────────────────────────────────
const colCount = computed(() => {
  const w = containerWidth.value - PAD * 2;
  if (w <= 0) return 1;
  // mirrors CSS auto-fill: minmax(190px, 1fr)
  return Math.max(1, Math.floor((w + GAP) / (CARD_MIN_WIDTH + GAP)));
});

const rowHeight = computed(() => CARD_HEIGHT + GAP); // height of one row slot

/** Albums split into rows of colCount. */
const rows = computed(() => {
  const cols = colCount.value;
  const out: AlbumGridItem[][] = [];
  for (let i = 0; i < props.albums.length; i += cols) {
    out.push(props.albums.slice(i, i + cols));
  }
  return out;
});

/** Total scroll height of the virtual container. */
const totalHeight = computed(() => {
  const n = rows.value.length;
  if (n === 0) return 0;
  return PAD * 2 + n * rowHeight.value - GAP;
});

/** Indices of the rows that should be rendered (with overscan). */
const visibleRowRange = computed(() => {
  const rh  = rowHeight.value;
  const st  = scrollTopVal.value;
  const ch  = containerHeight.value;
  const start = Math.max(0, Math.floor((st - PAD) / rh) - OVERSCAN);
  const end   = Math.min(rows.value.length, Math.ceil((st + ch - PAD) / rh) + OVERSCAN);
  return { start, end };
});

const visibleRows = computed(() => {
  const { start, end } = visibleRowRange.value;
  return rows.value.slice(start, end).map((albums, i) => ({
    rowIndex: start + i,
    albums,
  }));
});

/** Pixel top of a given row index inside the virtual container. */
function rowTop(rowIndex: number) {
  return PAD + rowIndex * rowHeight.value;
}

// ── Public API ────────────────────────────────────────────────────────────────
function scrollToAlbum(key: string) {
  const container = getScrollEl();
  if (!container) return;
  const idx = props.albums.findIndex((a) => a.key === key);
  if (idx < 0) return;
  const ri  = Math.floor(idx / colCount.value);
  const top = rowTop(ri);
  container.scrollTop = Math.max(0, top - container.clientHeight / 2 + CARD_HEIGHT / 2);
}

defineExpose({ scrollToAlbum });
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <div v-if="albums.length === 0" class="flex h-full items-center justify-center text-sm text-stone-400">
      No albums match current filters.
    </div>
    <div
      v-else
      ref="containerRef"
      class="relative min-h-0 flex-1 overflow-y-auto"
    >
      <!-- Full-height spacer so the scrollbar reflects total content -->
      <div :style="{ height: totalHeight + 'px', position: 'relative' }">
        <!-- Only render visible rows, each absolutely positioned -->
        <div
          v-for="{ rowIndex, albums: rowAlbums } in visibleRows"
          :key="rowIndex"
          class="absolute grid"
          :style="{
            top: rowTop(rowIndex) + 'px',
            left: PAD + 'px',
            right: PAD + 'px',
            gap: GAP + 'px',
            gridTemplateColumns: `repeat(${colCount}, minmax(0, 1fr))`,
          }"
        >
          <AlbumGridCard
            v-for="album in rowAlbums"
            :key="album.key"
            :album="album"
            :is-playing="album.key === playingAlbumKey"
            :data-album-key="album.key"
            @openAlbum="emit('openAlbum', $event)"
            @albumContextMenu="(e, key) => emit('albumContextMenu', e, key)"
          />
        </div>
      </div>
    </div>
  </div>
</template>
