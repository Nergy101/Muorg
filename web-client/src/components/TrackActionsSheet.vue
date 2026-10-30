<template>
  <BottomSheet :open="open" @close="emit('close')">
    <template v-if="track">
      <!-- ─── main ─────────────────────────────────────────────────────── -->
      <template v-if="level === 'main'">
        <div class="flex items-center gap-3 px-6 pb-3">
          <div class="h-11 w-11 shrink-0 overflow-hidden rounded bg-surface-variant">
            <img
              v-if="coverUrl"
              :src="coverUrl"
              :alt="track.album ?? ''"
              class="h-full w-full object-cover"
            />
            <div v-else class="flex h-full w-full items-center justify-center">
              <MageIcon name="music" class="h-4 w-4 text-on-surface-variant/60" />
            </div>
          </div>
          <div class="min-w-0 flex-1">
            <MarqueeText :text="track.title ?? '—'" class="text-title-md text-on-surface" />
            <p class="truncate text-body-sm text-on-surface-variant">{{ subtitle }}</p>
          </div>
        </div>

        <div class="flex items-center justify-between gap-4 px-6 py-2">
          <span class="text-body-lg text-on-surface">Rating</span>
          <StarRating :model-value="track.rating" @update:model-value="onSetRating" />
        </div>

        <div class="my-1 border-t border-outline/30" />

        <button type="button" :class="ROW" @click="onToggleFavorite">
          <MageIcon
            name="heart"
            class="h-5 w-5 shrink-0"
            :class="isFavorite ? 'text-primary' : 'text-on-surface-variant'"
          />
          <span :class="isFavorite ? 'text-primary' : ''">
            {{ isFavorite ? "Remove from favorites" : "Add to favorites" }}
          </span>
        </button>

        <button
          v-if="canMoveToUserQueue"
          type="button"
          :class="ROW"
          @click="onMoveToPlayNext"
        >
          <MageIcon name="arrow-up" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Play next</span>
        </button>

        <button
          v-if="canMoveToUserQueue"
          type="button"
          :class="ROW"
          @click="onMoveToUserQueue"
        >
          <MageIcon name="stack" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Add to queue</span>
        </button>

        <button type="button" :class="ROW" @click="openPlaylists">
          <MageIcon name="playlist-add" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Add to playlist</span>
        </button>

        <button
          v-if="canRemoveFromQueue"
          type="button"
          :class="[ROW, 'text-error']"
          @click="onRemoveQueue"
        >
          <MageIcon name="trash" class="h-5 w-5 shrink-0" />
          <span>Remove from queue</span>
        </button>
        <template v-else>
          <button type="button" :class="ROW" @click="onPlayNext">
            <MageIcon name="arrow-up" class="h-5 w-5 shrink-0 text-on-surface-variant" />
            <span>Play next</span>
          </button>
          <button type="button" :class="ROW" @click="onAddToQueue">
            <MageIcon name="stack" class="h-5 w-5 shrink-0 text-on-surface-variant" />
            <span>Add to queue</span>
          </button>
        </template>

        <div class="my-1 border-t border-outline/30" />

        <button
          v-if="artistName"
          type="button"
          class="flex min-h-14 w-full items-center gap-4 px-6 py-2 text-left"
          @click="emit('view-artist'); emit('close')"
        >
          <MageIcon name="user" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span class="min-w-0 flex-1">
            <span class="block text-body-lg text-on-surface">View artist</span>
            <span class="block truncate text-body-sm text-on-surface-variant">{{ artistName }}</span>
          </span>
        </button>

        <button
          v-if="track.album"
          type="button"
          class="flex min-h-14 w-full items-center gap-4 px-6 py-2 text-left"
          @click="emit('view-album'); emit('close')"
        >
          <MageIcon name="music" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span class="min-w-0 flex-1">
            <span class="block text-body-lg text-on-surface">View album</span>
            <span class="block truncate text-body-sm text-on-surface-variant">{{ track.album }}</span>
          </span>
        </button>

        <div class="my-1 border-t border-outline/30" />
        <button type="button" :class="ROW" @click="onSelect">
          <MageIcon name="check-circle" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Select</span>
        </button>

        <button type="button" :class="ROW" @click="level = 'info'">
          <MageIcon name="information-circle" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Track info</span>
        </button>
        <button type="button" :class="ROW" @click="onEditMetadata">
          <MageIcon name="edit" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Edit metadata</span>
        </button>
        <button type="button" :class="[ROW, 'text-error']" @click="deleteConfirmOpen = true">
          <MageIcon name="trash" class="h-5 w-5 shrink-0" />
          <span>Delete track</span>
        </button>
        <div class="h-4" />
      </template>

      <!-- ─── playlists ────────────────────────────────────────────────── -->
      <template v-else-if="level === 'playlists'">
        <button type="button" :class="ROW" @click="level = 'main'">
          <MageIcon name="arrow-left" class="h-5 w-5 shrink-0" />
          <span>Back</span>
        </button>
        <div :class="LABEL">ADD TO…</div>

        <div v-if="membershipLoading" class="flex justify-center py-6">
          <MageIcon name="refresh" class="h-6 w-6 animate-spin text-on-surface-variant" />
        </div>

        <template v-else>
          <button
            v-for="p in manualPlaylists"
            :key="p.id"
            type="button"
            class="flex h-14 w-full items-center gap-3 px-6 text-left"
            @click="togglePlaylist(p)"
          >
            <span class="text-title-lg leading-none">{{ p.icon ?? "🎵" }}</span>
            <span class="min-w-0 flex-1 truncate text-body-lg text-on-surface">{{ p.name }}</span>
            <MageIcon
              v-if="isInPlaylist(p)"
              name="check-circle"
              class="h-5 w-5 shrink-0 text-primary"
            />
            <span v-else class="h-5 w-5 shrink-0 rounded-full border-2 border-on-surface-variant/40" />
          </button>

          <div class="my-1 border-t border-outline/30" />

          <button v-if="!creating" type="button" :class="[ROW, 'text-primary']" @click="startCreating">
            <MageIcon name="plus" class="h-5 w-5 shrink-0" />
            <span>New playlist…</span>
          </button>
          <div v-else class="px-6 pb-4 pt-2">
            <input
              ref="nameInput"
              v-model="newName"
              type="text"
              placeholder="Playlist name"
              class="w-full rounded-xl bg-surface-variant px-3 py-2.5 text-body-lg text-on-surface outline-none placeholder:text-on-surface-variant"
              @keyup.enter="submitCreate"
            />
            <div class="mt-2 flex justify-end gap-2">
              <button
                type="button"
                class="rounded-full px-4 py-2 text-label-lg text-on-surface-variant"
                @click="creating = false"
              >Cancel</button>
              <button
                type="button"
                class="rounded-full px-4 py-2 text-label-lg text-primary disabled:opacity-50"
                :disabled="newName.trim().length === 0"
                @click="submitCreate"
              >Create</button>
            </div>
          </div>
        </template>
        <div class="h-4" />
      </template>

      <!-- ─── info ─────────────────────────────────────────────────────── -->
      <template v-else-if="level === 'info'">
        <button type="button" :class="ROW" @click="level = 'main'">
          <MageIcon name="arrow-left" class="h-5 w-5 shrink-0" />
          <span>Back</span>
        </button>
        <div :class="LABEL">TRACK INFO</div>
        <div
          v-for="field in infoFields"
          :key="field.label"
          class="flex items-start justify-between gap-4 px-6 py-1.5"
        >
          <span class="shrink-0 text-body-md text-on-surface-variant">{{ field.label }}</span>
          <span class="break-all text-right text-body-md text-on-surface">{{ field.value }}</span>
        </div>
        <div class="pb-6" />
      </template>
    </template>
  </BottomSheet>

  <ConfirmDialog
    :open="deleteConfirmOpen"
    title="Delete track"
    :message="`Delete &quot;${track?.title ?? 'this track'}&quot; from the server? This permanently removes the file and cannot be undone.`"
    confirm-label="Delete"
    danger
    @confirm="onDeleteConfirm"
    @cancel="deleteConfirmOpen = false"
  />
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import MageIcon from "./MageIcon.vue";
import BottomSheet from "./BottomSheet.vue";
import ConfirmDialog from "./ConfirmDialog.vue";
import MarqueeText from "./MarqueeText.vue";
import { useLibraryStore, formatDuration } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { usePlaylistStore } from "../stores/playlists";
import { useSelectionStore } from "../stores/selection";
import { showToast, showWarning } from "../composables/useToast";
import { openMetadataEditor } from "../composables/useMetadataEditor";
import type { CatalogTrack, Playlist } from "../types";

