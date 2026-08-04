<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-150"
      enter-from-class="opacity-0"
      leave-active-class="transition-opacity duration-150"
      leave-to-class="opacity-0"
    >
      <div v-if="open" class="fixed inset-0 z-[60] flex flex-col bg-stone-900 md:hidden">
        <!-- Search header -->
        <div class="safe-top flex shrink-0 items-center gap-2 border-b border-stone-800 px-3 py-2.5">
          <button
            type="button"
            class="flex h-10 w-10 shrink-0 items-center justify-center rounded text-stone-400 active:bg-stone-800"
            aria-label="Close search"
            @click="close"
          >
            <FeatherIcon name="arrow-left" class="h-5 w-5" />
          </button>
          <div class="relative min-w-0 flex-1">
            <FeatherIcon name="search" class="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-stone-500" />
            <input
              ref="inputRef"
              v-model="query"
              type="search"
              placeholder="Search title, artist, album…"
              enterkeyhint="search"
              class="h-11 w-full rounded-full border border-stone-700 bg-stone-800 pl-9 pr-4 text-sm text-stone-100 placeholder-stone-500 focus:border-accent focus:outline-none"
            />
            <button
              v-if="query"
              type="button"
              class="absolute right-2 top-1/2 flex h-7 w-7 -translate-y-1/2 items-center justify-center rounded-full text-stone-400 active:bg-stone-700"
              aria-label="Clear search"
              @click="query = ''"
            >
              <FeatherIcon name="x" class="h-4 w-4" />
            </button>
          </div>
        </div>

        <!-- Body -->
        <div class="min-h-0 flex-1 overflow-y-auto overscroll-contain px-3 pb-[max(env(safe-area-inset-bottom,0px),0.75rem)]">
          <!-- Recent searches -->
          <div v-if="!query" class="pt-4">
            <div class="flex items-center justify-between px-1 pb-2">
              <span class="text-xs font-semibold uppercase tracking-wide text-stone-500">Recent</span>
              <button
                v-if="recentSearches.length"
                type="button"
                class="text-xs text-stone-500 underline underline-offset-2 active:text-stone-300"
                @click="clearRecent"
              >
                Clear
              </button>
            </div>
            <div v-if="recentSearches.length === 0" class="px-1 py-8 text-center text-sm text-stone-600">
              Search your library, albums, artists and playlists.
            </div>
            <button
              v-for="r in recentSearches"
              :key="r"
              type="button"
              class="flex h-11 w-full items-center gap-3 rounded-lg px-3 text-left text-sm text-stone-300 active:bg-stone-800"
              @click="query = r"
            >
              <FeatherIcon name="clock" class="h-4 w-4 shrink-0 text-stone-500" />
              <span class="min-w-0 flex-1 truncate">{{ r }}</span>
            </button>
          </div>

          <!-- Results -->
          <template v-else>
            <section v-if="tracks.length" class="pt-4">
              <h2 class="px-1 pb-1.5 text-xs font-semibold uppercase tracking-wide text-stone-500">Tracks</h2>
              <button
                v-for="t in tracks"
                :key="'t' + t.id"
                type="button"
                class="flex min-h-12 w-full items-center gap-3 rounded-lg px-2 text-left active:bg-stone-800/70"
                @click="playTrack(t)"
              >
                <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded bg-stone-800 text-stone-400">
                  <FeatherIcon :name="t.id === lib.nowPlaying?.id ? 'volume-2' : 'music'" class="h-4 w-4" />
                </span>
                <span class="min-w-0 flex-1">
                  <span class="block truncate text-sm text-stone-100">{{ t.title ?? '—' }}</span>
                  <span class="block truncate text-xs text-stone-500">{{ t.artist ?? t.album_artist ?? '—' }}</span>
                </span>
              </button>
            </section>

            <section v-if="albums.length" class="pt-4">
              <h2 class="px-1 pb-1.5 text-xs font-semibold uppercase tracking-wide text-stone-500">Albums</h2>
              <button
                v-for="a in albums"
                :key="'a' + a.key"
                type="button"
                class="flex min-h-12 w-full items-center gap-3 rounded-lg px-2 text-left active:bg-stone-800/70"
                @click="openAlbum(a)"
              >
                <span class="flex h-9 w-9 shrink-0 items-center justify-center overflow-hidden rounded bg-stone-800">
                  <img v-if="albumCover(a)" :src="albumCover(a)" :alt="a.album" class="h-full w-full object-cover" />
                  <FeatherIcon v-else name="disc" class="h-4 w-4 text-stone-500" />
                </span>
                <span class="min-w-0 flex-1">
                  <span class="block truncate text-sm text-stone-100">{{ a.album }}</span>
                  <span class="block truncate text-xs text-stone-500">{{ a.albumArtist }}</span>
                </span>
              </button>
            </section>

            <section v-if="artists.length" class="pt-4">
              <h2 class="px-1 pb-1.5 text-xs font-semibold uppercase tracking-wide text-stone-500">Artists</h2>
              <button
                v-for="name in artists"
                :key="'ar' + name"
                type="button"
                class="flex min-h-12 w-full items-center gap-3 rounded-lg px-2 text-left active:bg-stone-800/70"
                @click="openArtist(name)"
              >
                <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-stone-700 text-stone-400">
                  <FeatherIcon name="user" class="h-4 w-4" />
                </span>
                <span class="min-w-0 flex-1 truncate text-sm text-stone-100">{{ name }}</span>
              </button>
            </section>

            <section v-if="playlists.length" class="pt-4">
              <h2 class="px-1 pb-1.5 text-xs font-semibold uppercase tracking-wide text-stone-500">Playlists</h2>
              <button
                v-for="p in playlists"
                :key="'p' + p.id"
                type="button"
                class="flex min-h-12 w-full items-center gap-3 rounded-lg px-2 text-left active:bg-stone-800/70"
                @click="openPlaylist(p.id)"
              >
                <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded bg-stone-800 text-base">
                  {{ p.icon ?? '🎵' }}
                </span>
                <span class="min-w-0 flex-1 truncate text-sm text-stone-100">{{ p.name }}</span>
                <span class="shrink-0 text-xs text-stone-500">{{ p.track_count }}</span>
              </button>
            </section>

            <div
              v-if="query.length >= 2 && !tracks.length && !albums.length && !artists.length && !playlists.length"
              class="px-1 py-10 text-center text-sm text-stone-600"
            >
              No results for “{{ query }}”.
            </div>
          </template>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, nextTick, ref, watch } from "vue";
