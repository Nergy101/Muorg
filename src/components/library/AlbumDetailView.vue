<script setup lang="ts">
import { nextTick, onMounted, ref } from "vue";
import TrackAlbumArt from "../shared/TrackAlbumArt.vue";
import LibraryTableBody from "./LibraryTableBody.vue";
import type { CatalogTrack } from "../../types";

const props = defineProps<{
  albumTitle: string;
  albumArtist: string;
  albumYear: number | null;
  coverPath: string;
  tracks: CatalogTrack[];
}>();

const emit = defineEmits<{
  (e: "openMetadata"): void;
}>();

const tableBodyRef = ref<InstanceType<typeof LibraryTableBody> | null>(null);

onMounted(() => {
  // Ensure album detail opens with tracks fully visible when grouping is enabled globally.
  nextTick(() => tableBodyRef.value?.expandAllGroups?.());
});

function onAlbumHeaderContextMenu(e: MouseEvent) {
  tableBodyRef.value?.openContextMenu(e, props.tracks);
}
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
<div class="flex shrink-0 items-center justify-center px-4 py-4">
      <div
        class="inline-flex cursor-context-menu items-center gap-6 rounded-lg border border-stone-700 px-5 py-4 transition-colors hover:border-stone-500"
        @contextmenu.prevent="onAlbumHeaderContextMenu"
      >
        <TrackAlbumArt :path="coverPath" :size-px="220" />
        <div class="min-w-0">
          <div class="truncate text-base font-semibold text-stone-100">{{ albumTitle }}</div>
          <div class="mt-1 truncate text-sm text-stone-300">{{ albumArtist }}</div>
          <div v-if="albumYear !== null" class="mt-1 text-xs text-stone-400">{{ albumYear }}</div>
        </div>
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
