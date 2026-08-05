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
              <FeatherIcon name="music" class="h-4 w-4 text-on-surface-variant/60" />
            </div>
          </div>
          <div class="min-w-0 flex-1">
            <MarqueeText :text="track.title ?? '—'" class="text-title-md text-on-surface" />
            <p class="truncate text-body-sm text-on-surface-variant">{{ subtitle }}</p>
          </div>
        </div>

        <div class="my-1 border-t border-outline/30" />

        <button type="button" :class="ROW" @click="onToggleFavorite">
          <FeatherIcon
            name="heart"
            class="h-5 w-5 shrink-0"
            :class="isFavorite ? 'text-primary' : 'text-on-surface-variant'"
          />
          <span :class="isFavorite ? 'text-primary' : ''">
            {{ isFavorite ? "Remove from favorites" : "Add to favorites" }}
          </span>
        </button>

        <button type="button" :class="ROW" @click="openPlaylists">
          <FeatherIcon name="plus" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Add to playlist</span>
        </button>

        <button
          v-if="canRemoveFromQueue"
          type="button"
          :class="[ROW, 'text-error']"
          @click="onRemoveQueue"
        >
          <FeatherIcon name="trash-2" class="h-5 w-5 shrink-0" />
          <span>Remove from queue</span>
        </button>
        <button v-else type="button" :class="ROW" @click="onAddToQueue">
          <FeatherIcon name="align-justify" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Add to queue</span>
        </button>

        <div class="my-1 border-t border-outline/30" />

        <button
          v-if="artistName"
          type="button"
          class="flex min-h-14 w-full items-center gap-4 px-6 py-2 text-left"
          @click="emit('view-artist'); emit('close')"
        >
          <FeatherIcon name="user" class="h-5 w-5 shrink-0 text-on-surface-variant" />
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
          <FeatherIcon name="music" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span class="min-w-0 flex-1">
            <span class="block text-body-lg text-on-surface">View album</span>
            <span class="block truncate text-body-sm text-on-surface-variant">{{ track.album }}</span>
          </span>
        </button>

        <div class="my-1 border-t border-outline/30" />

        <button type="button" :class="ROW" @click="level = 'info'">
          <FeatherIcon name="info" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Track info</span>
        </button>
        <button type="button" :class="ROW" @click="openEdit">
          <FeatherIcon name="edit-2" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <span>Edit metadata</span>
        </button>
        <div class="h-4" />
      </template>

      <!-- ─── playlists ────────────────────────────────────────────────── -->
      <template v-else-if="level === 'playlists'">
        <button type="button" :class="ROW" @click="level = 'main'">
          <FeatherIcon name="arrow-left" class="h-5 w-5 shrink-0" />
          <span>Back</span>
        </button>
        <div :class="LABEL">ADD TO…</div>

        <div v-if="membershipLoading" class="flex justify-center py-6">
          <FeatherIcon name="refresh-cw" class="h-6 w-6 animate-spin text-on-surface-variant" />
        </div>

        <template v-else>
          <button
            v-for="p in playlistStore.playlists"
            :key="p.id"
            type="button"
            class="flex h-14 w-full items-center gap-3 px-6 text-left"
            @click="togglePlaylist(p)"
          >
            <span class="text-title-lg leading-none">{{ p.icon ?? "🎵" }}</span>
            <span class="min-w-0 flex-1 truncate text-body-lg text-on-surface">{{ p.name }}</span>
            <FeatherIcon
              v-if="isInPlaylist(p)"
              name="check-circle"
              class="h-5 w-5 shrink-0 text-primary"
            />
            <FeatherIcon v-else name="circle" class="h-5 w-5 shrink-0 text-on-surface-variant/40" />
          </button>

          <div class="my-1 border-t border-outline/30" />

          <button v-if="!creating" type="button" :class="[ROW, 'text-primary']" @click="startCreating">
            <FeatherIcon name="plus" class="h-5 w-5 shrink-0" />
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
          <FeatherIcon name="arrow-left" class="h-5 w-5 shrink-0" />
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

      <!-- ─── edit ─────────────────────────────────────────────────────── -->
      <template v-else>
        <button type="button" :class="ROW" @click="level = 'main'">
          <FeatherIcon name="arrow-left" class="h-5 w-5 shrink-0" />
          <span>Back</span>
        </button>
        <div :class="LABEL">EDIT METADATA</div>

        <div v-for="field in EDIT_FIELDS" :key="field.key" class="px-6 py-1.5">
          <label class="mb-1 block text-body-sm text-on-surface-variant">{{ field.label }}</label>
          <input
            v-model="form[field.key]"
            :type="field.key === 'year' ? 'number' : 'text'"
            class="w-full rounded-xl bg-surface-variant px-3 py-2.5 text-body-lg text-on-surface outline-none"
          />
        </div>

        <div class="flex justify-end gap-2 px-6 pb-6 pt-3">
          <button
            type="button"
            class="rounded-full px-4 py-2 text-label-lg text-on-surface-variant"
            @click="level = 'main'"
          >Cancel</button>
          <button
            type="button"
            class="rounded-full px-4 py-2 text-label-lg text-primary"
            @click="onSave"
          >Save</button>
        </div>
      </template>
    </template>
  </BottomSheet>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import BottomSheet from "./BottomSheet.vue";
