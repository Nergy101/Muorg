<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import MageIcon from "./MageIcon.vue";
import {
  getListeningSummary,
  getListeningTime,
  getPlayClock,
  getTopAlbumsByTime,
  getTopArtistsByTime,
  getTopTracksByTime,
} from "../api/catalog";
import type {
  ClockBucket,
  ListeningSummary,
  ListeningWeek,
  TopAlbumStat,
  TopArtistStat,
  TopTrackStat,
} from "../types";

/**
 * Listening statistics from the server's play history (NER-325): what was
 * actually listened, not just what was scanned.
 *
 * Unlike the catalogue charts next to it, this panel fetches its own data —
 * the numbers live in `play_history` on the server and are not derivable from
 * the loaded track list. Everything is ranked by listening time; plays break
 * ties. A `duration_played` measurement is used when the client reported
 * one, otherwise the track's catalog duration estimates the row.
 */

const WINDOWS = [7, 30, 90] as const;
const LIST_SIZE = 5;
const WEEKS_SHOWN = 12;

const days = ref<number>(30);
const loading = ref(true);
const error = ref(false);

const summary = ref<ListeningSummary | null>(null);
const weeks = ref<ListeningWeek[]>([]);
const topTracks = ref<TopTrackStat[]>([]);
const topArtists = ref<TopArtistStat[]>([]);
const topAlbums = ref<TopAlbumStat[]>([]);
const clock = ref<ClockBucket[]>([]);

// Responses are tagged with the window they were requested for, so a slow
// request for 7 days cannot overwrite the state of a newer 90-day request.
let fetchToken = 0;

async function fetchAll(targetDays: number): Promise<void> {
  const token = ++fetchToken;
  loading.value = true;
  error.value = false;
  try {
    const [s, t, a, al, c] = await Promise.all([
      getListeningSummary(targetDays),
      getTopTracksByTime(LIST_SIZE, targetDays),
      getTopArtistsByTime(LIST_SIZE, targetDays),
      getTopAlbumsByTime(LIST_SIZE, targetDays),
      getPlayClock(targetDays),
    ]);
    if (token !== fetchToken) return;
    summary.value = s;
    topTracks.value = t;
    topArtists.value = a;
    topAlbums.value = al;
    clock.value = c;
  } catch {
    if (token === fetchToken) {
      error.value = true;
      summary.value = null;
      topTracks.value = [];
      topArtists.value = [];
      topAlbums.value = [];
      clock.value = [];
    }
  } finally {
    if (token === fetchToken) loading.value = false;
  }
}

onMounted(() => {
  void fetchAll(days.value);
  // The weekly graph reads the rollup and never depends on the window; one
  // fetch is enough. Keep it out of `loading` so a slow week fetch cannot
  // hold the whole panel hostage.
  void getListeningTime(WEEKS_SHOWN)
    .then((w) => {
      weeks.value = w;
    })
    .catch(() => {
      /* the rest of the panel still renders without the weekly graph */
    });
});

watch(days, (d) => void fetchAll(d));

