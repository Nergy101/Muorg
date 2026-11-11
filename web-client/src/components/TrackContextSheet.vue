<template>
  <BottomSheet :open="open" @close="emit('close')">
    <template v-if="tracks.length > 0">
      <!-- ─── main ─────────────────────────────────────────────────────── -->
      <template v-if="level === 'main'">
        <div class="flex items-center gap-3 px-6 pb-3">
          <div class="h-11 w-11 shrink-0 overflow-hidden rounded bg-surface-variant">
            <img
              v-if="coverUrl"
              :src="coverUrl"
              :alt="tracks[0].album ?? ''"
              class="h-full w-full object-cover"
            />
            <div v-else class="flex h-full w-full items-center justify-center">
              <MageIcon name="music" class="h-4 w-4 text-on-surface-variant/60" />
            </div>
          </div>
          <div class="min-w-0 flex-1">
            <MarqueeText
              :text="tracks.length === 1 ? tracks[0].title ?? '—' : `${tracks.length} tracks selected`"
              class="text-title-md text-on-surface"
            />
            <p class="truncate text-body-sm text-on-surface-variant">{{ subtitle }}</p>
          </div>
        </div>

        <div class="flex items-center justify-center px-6 py-2">
          <StarRating :model-value="rating" @update:model-value="onSetRating" />
        </div>

        <div class="my-1 border-t border-outline/30" />

        <button type="button" :class="ROW" @click="onPlay">
          <MageIcon name="play" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Play</span>
        </button>
        <button type="button" :class="ROW" @click="onPlayNext">
          <MageIcon name="next" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Play next</span>
        </button>
        <button type="button" :class="ROW" @click="onAddToQueue">
          <MageIcon name="stack" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Add to queue</span>
        </button>
        <button type="button" :class="ROW" @click="level = 'playlists'">
          <MageIcon name="playlist-add" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Add to playlist…</span>
        </button>
        <button type="button" :class="ROW" @click="onToggleFavorite">
          <MageIcon
            name="heart"
            class="h-5 w-5 shrink-0"
            :class="allFavorite ? 'text-primary fill-current' : 'text-on-surface-variant'"
          />
          <span :class="allFavorite ? 'text-primary' : ''">
            {{ allFavorite ? "Unfavourite" : "Favourite" }}
          </span>
        </button>

        <div class="my-1 border-t border-outline/30" />

        <button type="button" :class="ROW" @click="onEditMetadata">
          <MageIcon name="edit" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Edit metadata…</span>
        </button>
        <button v-if="tracks.length === 1" type="button" :class="ROW" @click="onCopyPath">
          <MageIcon name="note-text" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Copy path</span>
        </button>
        <button v-if="tracks.length === 1" type="button" :class="ROW" @click="level = 'info'">
          <MageIcon name="information-circle" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Track info</span>
        </button>

        <div class="my-1 border-t border-outline/30" />

        <button type="button" :class="[ROW, 'text-error']" @click="deleteConfirmOpen = true">
          <MageIcon name="trash" class="h-5 w-5 shrink-0" />
          <span>Delete…</span>
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
        <button
          v-for="p in manualPlaylists"
          :key="p.id"
          type="button"
          class="flex h-14 w-full items-center gap-3 px-6 text-left"
          @click="onAddToPlaylist(p)"
        >
          <span class="text-title-lg leading-none">{{ p.icon ?? "🎵" }}</span>
          <span class="min-w-0 flex-1 truncate text-body-lg text-on-surface">{{ p.name }}</span>
        </button>
        <div class="h-4" />
      </template>

      <!-- ─── info ─────────────────────────────────────────────────────── -->
      <template v-else>
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
    :open="ratingConfirm !== null"
    title="Rate tracks?"
    :message="`Set the rating for ${ratingConfirm?.ids.length ?? 0} tracks?`"
    confirm-label="Rate"
    @confirm="confirmRating"
    @cancel="ratingConfirm = null"
  />
  <ConfirmDialog
    :open="deleteConfirmOpen"
    title="Delete tracks?"
    :message="`Permanently delete ${tracks.length} track${tracks.length === 1 ? '' : 's'} from the server? This cannot be undone.`"
    confirm-label="Delete"
    danger
    @confirm="onDeleteConfirm"
    @cancel="deleteConfirmOpen = false"
  />
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import MageIcon from "./MageIcon.vue";
import BottomSheet from "./BottomSheet.vue";
import ConfirmDialog from "./ConfirmDialog.vue";
import MarqueeText from "./MarqueeText.vue";
import StarRating from "./metadata/StarRating.vue";
import { useLibraryStore, formatDuration } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { usePlaylistStore } from "../stores/playlists";
import { useSelectionStore } from "../stores/selection";
import { showToast } from "../composables/useToast";
import { openMetadataEditor } from "../composables/useMetadataEditor";
import type { CatalogTrack, Playlist } from "../types";