const ROW = "flex h-14 w-full items-center gap-4 px-6 text-left text-body-lg text-on-surface";
const LABEL = "px-6 pt-3 pb-1 text-label-sm uppercase tracking-[0.8px] text-primary";

const props = defineProps<{
  open: boolean;
  track: CatalogTrack | null;
  canRemoveFromQueue?: boolean;
  /** Show the "Add to queue" (move to user queue) action below Favorite. Used
   *  on the queue screen for system "Up next" tracks. */
  canMoveToUserQueue?: boolean;
  /** Which level to open on. Lets a caller jump straight to the playlist picker. */
  initialLevel?: "main" | "playlists";
}>();

const emit = defineEmits<{
  close: [];
  "view-artist": [];
  "view-album": [];
  "remove-from-queue": [];
}>();

const lib = useLibraryStore();
const player = usePlayerStore();
const playlistStore = usePlaylistStore();
const selection = useSelectionStore();

function onSelect(): void {
  const t = props.track;
  if (!t) return;
  selection.begin(t.id);
  emit("close");
}

const level = ref<"main" | "playlists" | "info">("main");

watch(
  () => props.open,
  (isOpen) => {
    if (!isOpen) return;
    // openPlaylists also loads membership, which "main" doesn't need.
    if (props.initialLevel === "playlists") void openPlaylists();
    else level.value = "main";
  },
);

