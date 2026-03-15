<script setup lang="ts">
import { computed } from "vue";
import type { CatalogTrack } from "../../types";
import FeatherIcon from "../shared/FeatherIcon.vue";

const props = defineProps<{
  open: boolean;
  title: string;
  tracks: CatalogTrack[];
  duplicateCount: number | null;
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (e: "selectTrack", track: CatalogTrack): void;
}>();

const hasTracks = computed(() => props.tracks.length > 0);

function close() {
  emit("close");
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") close();
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[250] flex items-center justify-center bg-stone-950/70 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="reports-modal-title"
      @keydown="onKeydown"
      @click.self="close"
    >
      <div class="max-h-[80vh] w-full max-w-3xl overflow-hidden rounded-lg border border-stone-600 bg-stone-800 shadow-xl" @click.stop>
        <div class="flex items-center justify-between border-b border-stone-700 px-4 py-3">
          <div class="flex items-center gap-2">
            <h2 id="reports-modal-title" class="text-sm font-semibold text-stone-200">
              {{ title }}
            </h2>
            <span v-if="duplicateCount != null" class="rounded-full bg-red-500/20 px-2 py-0.5 text-[0.7rem] font-medium text-red-300">
              {{ duplicateCount }} extra duplicate{{ duplicateCount === 1 ? "" : "s" }}
            </span>
          </div>
          <button
            type="button"
            class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
            aria-label="Close"
            @click="close"
          >
            <FeatherIcon name="x" class="h-4 w-4" />
          </button>
        </div>

        <div class="max-h-[calc(80vh-3rem)] overflow-y-auto">
          <table class="w-full border-collapse text-left text-sm">
            <thead class="bg-stone-900">
              <tr class="border-b border-stone-700 text-xs font-semibold uppercase tracking-wide text-stone-400">
                <th class="px-3 py-2">Title</th>
                <th class="px-3 py-2">Artist</th>
                <th class="px-3 py-2">Album</th>
                <th class="px-3 py-2">Year</th>
                <th class="px-3 py-2">Path</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!hasTracks">
                <td colspan="5" class="px-3 py-4 text-center text-xs text-stone-400">
                  No tracks match this report.
                </td>
              </tr>
              <tr
                v-for="track in tracks"
                v-else
                :key="track.id"
                class="border-b border-stone-700/60 hover:bg-stone-800/70 cursor-pointer"
                @click="emit('selectTrack', track)"
              >
                <td class="px-3 py-1.5 text-stone-200">{{ track.title ?? "—" }}</td>
                <td class="px-3 py-1.5 text-stone-200">{{ track.artist ?? "—" }}</td>
                <td class="px-3 py-1.5 text-stone-200">{{ track.album ?? "—" }}</td>
                <td class="px-3 py-1.5 text-stone-300">{{ track.year ?? "—" }}</td>
                <td class="px-3 py-1.5 text-[11px] text-stone-400">
                  <span class="line-clamp-2 break-all">{{ track.path }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </Teleport>
</template>

