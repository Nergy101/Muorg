<template>
  <!-- One album card PER track — not grouped by album — so every song gets
       its own cover card, labelled with the track title and artist. Same item
       shape the library's album grid renders. -->
  <div class="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
    <template v-for="card in cards" :key="card.item.key">
      <AlbumCard
        v-if="card.item.hasCover"
        :item="card.item"
        mode="grid"
        @open="$emit('open', card.track)"
      />
      <!-- Tracks without artwork still get a card, with a placeholder
           sleeve instead of the cover. -->
      <button
        v-else
        type="button"
        class="relative aspect-square w-full select-none overflow-hidden rounded-xl bg-surface-variant ring-1 ring-white/10 text-left"
        @click="$emit('open', card.track)"
      >
        <div class="absolute inset-0 flex items-center justify-center">
          <MageIcon name="music" class="h-10 w-10 text-on-surface-variant/60" />
        </div>
        <div
          class="glass-scrim pointer-events-none absolute inset-x-0 bottom-0 h-[44px]"
          aria-hidden="true"
        />
        <div class="scrim-label absolute inset-x-0 bottom-0 flex h-[44px] items-center px-2.5">
          <div class="min-w-0 flex-1">
            <MarqueeText
              :text="card.item.album"
              class="text-label-lg font-semibold text-on-surface"
            />
            <div class="truncate text-label-sm text-on-surface-variant">
              {{ card.item.albumArtist }}
            </div>
          </div>
        </div>
      </button>
    </template>
  </div>
</template>

<script setup lang="ts">
import AlbumCard from "./AlbumCard.vue";
import MageIcon from "./MageIcon.vue";
import MarqueeText from "./MarqueeText.vue";
import type { CatalogTrack, SearchResultCard } from "../types";

/** Album-cover grid of search-result cards (live results or recent results). */
defineProps<{ cards: SearchResultCard[] }>();
defineEmits<{ open: [track: CatalogTrack] }>();
</script>
