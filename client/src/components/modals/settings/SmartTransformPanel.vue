<script setup lang="ts">
/**
 * Smart Transform tab: bulk path rewriting.
 *
 * Every match is previewed and individually selectable before anything runs,
 * because this moves real files on disk — there is no undo beyond the backups.
 */
import { computed, ref, watch } from "vue";
import * as catalogApi from "../../../api/catalog";
import { useCatalogStore } from "../../../stores/catalog";
import { buildTransformPath } from "../../../utils/pathFormat";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const store = useCatalogStore();

const transformOrigin = ref("");
const transformDest = ref("");
const transformSelectedIds = ref<Set<number>>(new Set());
const transformError = ref<string | null>(null);

interface TransformMatch {
  track: (typeof store.tracks)[0];
  newPath: string;
}

const transformMatches = computed<TransformMatch[]>(() => {
  const origin = transformOrigin.value.trim();
  const dest = transformDest.value.trim();
  if (!origin || !dest) return [];
  return store.tracks
    .map((t) => {
      const newPath = buildTransformPath(origin, dest, t.path);
      return newPath && newPath !== t.path ? { track: t, newPath } : null;
    })
    .filter((x): x is TransformMatch => x !== null);
});

const transformAllSelected = computed(
  () =>
    transformMatches.value.length > 0 &&
    transformMatches.value.every((m) => transformSelectedIds.value.has(m.track.id)),
);

function toggleTransformSelectAll() {
  if (transformAllSelected.value) {
    transformSelectedIds.value = new Set();
  } else {
    transformSelectedIds.value = new Set(transformMatches.value.map((m) => m.track.id));
  }
}

function toggleTransformTrack(id: number) {
  const next = new Set(transformSelectedIds.value);
  if (next.has(id)) {
    next.delete(id);
  } else {
    next.add(id);
  }
  transformSelectedIds.value = next;
}

async function runTransform() {
  const selected = transformSelectedIds.value;
  const toTransform = transformMatches.value.filter((m) => selected.has(m.track.id));
  if (!toTransform.length) return;
  transformError.value = null;
  const total = toTransform.length;
  store.setBulkProgress({ current: 0, total });
  try {
    for (let i = 0; i < toTransform.length; i++) {
      const { track, newPath } = toTransform[i];
      const id = store._trackIdByPath(track.path);
      if (id != null) await catalogApi.renameTrackFile(id, newPath);
      store.setBulkProgress({ current: i + 1, total });
    }
    await store.loadTracks();
    transformSelectedIds.value = new Set();
  } catch (err) {
    transformError.value = String(err);
  } finally {
    store.setBulkProgress(null);
  }
}

// Reset selection when matches change
watch(transformMatches, () => {
  transformSelectedIds.value = new Set();
});
</script>

<template>
          <div class="space-y-3 flex flex-col h-full">
            <p class="flex items-center gap-2 text-xs font-semibold text-stone-400 shrink-0">
              <FeatherIcon name="shuffle" class="h-3.5 w-3.5 shrink-0 text-stone-500" />
              Smart Transform
            </p>

            <!-- Pattern inputs -->
            <div class="settings-section shrink-0">
              <div class="grid grid-cols-2 gap-3">
                <div>
                  <label class="block text-xs font-medium text-stone-500 mb-1">Origin pattern</label>
                  <input
                    v-model="transformOrigin"
                    type="text"
                    class="w-full rounded border border-stone-600 bg-stone-900 px-2 py-1.5 font-mono text-xs text-stone-200 placeholder:text-stone-500"
                    placeholder="e.g. &lt;Artist&gt;/&lt;Album&gt;/&lt;TrackNumber&gt; - &lt;TrackTitle&gt;.&lt;Format&gt;"
                  />
                </div>
                <div>
                  <label class="block text-xs font-medium text-stone-500 mb-1">Destination pattern</label>
                  <input
                    v-model="transformDest"
                    type="text"
                    class="w-full rounded border border-stone-600 bg-stone-900 px-2 py-1.5 font-mono text-xs text-stone-200 placeholder:text-stone-500"
                    placeholder="e.g. &lt;AlbumArtist&gt;/&lt;Year&gt; - &lt;Album&gt;/&lt;TrackNumber&gt; - &lt;TrackTitle&gt;.&lt;Format&gt;"
                  />
                </div>
              </div>
              <p class="mt-2 text-xs text-stone-500">
                Tracks whose path matches the Origin pattern are listed below. The path prefix before the matched segments is preserved; only the matched segments are rewritten using the Destination pattern.
              </p>
            </div>

            <!-- Match list -->
            <div class="settings-section flex-1 min-h-0 flex flex-col overflow-hidden p-0">
              <!-- Header row -->
              <div class="flex items-center gap-2 border-b border-stone-700 px-3 py-2 shrink-0">
                <input
                  type="checkbox"
                  :checked="transformAllSelected"
                  :indeterminate="transformSelectedIds.size > 0 && !transformAllSelected"
                  class="shrink-0"
                  @change="toggleTransformSelectAll"
                />
                <span class="flex-1 text-xs font-medium text-stone-500">Origin path</span>
                <span class="w-5 shrink-0" />
                <span class="flex-1 text-xs font-medium text-stone-500">Transformed path</span>
              </div>

              <!-- No patterns yet -->
              <div v-if="!transformOrigin.trim() || !transformDest.trim()" class="flex-1 flex items-center justify-center">
                <p class="text-xs text-stone-500">Enter an Origin and Destination pattern to see matching tracks.</p>
              </div>

              <!-- No matches -->
              <div v-else-if="transformMatches.length === 0" class="flex-1 flex items-center justify-center">
                <p class="text-xs text-stone-500">No tracks match the Origin pattern.</p>
              </div>

              <!-- Match rows -->
              <div v-else class="flex-1 overflow-y-auto">
                <div
                  v-for="m in transformMatches"
                  :key="m.track.id"
                  class="flex items-start gap-2 border-b border-stone-700/50 px-3 py-1.5 cursor-pointer hover:bg-stone-700/30"
                  :class="transformSelectedIds.has(m.track.id) ? 'bg-stone-700/20' : ''"
                  @click="toggleTransformTrack(m.track.id)"
                >
                  <input
                    type="checkbox"
                    :checked="transformSelectedIds.has(m.track.id)"
                    class="mt-0.5 shrink-0 pointer-events-none"
                  />
                  <span class="flex-1 font-mono text-xs text-stone-400 break-all">{{ m.track.path }}</span>
                  <FeatherIcon name="arrow-right" class="h-3 w-3 shrink-0 mt-0.5 text-stone-500" />
                  <span class="flex-1 font-mono text-xs text-stone-300 break-all">{{ m.newPath }}</span>
                </div>
              </div>
            </div>

            <!-- Bottom bar -->
            <div class="shrink-0 flex items-center justify-between gap-3">
              <p v-if="transformError" class="text-xs text-red-400">{{ transformError }}</p>
              <p v-else-if="transformMatches.length > 0" class="text-xs text-stone-500">
                {{ transformSelectedIds.size }} of {{ transformMatches.length }} selected
              </p>
              <span v-else />
              <button
                type="button"
                class="settings-action-btn rounded px-3 py-1.5 text-xs font-medium disabled:opacity-40"
                :disabled="transformSelectedIds.size === 0"
                @click="runTransform"
              >
                Transform
              </button>
            </div>
          </div>

          <!-- ── Statistics ──────────────────────────────────────────── -->
</template>
