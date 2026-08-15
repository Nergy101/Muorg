<template>
  <!-- Render the card once its covers have settled (4 loaded, or the whole
       candidate pool resolved — some may have failed). While still fetching,
       nothing renders so the Home grid reflows and the card pops in whole. The
       cover-request watch lives in setup, so it still runs while hidden. -->
  <div
    v-if="ready"
    class="flex select-none flex-col"
    role="button"
    tabindex="0"
    @click="emit('open')"
    @keydown.enter="emit('open')"
  >
    <div
      class="relative aspect-square w-full overflow-hidden rounded-xl bg-surface-variant shadow-[0_12px_30px_-8px_rgba(0,0,0,0.55),0_2px_6px_rgba(0,0,0,0.35),inset_0_0_0_1px_rgba(255,255,255,0.06)] ring-1 ring-white/10 transition-transform duration-150 active:scale-95 lg:hover:scale-[1.02] lg:hover:shadow-[0_16px_36px_-8px_rgba(0,0,0,0.6)]"
    >
      <div v-if="coverCount === 0" class="absolute inset-0 flex items-center justify-center">
        <MageIcon name="music" class="h-10 w-10 text-white/40" />
      </div>
      <img
        v-else-if="coverCount === 1"
        :src="coverUrls[0] ?? undefined"
        :alt="mix.name"
        class="absolute inset-0 h-full w-full object-cover"
        decoding="async"
      />
      <div v-else class="grid h-full w-full grid-cols-2 grid-rows-2">
        <template v-for="(url, i) in coverUrls" :key="i">
          <img
            v-if="url"
            :src="url"
            :alt="mix.name"
            class="h-full w-full object-cover"
            decoding="async"
          />
          <div v-else class="h-full w-full bg-surface-variant/50" />
        </template>
      </div>

      <!-- Bottom frosted scrim so the title stays legible over artwork -->
      <div
        class="glass-scrim pointer-events-none absolute inset-x-0 bottom-0 h-[52px]"
        aria-hidden="true"
      />

      <!-- Title at the bottom of the glass; music icon + count on the right -->
      <div class="absolute inset-x-0 bottom-0 flex h-[52px] items-end gap-1.5 px-2.5 pb-2">
        <p class="min-w-0 flex-1 truncate text-label-lg font-semibold text-white">{{ mix.name }}</p>
        <span class="flex shrink-0 items-center gap-1 text-label-sm text-white/70">
          <MageIcon name="music" class="h-3.5 w-3.5" />
          <span class="tabular-nums">{{ mix.trackIds.length }}</span>
        </span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from "vue";
import MageIcon from "./MageIcon.vue";
import { useLibraryStore, albumKeyFor } from "../stores/library";
import type { Mix } from "../composables/useMixes";

/**
 * How many distinct album covers we scan from the mix's track order before
 * giving up. Scanning beyond the first 4 gives us a pool of fallbacks: if one
 * of the first four albums has no artwork (or its fetch fails), we substitute
 * the next distinct cover in the mix instead of leaving an empty tile.
 */
const MAX_DISTINCT_COVERS = 16;

const props = defineProps<{ mix: Mix }>();
const emit = defineEmits<{ open: [] }>();

const lib = useLibraryStore();

const trackById = computed(() => new Map(lib.tracks.map((t) => [t.id, t])));

/** Distinct album covers in the mix's track order, capped at MAX_DISTINCT_COVERS. */
const coverCandidates = computed<number[]>(() => {
  const seen = new Set<string>();
  const out: number[] = [];
  for (const id of props.mix.trackIds) {
    const t = trackById.value.get(id);
    if (!t) continue;
    const key = albumKeyFor(t);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(t.id);
    if (out.length === MAX_DISTINCT_COVERS) break;
  }
  return out;
});

/**
 * The covers we render: the first MAX_DISTINCT_COVERS distinct albums' covers
 * that successfully loaded, in track order. Albums whose cover failed are
 * skipped, so a failure naturally falls through to the 5th/6th/... distinct
 * cover instead of leaving an empty tile.
 */
const coverUrls = computed<(string | null)[]>(() => {
  const urls: (string | null)[] = [];
  for (const id of coverCandidates.value) {
    const url = lib.coverCache.get(id);
    if (!url) continue;
    urls.push(url);
    if (urls.length === 4) break;
  }
  return urls;
});
const coverCount = computed(() => coverUrls.value.length);

/**
 * Still fetching while we have fewer than 4 covers AND some candidate is still
 * pending. Once every candidate has settled (loaded or failed), loading ends
 * and the card renders — so a mix with genuinely fewer than 4 distinct covers
 * still appears instead of vanishing.
 */
const loading = computed(() => {
  if (coverCount.value >= 4) return false;
  return coverCandidates.value.some((id) => lib.coverPending.has(id));
});

/** Render once loading settles — always shows every mix. */
const ready = computed(() => !loading.value);

// Request covers for the candidate pool whenever the mix's distinct covers
// change — both on mount and when the Home refresh swaps in a new mix (same
// card instance, new trackIds). requestCover dedupes (pending/cached/failed),
// so re-requesting an already-settled candidate is a no-op. This runs even
// while the card is hidden, so covers load in the background and the card
// appears as soon as they settle.
watch(
  coverCandidates,
  (ids) => {
    for (const id of ids) lib.requestCover(id);
  },
  { immediate: true },
);
</script>