/** Seconds listened as `Xh Ym` (or `Ym` under an hour), like the totals row. */
function fmtDuration(secs: number): string {
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m`;
  return `${secs}s`;
}

const weekBars = computed(() => {
  const max = Math.max(1, ...weeks.value.map((w) => w.listening_secs));
  return weeks.value.map((w) => ({
    label: new Date(w.week_start * 1000).toLocaleDateString(undefined, {
      day: "numeric",
      month: "short",
    }),
    secs: w.listening_secs,
    pct: (w.listening_secs / max) * 100,
    empty: w.plays === 0,
  }));
});

/** The clock always renders all 24 hours so the shape of a day reads at a glance. */
const clockBars = computed(() => {
  const byHour = new Map(clock.value.map((c) => [c.hour, c]));
  const max = Math.max(1, ...clock.value.map((c) => c.plays));
  return Array.from({ length: 24 }, (_, hour) => {
    const c = byHour.get(hour);
    return {
      hour,
      plays: c?.plays ?? 0,
      pct: ((c?.plays ?? 0) / max) * 100,
      empty: !c,
    };
  });
});

function fmtTimeOfDay(hour: number): string {
  const h = hour % 12 === 0 ? 12 : hour % 12;
  return `${h}${hour < 12 ? "am" : "pm"}`;
}
</script>

<template>
  <section class="rounded-2xl bg-surface-container p-4">
    <div class="mb-3 flex flex-wrap items-center justify-between gap-2">
      <p class="text-label-lg text-on-surface-variant">Listening</p>
      <div class="flex items-center gap-1">
        <button
          v-for="w in WINDOWS"
          :key="w"
          type="button"
          class="rounded-full px-2 py-1 text-label-sm transition-colors"
          :class="
            days === w
              ? 'bg-primary text-on-primary'
              : 'text-on-surface-variant hover:bg-surface-variant/60'
          "
          :aria-pressed="days === w"
          @click="days = w"
        >
          {{ w }}d
        </button>
      </div>
    </div>

    <div v-if="loading && summary === null" class="flex items-center justify-center py-8">
      <MageIcon name="refresh" class="h-6 w-6 animate-spin text-on-surface-variant" />
    </div>

    <div v-else-if="error" class="py-4 text-center">
      <p class="text-body-sm text-on-surface-variant">Listening statistics are unavailable.</p>
      <button
        type="button"
        class="mt-2 rounded-full px-3 py-1 text-label-sm text-on-surface-variant transition-colors hover:bg-surface-variant/60"
        @click="fetchAll(days)"
      >
        Try again
      </button>
    </div>

    <div v-else-if="summary === null" class="py-4 text-center text-body-sm text-on-surface-variant">
      Nothing listened yet.
    </div>

    <div v-else class="space-y-4">
      <!-- Window totals -->
      <div class="grid grid-cols-3 gap-2">
        <div class="rounded-xl bg-surface-variant/40 px-2 py-2 text-center">
          <p class="text-title-md tabular-nums text-on-surface">{{ summary.plays.toLocaleString() }}</p>
          <p class="text-label-sm text-on-surface-variant">Plays</p>
        </div>
        <div class="rounded-xl bg-surface-variant/40 px-2 py-2 text-center">
          <p class="text-title-md tabular-nums text-on-surface">{{ fmtDuration(summary.listening_secs) }}</p>
          <p class="text-label-sm text-on-surface-variant">Listened</p>
        </div>
        <div class="rounded-xl bg-surface-variant/40 px-2 py-2 text-center">
          <p class="text-title-md tabular-nums text-on-surface">{{ summary.unique_tracks.toLocaleString() }}</p>
          <p class="text-label-sm text-on-surface-variant">Tracks</p>
        </div>
      </div>
      <p class="text-center text-label-sm text-on-surface-variant">
        {{ summary.unique_artists.toLocaleString() }} artists ·
        {{ summary.unique_albums.toLocaleString() }} albums in the last {{ days }} days
      </p>

      <!-- Weekly listening time -->
      <div v-if="weekBars.length">
        <p class="mb-2 text-label-md text-on-surface-variant">Listening time per week</p>
        <div class="flex h-20 items-end gap-1" aria-label="Listening time per week">
          <div
            v-for="(bar, i) in weekBars"
            :key="i"
            class="group/bar min-w-0 flex-1"
          >
            <div
              class="w-full rounded-sm transition-all duration-300"
              :class="bar.empty ? 'bg-surface-variant/60' : 'bg-primary group-hover/bar:opacity-80'"
              :style="{ height: Math.max(bar.pct, bar.empty ? 3 : 6) + '%' }"
            />
            <p
              v-if="i % 2 === 0"
              class="mt-1 truncate text-center text-[10px] leading-tight text-on-surface-variant"
            >
              {{ bar.label }}
            </p>
            <p v-else class="mt-1 text-center text-[10px] leading-tight text-transparent">·</p>
          </div>
        </div>
      </div>

      <!-- Top lists, ranked by listening time -->
      <div class="grid gap-4 lg:grid-cols-3">
        <div>
          <p class="mb-2 text-label-md text-on-surface-variant">Top tracks</p>
          <p v-if="!topTracks.length" class="text-xs text-on-surface-variant">Nothing listened yet.</p>
          <ul v-else class="space-y-1.5">
            <li v-for="row in topTracks" :key="row.track_id" class="min-w-0">
              <p class="truncate text-xs text-on-surface">{{ row.title }}</p>
              <div class="flex items-baseline justify-between gap-2">
                <span class="min-w-0 truncate text-[11px] text-on-surface-variant">{{ row.artist }}</span>
                <span class="shrink-0 text-[11px] tabular-nums text-on-surface-variant">
                  {{ fmtDuration(row.listening_secs) }} · ×{{ row.plays }}
                </span>
              </div>
            </li>
          </ul>
        </div>
        <div>
          <p class="mb-2 text-label-md text-on-surface-variant">Top artists</p>
          <p v-if="!topArtists.length" class="text-xs text-on-surface-variant">Nothing listened yet.</p>
          <ul v-else class="space-y-1.5">
            <li v-for="row in topArtists" :key="row.artist" class="flex items-baseline justify-between gap-2">
              <span class="min-w-0 truncate text-xs text-on-surface">{{ row.artist }}</span>
              <span class="shrink-0 text-[11px] tabular-nums text-on-surface-variant">
                {{ fmtDuration(row.listening_secs) }} · ×{{ row.plays }}
              </span>
            </li>
          </ul>
        </div>
        <div>
          <p class="mb-2 text-label-md text-on-surface-variant">Top albums</p>
          <p v-if="!topAlbums.length" class="text-xs text-on-surface-variant">Nothing listened yet.</p>
          <ul v-else class="space-y-1.5">
            <li v-for="row in topAlbums" :key="row.album" class="min-w-0">
              <p class="truncate text-xs text-on-surface">{{ row.album }}</p>
              <div class="flex items-baseline justify-between gap-2">
                <span class="min-w-0 truncate text-[11px] text-on-surface-variant">{{ row.album_artist }}</span>
                <span class="shrink-0 text-[11px] tabular-nums text-on-surface-variant">
                  {{ fmtDuration(row.listening_secs) }}
                </span>
              </div>
            </li>
          </ul>
        </div>
      </div>

      <!-- Clock -->
      <div>
        <p class="mb-2 text-label-md text-on-surface-variant">
          When you listen
          <span v-if="clock.length" class="text-on-surface-variant/70">(server-local time)</span>
        </p>
        <p v-if="!clock.length" class="text-xs text-on-surface-variant">Nothing listened yet.</p>
        <div v-else class="flex h-16 items-end gap-0.5" aria-label="Plays per hour of day">
          <div v-for="bar in clockBars" :key="bar.hour" class="min-w-0 flex-1">
            <div
              class="w-full rounded-sm transition-all duration-300"
              :class="bar.empty ? 'bg-surface-variant/60' : 'bg-primary'"
              :style="{ height: Math.max(bar.pct, bar.empty ? 3 : 6) + '%' }"
            />
            <p class="mt-1 text-center text-[10px] leading-tight text-on-surface-variant">
              {{ bar.hour % 3 === 0 ? bar.hour : "" }}
            </p>
          </div>
        </div>
        <p class="mt-1 text-center text-[10px] text-on-surface-variant">
          {{ fmtTimeOfDay(0) }} to {{ fmtTimeOfDay(24) }}
        </p>
      </div>
    </div>
  </section>
</template>
