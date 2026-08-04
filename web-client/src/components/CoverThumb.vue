<template>
  <span class="inline-flex shrink-0 items-center justify-center overflow-hidden bg-stone-800" :class="props.class">
    <img v-if="coverUrl" :src="coverUrl" :alt="props.track.title ?? ''" class="h-full w-full object-cover" />
    <FeatherIcon v-else name="music" class="h-1/2 w-1/2 text-stone-600" />
  </span>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useLibraryStore } from "../stores/library";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import type { CatalogTrack } from "../types";

const props = defineProps<{ track: CatalogTrack; class?: string }>();

const lib = useLibraryStore();

const coverUrl = computed(() => {
  if (!props.track.has_cover) return null;
  lib.requestCover(props.track.id);
  return lib.coverCache.get(props.track.id) ?? null;
});
</script>
