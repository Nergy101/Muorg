<template>
  <!-- Mounted once in App.vue; visible whenever anything is selected,
       regardless of which view started the selection. Mobile: overlays the
       bottom chrome (mini player / tab bar), replacing it as the thing you're
       looking at while selecting. Desktop (lg): floats near the top instead,
       so it never covers the mini player or the sidebar rail. -->
  <Teleport to="body">
    <Transition
      enter-active-class="sheet-motion"
      enter-from-class="translate-y-4 opacity-0"
      leave-active-class="sheet-motion"
      leave-to-class="translate-y-4 opacity-0"
    >
      <div
        v-if="selection.hasSelection"
        class="glass-strong fixed inset-x-0 bottom-0 z-30 flex flex-col gap-2 whitespace-nowrap rounded-t-3xl px-3 pb-[max(env(safe-area-inset-bottom),0.75rem)] pt-3 shadow-2xl lg:bottom-auto lg:top-14 lg:mx-auto lg:w-fit lg:max-w-[calc(100%-2rem)] lg:flex-row lg:items-center lg:gap-3 lg:rounded-2xl lg:px-4 lg:py-1.5"
        role="toolbar"
        aria-label="Selection actions"
      >
        <div class="flex items-center gap-2">
          <button
            type="button"
            class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface-variant transition-colors lg:hover:bg-surface-container"
            aria-label="Clear selection"
            @click="selection.clear()"
          >
            <MageIcon name="multiply" class="h-4 w-4" />
          </button>
          <span class="text-label-lg font-semibold text-on-surface">{{ selection.count }} selected</span>
          <button type="button" class="ml-1 text-label-lg text-primary" @click="onSelectAll">
            Select all
          </button>
        </div>

        <div class="-mx-1 flex items-center gap-1 overflow-x-auto px-1">
          <button type="button" :class="ACTION_BTN" @click="openPicker">
            <MageIcon name="playlist-add" class="h-5 w-5" />
            <span>Playlist</span>
          </button>
          <button type="button" :class="ACTION_BTN" @click="onAddToQueue">
            <MageIcon name="stack" class="h-5 w-5" />
            <span>Queue</span>
          </button>
          <button type="button" :class="ACTION_BTN" @click="onPlayNext">
            <MageIcon name="arrow-up" class="h-5 w-5" />
            <span>Play next</span>
          </button>
          <button type="button" :class="ACTION_BTN" @click="onEditMetadata">
            <MageIcon name="edit" class="h-5 w-5" />
            <span>Edit</span>
          </button>

          <div class="relative shrink-0">
            <button
              type="button"
              :class="ACTION_BTN"
              aria-haspopup="true"
              :aria-expanded="ratingMenuOpen"
              @click="toggleRatingMenu"
            >
              <StarIcon class="h-5 w-5 text-on-surface-variant" />
              <span>Rate</span>
            </button>
            <div
              v-if="ratingMenuOpen"
              class="glass-strong absolute bottom-full left-1/2 mb-2 flex -translate-x-1/2 items-center gap-0.5 whitespace-nowrap rounded-full px-2 py-1.5 shadow-xl lg:bottom-auto lg:top-full lg:mb-0 lg:mt-2"
              @mouseleave="hoveredStar = null"
            >
              <button
                v-for="n in 5"
                :key="n"
                type="button"
                class="p-1"
                :aria-label="`Rate ${n} star${n > 1 ? 's' : ''}`"
                @mouseenter="hoveredStar = n"
                @click="onPickRating(n)"
              >
                <StarIcon
                  class="h-4 w-4"
                  :class="n <= (hoveredStar ?? 0) ? 'text-primary' : 'text-on-surface-variant/50'"
                  :filled="n <= (hoveredStar ?? 0)"
                />
              </button>
              <button
                type="button"
                class="ml-1.5 shrink-0 text-label-sm text-on-surface-variant"
                @click="onClearRating"
              >Clear</button>
            </div>
          </div>

          <button type="button" :class="[ACTION_BTN, 'text-error']" @click="deleteConfirmOpen = true">
            <MageIcon name="trash" class="h-5 w-5" />
            <span>Delete</span>
          </button>
        </div>
      </div>
    </Transition>
  </Teleport>

  <PlaylistPickerSheet
    :open="pickerOpen"
    :playlists="playlistStore.playlists"
    :membership-ids="membershipIds"
    :partial-membership-ids="partialMembershipIds"
    :track-ids="selection.ids"
    @add="onPickerAdd"
    @remove="onPickerRemove"
    @create="onPickerCreate"
    @close="pickerOpen = false"
  />

  <ConfirmDialog
    :open="rateConfirmOpen"
    title="Rate tracks"
    :message="rateConfirmMessage"
    confirm-label="Rate"
    @confirm="onRateConfirm"
    @cancel="rateConfirmOpen = false"
  />

  <ConfirmDialog
    :open="deleteConfirmOpen"
    title="Delete tracks"
    :message="`Delete ${selection.count} track${selection.count === 1 ? '' : 's'} from the server? This permanently removes the files and cannot be undone.`"
    confirm-label="Delete"
    danger
    @confirm="onDeleteConfirm"
    @cancel="deleteConfirmOpen = false"
  />
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from "vue";
import { useRoute } from "vue-router";
import MageIcon from "./MageIcon.vue";
import StarIcon from "./StarIcon.vue";
import ConfirmDialog from "./ConfirmDialog.vue";
import PlaylistPickerSheet from "./PlaylistPickerSheet.vue";
import { useSelectionStore } from "../stores/selection";
import { useLibraryStore } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { usePlaylistStore } from "../stores/playlists";
import { openMetadataEditor } from "../composables/useMetadataEditor";
import { showToast, showWarning } from "../composables/useToast";
import { visibleIds } from "../composables/useSelectableList";
import type { CatalogTrack, Playlist } from "../types";