import MarqueeText from "./MarqueeText.vue";
import { useLibraryStore, formatDuration } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { usePlaylistStore } from "../stores/playlists";
import { showToast } from "../composables/useToast";
import type { CatalogTrack, Playlist } from "../types";

const ROW = "flex h-14 w-full items-center gap-4 px-6 text-left text-body-lg text-on-surface";
const LABEL = "px-6 pt-3 pb-1 text-label-sm uppercase tracking-[0.8px] text-primary";

/** Editable metadata fields, in the Android sheet's order. */
const EDIT_FIELDS = [
  { key: "title", label: "Title" },
  { key: "artist", label: "Artist" },
  { key: "album", label: "Album" },
  { key: "album_artist", label: "Album Artist" },
  { key: "genre", label: "Genre" },
  { key: "year", label: "Year" },
] as const;

type EditKey = (typeof EDIT_FIELDS)[number]["key"];

const props = defineProps<{
  open: boolean;
  track: CatalogTrack | null;
  canRemoveFromQueue?: boolean;
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

const level = ref<"main" | "playlists" | "info" | "edit">("main");

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

function onAddToQueue(): void {
  const t = props.track;
  if (!t) return;
  player.addToQueue(t);
  emit("close");
}

function onRemoveQueue(): void {
  emit("remove-from-queue");
  emit("close");
}

// --- playlists level ------------------------------------------------------

const membership = ref<Set<number>>(new Set());
const membershipLoading = ref(false);
const creating = ref(false);
const newName = ref("");
const nameInput = ref<HTMLInputElement | null>(null);

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

  if (wasIn) {
    await playlistStore.removeTracks(p.id, [t.id]);
    showToast(`Removed from ${p.name}`);
  } else {
    await playlistStore.addTracks(p.id, [t.id]);
    showToast(`Added to ${p.name}`);
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
  const p = await playlistStore.createPlaylist(name, "🎵");
  await playlistStore.addTracks(p.id, [t.id]);
  membership.value = new Set(membership.value).add(p.id);
  showToast(`Added to ${name}`);
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

// --- edit level ------------------------------------------------------------

const form = ref<Record<EditKey, string>>({
  title: "",
  artist: "",
  album: "",
  album_artist: "",
  genre: "",
  year: "",
});

function openEdit(): void {
  const t = props.track;
  if (!t) return;
  form.value = {
    title: t.title ?? "",
    artist: t.artist ?? "",
    album: t.album ?? "",
    album_artist: t.album_artist ?? "",
    genre: t.genre ?? "",
    year: t.year != null ? String(t.year) : "",
  };
  level.value = "edit";
}

function onSave(): void {
  const t = props.track;
  if (!t) return;
  const parsedYear = Number.parseInt(form.value.year, 10);
  // The store owns the diff, the no-change toast and the library reload.
  void player.saveMetadata(t, {
    title: form.value.title,
    artist: form.value.artist,
    album: form.value.album,
    album_artist: form.value.album_artist,
    genre: form.value.genre,
    year: Number.isNaN(parsedYear) ? null : parsedYear,
  });
  emit("close");
}
</script>
