<template>
  <!-- Square artist card: cover collage fills the card, with the artist
       title and total song count on a dark bottom fade — same caption
       treatment as the track square grid (AlbumCard grid mode). -->
  <button
    type="button"
    class="relative aspect-square w-full select-none overflow-hidden rounded-xl bg-surface-variant ring-1 ring-white/10 text-left"
    :aria-label="`Browse artist ${name}`"
    @click="$emit('open', name)"
  >
    <div v-if="coverUrls.length === 0" class="absolute inset-0 flex items-center justify-center">
      <MageIcon name="user" class="h-10 w-10 text-on-surface-variant/50" />
    </div>
    <img
      v-else-if="coverUrls.length === 1"
      :src="coverUrls[0] ?? undefined"
      :alt="name"
      class="absolute inset-0 h-full w-full object-cover"
    />
    <!-- 2 or 3 covers: vertical split, each cover centered in its
         own panel instead of a 2x2 grid with empty cells. -->
    <div v-else-if="coverUrls.length < 4" class="flex h-full w-full">
      <div
        v-for="(url, i) in coverUrls"
        :key="i"
        class="relative h-full flex-1 bg-surface-variant/50"
      >
        <img
          v-if="url"
          :src="url"
          :alt="name"
          class="absolute inset-0 h-full w-full object-cover"
        />
      </div>
    </div>
    <div v-else class="grid h-full w-full grid-cols-2 grid-rows-2">
      <template v-for="(url, i) in coverUrls" :key="i">
        <img v-if="url" :src="url" :alt="name" class="h-full w-full object-cover" />
        <div v-else class="h-full w-full bg-surface-variant/50" />
      </template>
    </div>

    <!-- Dark bottom fade so the caption stays legible over any artwork -->
    <div
      class="glass-scrim pointer-events-none absolute inset-x-0 bottom-0 h-[44px]"
      aria-hidden="true"
    />
    <div class="scrim-label absolute inset-x-0 bottom-0 flex h-[44px] items-center gap-2 px-2.5">
      <div class="min-w-0 flex-1">
        <MarqueeText :text="name" class="text-label-lg font-semibold text-on-surface" />
      </div>
      <div class="flex shrink-0 items-center gap-1 text-on-surface-variant">
        <MageIcon name="music" class="h-3 w-3" />
        <span class="text-label-sm tabular-nums">{{ trackCount }}</span>
      </div>
    </div>
  </button>
</template>

<script setup lang="ts">
import MageIcon from "./MageIcon.vue";
import MarqueeText from "./MarqueeText.vue";

/** A large artist card: up to 4 album covers in a collage, with the artist
 *  name and total song count on the bottom scrim — used for search artist
 *  matches and recent artists. */
defineProps<{
  name: string;
  coverUrls: (string | null)[];
  trackCount: number;
}>();
defineEmits<{ open: [name: string] }>();
</script>
