<template>
  <div
    class="flex select-none flex-col"
    role="button"
    tabindex="0"
    @click="emit('open')"
    @keydown.enter="emit('open')"
  >
    <!-- Cover collage: up to 4 distinct covers from the mix's tracks -->
    <div
      class="relative aspect-square w-full overflow-hidden rounded-xl bg-surface-variant transition-transform duration-150 active:scale-95 lg:hover:scale-[1.02]"
    >
      <div v-if="coverCount === 0" class="absolute inset-0 flex items-center justify-center text-4xl">
        {{ mix.emoji }}
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

      <!-- Bottom scrim so the emoji + title stay legible over any artwork -->
      <div
        class="pointer-events-none absolute inset-x-0 bottom-0 h-[64px] bg-gradient-to-t from-[#111111e6] to-transparent"
        aria-hidden="true"
      />

      <!-- Emoji + title at the bottom of the card -->
      <div class="absolute inset-x-0 bottom-0 flex items-center gap-1.5 px-2.5 pb-2">
        <span class="text-lg leading-none drop-shadow">{{ mix.emoji }}</span>
        <div class="min-w-0 flex-1">
          <p class="truncate text-label-lg font-semibold text-white">{{ mix.name }}</p>
          <p class="text-label-sm text-white/70">
            {{ mix.trackIds.length }} {{ mix.trackIds.length === 1 ? "track" : "tracks" }}
          </p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, watch } from "vue";
import { useLibraryStore, albumKeyFor } from "../stores/library";
import type { Mix } from "../composables/useMixes";

const props = defineProps<{ mix: Mix }>();
const emit = defineEmits<{ open: [] }>();

const lib = useLibraryStore();

const trackById = computed(() => new Map(lib.tracks.map((t) => [t.id, t])));

/** First 4 distinct album covers in the mix's track order. */
const coverTrackIds = computed<number[]>(() => {
  const seen = new Set<string>();
  const out: number[] = [];
  for (const id of props.mix.trackIds) {
    const t = trackById.value.get(id);
    if (!t) continue;
    const key = albumKeyFor(t);
    if (seen.has(key)) continue;
    seen.add(key);
    out.push(t.id);
    if (out.length === 4) break;
  }
  return out;
});

const coverUrls = computed<(string | null)[]>(
  () => coverTrackIds.value.map((id) => lib.coverCache.get(id) ?? null),
);
const coverCount = computed(() => coverUrls.value.filter(Boolean).length);

// Request covers whenever the mix's distinct covers change — both on mount and
// when the Home refresh swaps in a new mix (same card instance, new trackIds).
watch(
  coverTrackIds,
  (ids) => {
    for (const id of ids) if (!lib.coverCache.has(id)) lib.requestCover(id);
  },
  { immediate: true },
);
</script>