const ACTION_BTN =
  "flex shrink-0 flex-col items-center gap-0.5 rounded-xl px-3 py-1.5 text-label-sm text-on-surface transition-colors lg:flex-row lg:gap-1.5 lg:py-1 lg:hover:bg-surface-container";

const route = useRoute();
const selection = useSelectionStore();
const lib = useLibraryStore();
const player = usePlayerStore();
const playlistStore = usePlaylistStore();

/** The selected tracks resolved from the catalog, in selection order. Ids for
 *  tracks that disappeared from the catalog (e.g. deleted elsewhere) drop out. */
const selectedTracks = computed(() =>
  selection.ids.map((id) => lib.trackById.get(id)).filter((t): t is CatalogTrack => t != null),
);

function onSelectAll(): void {
  selection.selectAll(visibleIds());
}

// Escape backs out of whatever popup is open before it clears the whole
// selection — a plain Escape with nothing else open clears it.
function onKeydown(e: KeyboardEvent): void {
  if (e.key !== "Escape" || !selection.hasSelection) return;
  if (ratingMenuOpen.value) {
    ratingMenuOpen.value = false;
    return;
  }
  if (pickerOpen.value) {
    pickerOpen.value = false;
    return;
  }
  if (rateConfirmOpen.value) {
    rateConfirmOpen.value = false;
    return;
  }
  if (deleteConfirmOpen.value) {
    deleteConfirmOpen.value = false;
    return;
  }
  selection.clear();
}
onMounted(() => window.addEventListener("keydown", onKeydown));
onUnmounted(() => window.removeEventListener("keydown", onKeydown));

// Selection is tied to whatever list you were looking at — leaving it (nav,
// logout) drops it rather than following you to an unrelated screen.
watch(() => route.fullPath, () => selection.clear());

// Whenever selection empties (Clear, Escape, a finished delete, logout),
// close every popup too so the next selection doesn't inherit stale state.
const pickerOpen = ref(false);
const ratingMenuOpen = ref(false);
const hoveredStar = ref<number | null>(null);

function toggleRatingMenu(): void {
  ratingMenuOpen.value = !ratingMenuOpen.value;
  hoveredStar.value = null;
}

const rateConfirmOpen = ref(false);
const deleteConfirmOpen = ref(false);
watch(
  () => selection.hasSelection,
  (has) => {
    if (has) return;
    pickerOpen.value = false;
    ratingMenuOpen.value = false;
    rateConfirmOpen.value = false;
    deleteConfirmOpen.value = false;
  },
);

