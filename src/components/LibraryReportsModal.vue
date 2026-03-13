<script setup lang="ts">
import type { CatalogTrack } from "../types";

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
</script>

<template>
  <Teleport to="body">
    <div
      v-if="props.open"
      class="fixed inset-0 z-[300] flex items-center justify-center bg-stone-950/70 p-4"
      role="dialog"
      aria-modal="true"
      @click.self="emit('close')"
    >
      <div class="report-modal flex max-h-[80vh] w-full max-w-5xl flex-col overflow-hidden rounded-lg border border-stone-600 bg-stone-800 shadow-xl">
        <div class="flex items-center justify-between border-b border-stone-700 px-4 py-3">
          <div class="min-w-0">
            <h2 class="truncate text-sm font-semibold">{{ props.title }}</h2>
            <p class="mt-0.5 text-xs">
              {{ props.tracks.length }} track{{ props.tracks.length === 1 ? "" : "s" }} in this report.<template v-if="props.duplicateCount != null">
                Of which {{ props.duplicateCount }} duplicate{{ props.duplicateCount === 1 ? "" : "s" }}.
              </template>
            </p>
          </div>
          <button type="button" class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200" aria-label="Close report" @click="emit('close')">
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
        <div class="min-h-0 flex-1 overflow-y-auto bg-stone-900/60 px-3 py-2 text-xs">
          <table class="w-full border-collapse text-left">
            <thead class="sticky top-0 bg-stone-900">
              <tr class="border-b border-stone-700 text-[0.7rem] font-medium uppercase tracking-wide text-stone-500">
                <th class="px-2 py-1">Title</th>
                <th class="px-2 py-1">Artist</th>
                <th class="px-2 py-1">Album</th>
                <th class="px-2 py-1">Path</th>
                <th class="px-2 py-1 text-right">Go</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="t in props.tracks" :key="t.id" class="border-b border-stone-800/70 hover:bg-stone-800/70">
                <td class="max-w-[220px] truncate px-2 py-1 text-stone-100" :title="t.title || '—'">{{ t.title || "—" }}</td>
                <td class="max-w-[200px] truncate px-2 py-1 text-stone-200" :title="t.artist || '—'">{{ t.artist || "—" }}</td>
                <td class="max-w-[320px] truncate px-2 py-1 text-stone-200" :title="t.album || '—'">{{ t.album || "—" }}</td>
                <td class="max-w-[320px] truncate px-2 py-1 text-stone-500" :title="t.path">{{ t.path }}</td>
                <td class="px-2 py-1 text-right">
                  <button
                    type="button"
                    class="rounded border border-stone-600 px-2 py-0.5 text-[0.7rem] text-stone-200 hover:bg-stone-700"
                    @click="emit('selectTrack', t)"
                  >
                    Edit
                  </button>
                </td>
              </tr>
              <tr v-if="!props.tracks.length">
                <td colspan="5" class="px-2 py-4 text-center text-stone-500">No tracks currently match this report.</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </Teleport>
</template>