const coverUrl = computed(() => {
  const t = props.track;
  if (!t?.has_cover) return null;
  lib.requestCover(t.id);
  return lib.coverCache.get(t.id) ?? null;
});

const artistName = computed(() => props.track?.artist ?? props.track?.album_artist ?? null);

const subtitle = computed(() => {
  const parts = [artistName.value, props.track?.album].filter((s): s is string => !!s);
  return parts.join(" · ") || "—";
});

const isFavorite = computed(() => (props.track ? player.favorites.has(props.track.id) : false));

function onToggleFavorite(): void {
  const t = props.track;
  if (!t) return;
  void player.toggleFavorite(t);
  emit("close");
}

function onPlayNext(): void {
  const t = props.track;
  if (!t) return;
  player.playNext(t);
  emit("close");
}

function onAddToQueue(): void {
  const t = props.track;
  if (!t) return;
  player.addToQueue(t);
  emit("close");
}

/** Moves a system "Up next" track into "Your queue" (removes it from Up next). */
function onMoveToUserQueue(): void {
  const t = props.track;
  if (!t) return;
  player.moveToUserQueue(t);
  emit("close");
}

/** Moves a system "Up next" track so it plays right after the current one. */
function onMoveToPlayNext(): void {
  const t = props.track;
  if (!t) return;
  player.moveToUserQueue(t, true);
  emit("close");
}

function onRemoveQueue(): void {
  emit("remove-from-queue");
  emit("close");
}

/** Single-track rating — no confirm needed (Q4 only requires one for bulk). */
async function onSetRating(value: number | null): Promise<void> {
  const t = props.track;
  if (!t) return;
  await lib.setRatings([t.id], value);
}

function onEditMetadata(): void {
  const t = props.track;
  if (!t) return;
  openMetadataEditor([t.id]);
  emit("close");
}

const deleteConfirmOpen = ref(false);