// --- Add to playlist ---------------------------------------------------

async function openPicker(): Promise<void> {
  await playlistStore.loadAllTrackIds();
  pickerOpen.value = true;
}

const membershipIds = computed(() => {
  const result = new Set<number>();
  const ids = selection.ids;
  if (ids.length === 0) return result;
  for (const p of playlistStore.playlists) {
    const set = playlistStore.trackIdSets.get(p.id);
    if (set && ids.every((id) => set.has(id))) result.add(p.id);
  }
  return result;
});

const partialMembershipIds = computed(() => {
  const result = new Set<number>();
  const ids = selection.ids;
  if (ids.length === 0) return result;
  for (const p of playlistStore.playlists) {
    const set = playlistStore.trackIdSets.get(p.id);
    if (!set) continue;
    if (ids.some((id) => set.has(id)) && !ids.every((id) => set.has(id))) result.add(p.id);
  }
  return result;
});

/** The actual add already happened in PlaylistPickerSheet (addTracksChecked,
 *  with its own duplicate-confirm dialog); this just reports success. */
function onPickerAdd(p: Playlist): void {
  showToast(`Added to ${p.name}`);
}

async function onPickerRemove(p: Playlist): Promise<void> {
  try {
    await playlistStore.removeTracks(p.id, selection.ids);
    showToast(`Removed from ${p.name}`);
  } catch (e) {
    showToast(`Failed: ${(e as Error).message}`);
  }
}

async function onPickerCreate(name: string): Promise<void> {
  try {
    const p = await playlistStore.createPlaylist(name, "🎵");
    await playlistStore.addTracks(p.id, selection.ids);
    showToast(`Added to ${p.name}`);
  } catch (e) {
    showToast(`Failed: ${(e as Error).message}`);
  }
}

// --- Queue ---------------------------------------------------------------

function onAddToQueue(): void {
  if (selectedTracks.value.length > 0) player.addTracksToQueue(selectedTracks.value);
}

/** `playNext` inserts one track right after the current one; calling it
 *  per-track in reverse leaves the final run in the selection's own order
 *  (each insert lands at the same fixed spot, pushing the previous one down). */
function onPlayNext(): void {
  const tracks = selectedTracks.value;
  for (let i = tracks.length - 1; i >= 0; i--) player.playNext(tracks[i]);
}

// --- Metadata --------------------------------------------------------------

function onEditMetadata(): void {
  openMetadataEditor(selection.ids);
}

// --- Rating (Q4: confirm + one undo step for >1 track) --------------------

const pendingRating = ref<number | null>(null);

const rateConfirmMessage = computed(() => {
  const n = selection.count;
  const what = pendingRating.value === null ? "clear the rating" : `set the rating to ${pendingRating.value}★`;
  return `${what[0].toUpperCase()}${what.slice(1)} on all ${n} selected tracks?`;
});

function onPickRating(star: number): void {
  ratingMenuOpen.value = false;
  pendingRating.value = star;
  if (selection.count > 1) rateConfirmOpen.value = true;
  else void applyRating(star);
}

function onClearRating(): void {
  ratingMenuOpen.value = false;
  pendingRating.value = null;
  if (selection.count > 1) rateConfirmOpen.value = true;
  else void applyRating(null);
}

async function applyRating(value: number | null): Promise<void> {
  await lib.setRatings(selection.ids, value);
}

function onRateConfirm(): void {
  rateConfirmOpen.value = false;
  void applyRating(pendingRating.value);
}

// --- Delete (permanent; Q3-adjacent but a distinct confirm) ---------------

async function onDeleteConfirm(): Promise<void> {
  deleteConfirmOpen.value = false;
  const ids = selection.ids;
  const { deleted, failed } = await lib.deleteTracks(ids);
  if (failed.length > 0) {
    showWarning(
      `Deleted ${deleted.length} track${deleted.length === 1 ? "" : "s"}; ${failed.length} failed`,
    );
  } else {
    showToast(`Deleted ${deleted.length} track${deleted.length === 1 ? "" : "s"}`);
  }
  selection.clear();
  selection.prune((id) => lib.trackById.has(id));
}
</script>