const ROW = "flex h-14 w-full items-center gap-4 px-6 text-left text-body-lg text-on-surface";
const LABEL = "px-6 pt-3 pb-1 text-label-sm uppercase tracking-[0.8px] text-primary";

const props = defineProps<{
  open: boolean;
  tracks: CatalogTrack[];
}>();

const emit = defineEmits<{ close: [] }>();

const lib = useLibraryStore();
const player = usePlayerStore();
const playlistStore = usePlaylistStore();
const selection = useSelectionStore();

const level = ref<"main" | "playlists" | "info">("main");

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) level.value = "main";
  },
);

const coverUrl = computed(() => {
  const t = props.tracks[0];
  if (!t?.has_cover) return null;
  lib.requestCover(t.id);
  return lib.coverCache.get(t.id) ?? null;
});

const subtitle = computed(() => {
  if (props.tracks.length > 1) return `${props.tracks.length} tracks`;
  const t = props.tracks[0];
  const parts = [t.artist ?? t.album_artist, t.album].filter((s): s is string => !!s);
  return parts.join(" · ") || "—";
});

const manualPlaylists = computed(() => playlistStore.playlists.filter((p) => p.smart_rules == null));

const allFavorite = computed(
  () => props.tracks.length > 0 && props.tracks.every((t) => player.favorites.has(t.id)),
);

const rating = computed<number | null>(() => {
  const tracks = props.tracks;
  if (!tracks.length) return null;
  const first = tracks[0].rating ?? null;
  return tracks.every((t) => (t.rating ?? null) === first) ? first : null;
});

function onPlay(): void {
  if (!props.tracks.length) return;
  void player.playTrack(props.tracks[0], props.tracks);
  emit("close");
}

function onPlayNext(): void {
  for (const t of [...props.tracks].reverse()) player.playNext(t);
  emit("close");
}

function onAddToQueue(): void {
  player.addTracksToQueue(props.tracks);
  emit("close");
}

async function onAddToPlaylist(p: Playlist): Promise<void> {
  const ids = props.tracks.map((t) => t.id);
  emit("close");
  try {
    await playlistStore.addTracks(p.id, ids);
    showToast(`Added to ${p.name}`);
  } catch (e) {
    showToast(`Failed: ${(e as Error).message}`);
  }
}

async function onToggleFavorite(): Promise<void> {
  const makeFavorite = !allFavorite.value;
  emit("close");
  for (const t of props.tracks) {
    if (player.favorites.has(t.id) !== makeFavorite) await player.toggleFavorite(t);
  }
}

const ratingConfirm = ref<{ ids: number[]; rating: number | null } | null>(null);

function onSetRating(value: number | null): void {
  const ids = props.tracks.map((t) => t.id);
  if (ids.length > 1) {
    ratingConfirm.value = { ids, rating: value };
  } else {
    void lib.setRatings(ids, value);
  }
  emit("close");
}

function confirmRating(): void {
  const pending = ratingConfirm.value;
  ratingConfirm.value = null;
  if (pending) void lib.setRatings(pending.ids, pending.rating);
}

function onEditMetadata(): void {
  openMetadataEditor(props.tracks.map((t) => t.id));
  emit("close");
}

function onCopyPath(): void {
  const t = props.tracks[0];
  if (!t) return;
  navigator.clipboard.writeText(t.path).catch(() => {
    showToast("Couldn't copy path");
  });
  emit("close");
}

const deleteConfirmOpen = ref(false);

async function onDeleteConfirm(): Promise<void> {
  deleteConfirmOpen.value = false;
  const ids = props.tracks.map((t) => t.id);
  emit("close");
  const result = await lib.deleteTracks(ids);
  selection.prune((id) => lib.trackById.has(id));
  if (result.failed.length > 0) {
    showToast(`Deleted ${result.deleted.length}, ${result.failed.length} failed`);
  } else {
    showToast(`Deleted ${result.deleted.length} track${result.deleted.length === 1 ? "" : "s"}`);
  }
}

function formatTimestamp(secs: number): string {
  const d = new Date(secs * 1000);
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

const infoFields = computed(() => {
  const t = props.tracks[0];
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
