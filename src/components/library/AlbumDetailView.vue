<script setup lang="ts">
import { nextTick, onMounted, ref } from "vue";
import TrackAlbumArt from "../shared/TrackAlbumArt.vue";
import LibraryTableBody from "./LibraryTableBody.vue";
import FeatherIcon from "../shared/FeatherIcon.vue";
import type { CatalogTrack } from "../../types";

defineProps<{
  albumTitle: string;
  albumArtist: string;
  albumYear: number | null;
  coverPath: string;
  tracks: CatalogTrack[];
}>();

const emit = defineEmits<{
  (e: "back"): void;
  (e: "openMetadata"): void;
}>();

const tableBodyRef = ref<InstanceType<typeof LibraryTableBody> | null>(null);

onMounted(() => {
  // Ensure album detail opens with tracks fully visible when grouping is enabled globally.
  nextTick(() => tableBodyRef.value?.expandAllGroups?.());
});
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <div class="flex shrink-0 items-center justify-between border-b border-stone-700 px-4 py-3">
      <button
        type="button"
        class="inline-flex items-center gap-1 rounded border border-stone-600 bg-stone-800 px-2 py-1 text-xs text-stone-200 hover:bg-stone-700"
        @click="emit('back')"
      >
        <FeatherIcon name="arrow-left" class="h-3.5 w-3.5" />
        Back
      </button>
      <div class="text-right">
        <div class="text-sm font-semibold text-stone-100">{{ albumTitle }}</div>
        <div class="text-xs text-stone-400">{{ albumArtist }}</div>
      </div>
    </div>

    <div class="flex shrink-0 items-center justify-center gap-6 px-4 py-4">
      <TrackAlbumArt :path="coverPath" :size-px="220" />
      <div class="min-w-0">
        <div class="truncate text-base font-semibold text-stone-100">{{ albumTitle }}</div>
        <div class="mt-1 truncate text-sm text-stone-300">{{ albumArtist }}</div>
        <div v-if="albumYear !== null" class="mt-1 text-xs text-stone-400">{{ albumYear }}</div>
      </div>
    </div>

    <div class="min-h-0 flex flex-1 flex-col overflow-hidden">
      <LibraryTableBody
        ref="tableBodyRef"
        :tracks-override="tracks"
        :disable-grouping="true"
        :hide-artist-column="true"
        :hide-album-column="true"
        :hide-year-column="true"
        @openMetadata="emit('openMetadata')"
      />
    </div>
  </div>
</template>