import { useLibraryStore } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import type { CatalogTrack, AlbumGridItem } from "../types";

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{
  close: [];
  "open-album": [item: AlbumGridItem];
}>();

const lib = useLibraryStore();
const playlistStore = usePlaylistStore();

const query = ref("");
const inputRef = ref<HTMLInputElement | null>(null);

const RECENT_KEY = "muorg-web-recent-searches";
const recentSearches = ref<string[]>(loadRecent());

function loadRecent(): string[] {
  try {
    const v = JSON.parse(localStorage.getItem(RECENT_KEY) ?? "[]");
    return Array.isArray(v) ? v.filter((x): x is string => typeof x === "string").slice(0, 5) : [];
  } catch {
    return [];
  }
}

function saveRecent(q: string): void {
  const next = [q, ...recentSearches.value.filter((s) => s !== q)].slice(0, 5);
  recentSearches.value = next;
  localStorage.setItem(RECENT_KEY, JSON.stringify(next));
}

function clearRecent(): void {
  recentSearches.value = [];
  localStorage.removeItem(RECENT_KEY);
}

const q = computed(() => query.value.trim().toLowerCase());

const tracks = computed(() => {
  if (q.value.length < 2) return [];
  return lib.tracks
    .filter(
      (t) =>
        (t.title ?? "").toLowerCase().includes(q.value) ||
        (t.artist ?? "").toLowerCase().includes(q.value) ||
        (t.album ?? "").toLowerCase().includes(q.value) ||
        (t.album_artist ?? "").toLowerCase().includes(q.value),
    )
    .slice(0, 12);
});

const albums = computed(() => {
  if (q.value.length < 2) return [];
  return lib.albumGridItems
    .filter(
      (a) =>
        a.album.toLowerCase().includes(q.value) || a.albumArtist.toLowerCase().includes(q.value),
    )
    .slice(0, 10);
});

const artists = computed(() => {
  if (q.value.length < 2) return [];
  const seen = new Set<string>();
  const out: string[] = [];
  for (const t of lib.tracks) {
    const name = (t.artist ?? t.album_artist ?? "").trim();
    if (name && name.toLowerCase().includes(q.value) && !seen.has(name)) {
      seen.add(name);
      out.push(name);
      if (out.length >= 8) break;
    }
  }
  return out;
});

const playlists = computed(() => {
  if (q.value.length < 2) return [];
  return playlistStore.playlists
    .filter((p) => p.name.toLowerCase().includes(q.value))
    .slice(0, 8);
});

function albumCover(a: AlbumGridItem): string | undefined {
  if (!a.hasCover || a.coverTrackId === null) return undefined;
  lib.requestCover(a.coverTrackId);
  return lib.coverCache.get(a.coverTrackId) ?? undefined;
}

function playTrack(t: CatalogTrack): void {
  saveRecent(query.value.trim());
  lib.playTrack(t);
  close();
}

function openAlbum(a: AlbumGridItem): void {
  saveRecent(query.value.trim());
  emit("open-album", a);
  close();
}

function openArtist(name: string): void {
  saveRecent(query.value.trim());
  lib.searchQuery = name;
  lib.setGroupBy("artist");
  lib.setViewMode("table");
  close();
}

function openPlaylist(id: number): void {
  saveRecent(query.value.trim());
  playlistStore.selectPlaylist(id);
  close();
}

function close(): void {
  query.value = "";
  emit("close");
}

watch(
  () => props.open,
  (open) => {
    if (open) {
      nextTick(() => inputRef.value?.focus());
    } else {
      query.value = "";
    }
  },
);
</script>