async function onDeleteConfirm(): Promise<void> {
  deleteConfirmOpen.value = false;
  const t = props.track;
  if (!t) return;
  const { deleted, failed } = await lib.deleteTracks([t.id]);
  if (failed.length > 0) showWarning(`Failed to delete: ${failed[0].message}`);
  else if (deleted.length > 0) showToast("Deleted");
  emit("close");
}

// --- playlists level ------------------------------------------------------

const membership = ref<Set<number>>(new Set());
const membershipLoading = ref(false);
const creating = ref(false);
const newName = ref("");
const nameInput = ref<HTMLInputElement | null>(null);

/** Smart playlists are rule-driven; manual membership toggles are meaningless. */
const manualPlaylists = computed(() => playlistStore.playlists.filter((p) => p.smart_rules == null));

async function openPlaylists(): Promise<void> {
  const t = props.track;
  if (!t) return;
  level.value = "playlists";
  creating.value = false;
  newName.value = "";
  membershipLoading.value = true;
  try {
    membership.value = await playlistStore.getPlaylistsContainingTrack(t.id);
  } finally {
    membershipLoading.value = false;
  }
}

/**
 * Favorites is driven by the player's optimistic set so its tick is correct
 * before the round-trip lands (Android's effectiveMembership).
 */
function isInPlaylist(p: Playlist): boolean {
  if (p.name === "Favorites") return isFavorite.value;
  return membership.value.has(p.id);
}

async function togglePlaylist(p: Playlist): Promise<void> {
  const t = props.track;
  if (!t) return;
  const wasIn = isInPlaylist(p);
  const next = new Set(membership.value);
  if (wasIn) next.delete(p.id);
  else next.add(p.id);
  membership.value = next;

  try {
    if (wasIn) {
      await playlistStore.removeTracks(p.id, [t.id]);
      showToast(`Removed from ${p.name}`);
    } else {
      await playlistStore.addTracks(p.id, [t.id]);
      showToast(`Added to ${p.name}`);
    }
  } catch (e) {
    // Revert the optimistic membership flip on failure.
    const reverted = new Set(membership.value);
    if (wasIn) reverted.add(p.id);
    else reverted.delete(p.id);
    membership.value = reverted;
    showToast(`Failed: ${(e as Error).message}`);
  }
}

async function startCreating(): Promise<void> {
  creating.value = true;
  await nextTick();
  nameInput.value?.focus();
}

async function submitCreate(): Promise<void> {
  const t = props.track;
  const name = newName.value.trim();
  if (!t || !name) return;
  creating.value = false;
  newName.value = "";
  try {
    const p = await playlistStore.createPlaylist(name, "🎵");
    await playlistStore.addTracks(p.id, [t.id]);
    membership.value = new Set(membership.value).add(p.id);
    showToast(`Added to ${name}`);
  } catch (e) {
    showToast(`Failed: ${(e as Error).message}`);
  }
}

// --- info level ------------------------------------------------------------

function formatTimestamp(secs: number): string {
  const d = new Date(secs * 1000);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

const infoFields = computed(() => {
  const t = props.track;
  if (!t) return [];
  const fields: { label: string; value: string }[] = [];
  const push = (label: string, value: string | number | null | undefined): void => {
    if (value === null || value === undefined || value === "") return;
    fields.push({ label, value: String(value) });
  };
  push("Title", t.title);
  push("Artist", t.artist);
  if (t.album_artist !== t.artist) push("Album Artist", t.album_artist);
  push("Featuring", t.featuring);
  push("Album", t.album);
  push("Year", t.year);
  push("Genre", t.genre);
  push("Track #", t.track_number);
  push("Disc #", t.disc_number);
  if (t.duration_secs) push("Duration", formatDuration(t.duration_secs));
  push("Format", t.format.toUpperCase());
  if (t.rating !== null) push("Rating", `${t.rating} / 5`);
  if (t.play_count > 0) push("Play Count", t.play_count);
  if (t.last_played_at !== null) push("Last Played", formatTimestamp(t.last_played_at));
  push("File", t.path);
  return fields;
});
</script>
