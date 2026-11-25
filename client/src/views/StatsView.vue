<script setup lang="ts">
import { computed, onMounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import PrettySelect from "../components/shared/PrettySelect.vue";
import { useCatalogStore } from "../stores/catalog";
import { api } from "../api/client";
import { getStats, type LibraryStats } from "../api/catalog";
import type { ListeningSummary, TopAlbumStat, TopArtistStat, TopTrackStat } from "@shared/api";

const store = useCatalogStore();
const { tracks } = storeToRefs(store);

const stats = ref<LibraryStats | null>(null);

onMounted(async () => {
  try {
    stats.value = await getStats();
  } catch {
    stats.value = null;
  }
});

function fmtDuration(secs: number): string {
  const h = Math.floor(secs / 3600);
  const m = Math.round((secs % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
}

// ── Listening section (web-client Statistics page) ─────────────────────────
const days = ref(30);
const RANGES = [
  { value: "7", label: "Last 7 days" },
  { value: "30", label: "Last 30 days" },
  { value: "90", label: "Last 90 days" },
];
const summary = ref<ListeningSummary | null>(null);
const topTracks = ref<TopTrackStat[]>([]);
const topArtists = ref<TopArtistStat[]>([]);
const topAlbums = ref<TopAlbumStat[]>([]);

async function loadListening() {
  const d = days.value;
  try {
    const [s, t, ar, al] = await Promise.all([
      api.getListeningSummary(d),
      api.getTopTracksByTime(10, d),
      api.getTopArtistsByTime(10, d),
      api.getTopAlbumsByTime(10, d),
    ]);
    summary.value = s;
    topTracks.value = t;
    topArtists.value = ar;
    topAlbums.value = al;
  } catch {
    // server without play history: leave empty
  }
}
onMounted(loadListening);
watch(days, loadListening);

// ── Genre distribution (donut) ──────────────────────────────────────────────
const genreRows = computed(() => {
  const map = new Map<string, number>();
  for (const t of tracks.value) {
    const g = (t.genre ?? "").trim() || "Unknown";
    map.set(g, (map.get(g) ?? 0) + 1);
  }
  return [...map.entries()].map(([genre, count]) => ({ genre, count })).sort((a, b) => b.count - a.count);
});
const genreTop = computed(() => genreRows.value.slice(0, 8));
const genreTotal = computed(() => genreRows.value.reduce((a, r) => a + r.count, 0));

// ── Tracks per release year (line chart) ────────────────────────────────────
const yearRows = computed(() => {
  const map = new Map<number, number>();
  for (const t of tracks.value) {
    if (t.year == null) continue;
    map.set(t.year, (map.get(t.year) ?? 0) + 1);
  }
  return [...map.entries()].map(([year, count]) => ({ year, count })).sort((a, b) => a.year - b.year);
});
const yearMax = computed(() => Math.max(1, ...yearRows.value.map((r) => r.count)));
const yearPoints = computed(() => {
  const rows = yearRows.value;
  if (!rows.length) return "";
  const w = 560, h = 120, pad = 8;
  const step = rows.length > 1 ? (w - pad * 2) / (rows.length - 1) : 0;
  return rows
    .map((r, i) => `${(pad + i * step).toFixed(1)},${(h - pad - (r.count / yearMax.value) * (h - pad * 2)).toFixed(1)}`)
    .join(" ");
});
/** X-axis ticks every 10 years between the first and last year. */
const yearTicks = computed(() => {
  const rows = yearRows.value;
  if (!rows.length) return [];
  const first = rows[0].year;
  const last = rows[rows.length - 1].year;
  const start = Math.floor(first / 10) * 10;
  const ticks: number[] = [];
  for (let y = start; y <= last; y += 10) ticks.push(y);
  return ticks;
});
const yearTickX = (year: number) => {
  const rows = yearRows.value;
  const first = rows[0].year, last = rows[rows.length - 1].year;
  const w = 560, pad = 8;
  if (last === first) return w / 2;
  return pad + ((year - first) / (last - first)) * (w - pad * 2);
};

// ── Formats (donut) ─────────────────────────────────────────────────────────
const formatRows = computed(() => {
  const map = new Map<string, number>();
  for (const t of tracks.value) {
    const f = (t.format ?? "?").toUpperCase();
    map.set(f, (map.get(f) ?? 0) + 1);
  }
  return [...map.entries()].map(([format, count]) => ({ format, count })).sort((a, b) => b.count - a.count);
});
const formatTotal = computed(() => formatRows.value.reduce((a, r) => a + r.count, 0));

// ── Donut chart helper (SVG) ────────────────────────────────────────────────
const DONUT_COLORS = ["#5b7c32", "#4a7fc1", "#7d63b8", "#b97a2a", "#b84a4a", "#3d8f85", "#a8a29e", "#57534e"];
function donutSegments(rows: { count: number }[], total: number) {
  const r = 40, c = 2 * Math.PI * r;
  let offset = 0;
  return rows.map((row, i) => {
    const frac = total > 0 ? row.count / total : 0;
    const seg = { color: DONUT_COLORS[i % DONUT_COLORS.length], dash: `${(frac * c).toFixed(2)} ${(c - frac * c).toFixed(2)}`, offset: (-offset * c).toFixed(2) };
    offset += frac;
    return seg;
  });
}
const genreSegments = computed(() => donutSegments(genreTop.value, genreTotal.value));
const formatSegments = computed(() => donutSegments(formatRows.value, formatTotal.value));
</script>

<template>
  <div class="app-main-scroll">
    <div class="view-pad">
      <div class="mb-4 flex flex-wrap items-center gap-3">
        <h1 class="view-title">Stats</h1>
        <span class="view-subtitle">Library overview</span>
      </div>

      <div class="mb-6 grid gap-3" style="grid-template-columns: repeat(auto-fit, minmax(150px, 1fr))">
        <div v-for="card in [
            { label: 'Tracks', value: stats?.track_count ?? tracks.length },
            { label: 'Artists', value: stats?.artist_count ?? '—' },
            { label: 'Albums', value: stats?.album_count ?? '—' },
            { label: 'Total time', value: stats ? fmtDuration(stats.total_duration_secs) : '—' },
          ]" :key="card.label" class="rounded-2xl bg-surface p-4">
          <div class="text-2xl font-bold text-on-surface">{{ card.value }}</div>
          <div class="text-xs text-on-surface-variant">{{ card.label }}</div>
        </div>
      </div>

      <!-- Listening -->
      <section class="mb-8">
        <div class="mb-3 flex items-center gap-3">
          <h2 class="text-[15px] font-bold text-on-background">Listening</h2>
          <div class="ml-auto">
            <PrettySelect
              :model-value="String(days)"
              :options="RANGES"
              @update:model-value="days = Number($event)"
            />
          </div>
        </div>

        <div v-if="summary" class="mb-4 grid gap-3" style="grid-template-columns: repeat(auto-fit, minmax(130px, 1fr))">
          <div class="rounded-2xl bg-surface p-3.5">
            <div class="text-xl font-bold text-on-surface">{{ summary.plays }}</div>
            <div class="text-xs text-on-surface-variant">plays</div>
          </div>
          <div class="rounded-2xl bg-surface p-3.5">
            <div class="text-xl font-bold text-on-surface">{{ fmtDuration(summary.listening_secs) }}</div>
            <div class="text-xs text-on-surface-variant">listened</div>
          </div>
          <div class="rounded-2xl bg-surface p-3.5">
            <div class="text-xl font-bold text-on-surface">{{ summary.unique_tracks }}</div>
            <div class="text-xs text-on-surface-variant">tracks</div>
          </div>
          <div class="rounded-2xl bg-surface p-3.5">
            <div class="text-xl font-bold text-on-surface">{{ summary.unique_artists }}</div>
            <div class="text-xs text-on-surface-variant">artists</div>
          </div>
          <div class="rounded-2xl bg-surface p-3.5">
            <div class="text-xl font-bold text-on-surface">{{ summary.unique_albums }}</div>
            <div class="text-xs text-on-surface-variant">albums</div>
          </div>
        </div>

        <div class="mb-4 grid gap-3 lg:grid-cols-3">
          <div class="rounded-2xl bg-surface p-4">
            <h3 class="mb-2 text-xs font-bold uppercase tracking-wider text-on-surface-variant">Top tracks</h3>
            <div v-for="(t, i) in topTracks" :key="t.track_id ?? i" class="flex items-center gap-2 py-1">
              <span class="w-5 shrink-0 text-right text-xs tabular-nums text-on-surface-variant">{{ i + 1 }}</span>
              <span class="min-w-0 flex-1 truncate text-[13px] text-on-surface">
                {{ t.title ?? "—" }}<template v-if="t.artist"> · {{ t.artist }}</template>
              </span>
              <span class="shrink-0 text-xs tabular-nums text-on-surface-variant">{{ fmtDuration(t.listening_secs) }}</span>
            </div>
            <p v-if="!topTracks.length" class="py-2 text-xs text-on-surface-variant">No plays in this range yet.</p>
          </div>
          <div class="rounded-2xl bg-surface p-4">
            <h3 class="mb-2 text-xs font-bold uppercase tracking-wider text-on-surface-variant">Top artists</h3>
            <div v-for="(a, i) in topArtists" :key="a.artist + i" class="flex items-center gap-2 py-1">
              <span class="w-5 shrink-0 text-right text-xs tabular-nums text-on-surface-variant">{{ i + 1 }}</span>
              <span class="min-w-0 flex-1 truncate text-[13px] text-on-surface">{{ a.artist }}</span>
              <span class="shrink-0 text-xs tabular-nums text-on-surface-variant">{{ fmtDuration(a.listening_secs) }}</span>
            </div>
            <p v-if="!topArtists.length" class="py-2 text-xs text-on-surface-variant">No plays in this range yet.</p>
          </div>
          <div class="rounded-2xl bg-surface p-4">
            <h3 class="mb-2 text-xs font-bold uppercase tracking-wider text-on-surface-variant">Top albums</h3>
            <div v-for="(a, i) in topAlbums" :key="(a.album ?? '') + i" class="flex items-center gap-2 py-1">
              <span class="w-5 shrink-0 text-right text-xs tabular-nums text-on-surface-variant">{{ i + 1 }}</span>
              <span class="min-w-0 flex-1 truncate text-[13px] text-on-surface">
                {{ a.album ?? "—" }}<template v-if="a.album_artist"> · {{ a.album_artist }}</template>
              </span>
              <span class="shrink-0 text-xs tabular-nums text-on-surface-variant">{{ fmtDuration(a.listening_secs) }}</span>
            </div>
            <p v-if="!topAlbums.length" class="py-2 text-xs text-on-surface-variant">No plays in this range yet.</p>
          </div>
        </div>

        <div class="grid gap-3 lg:grid-cols-3">
          <!-- Genre distribution donut -->
          <div class="rounded-2xl bg-surface p-4">
            <h3 class="mb-2 text-xs font-bold uppercase tracking-wider text-on-surface-variant">Genre distribution</h3>
            <div class="flex items-center gap-4">
              <svg viewBox="0 0 100 100" class="h-28 w-28 shrink-0 -rotate-90">
                <circle cx="50" cy="50" r="40" fill="none" stroke="var(--surface-variant)" stroke-width="16" />
                <circle
                  v-for="(s, i) in genreSegments"
                  :key="i"
                  cx="50" cy="50" r="40" fill="none"
                  :stroke="s.color" stroke-width="16"
                  :stroke-dasharray="s.dash" :stroke-dashoffset="s.offset"
                />
              </svg>
              <div class="flex min-w-0 flex-1 flex-col gap-1">
                <div v-for="(r, i) in genreTop" :key="r.genre" class="flex items-center gap-1.5 text-xs">
                  <span class="h-2.5 w-2.5 shrink-0 rounded-sm" :style="{ background: DONUT_COLORS[i % DONUT_COLORS.length] }" />
                  <span class="min-w-0 flex-1 truncate text-on-surface">{{ r.genre }}</span>
                  <span class="shrink-0 tabular-nums text-on-surface-variant">{{ r.count }}</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Tracks per release year (line chart) -->
          <div class="rounded-2xl bg-surface p-4">
            <h3 class="mb-2 text-xs font-bold uppercase tracking-wider text-on-surface-variant">Tracks per release year</h3>
            <svg v-if="yearRows.length" viewBox="0 0 560 132" class="h-28 w-full" preserveAspectRatio="none">
              <polyline :points="yearPoints" fill="none" stroke="var(--primary)" stroke-width="2.5" stroke-linejoin="round" />
              <line v-for="t in yearTicks" :key="t" :x1="yearTickX(t)" :x2="yearTickX(t)" y1="120" y2="126" stroke="var(--outline)" stroke-width="1" />
              <text
                v-for="t in yearTicks"
                :key="`t${t}`"
                :x="yearTickX(t)" y="131"
                text-anchor="middle"
                class="fill-on-surface-variant"
                style="font-size: 9px"
              >{{ t }}</text>
            </svg>
            <p v-else class="py-2 text-xs text-on-surface-variant">No year tags.</p>
          </div>

          <!-- Formats donut -->
          <div class="rounded-2xl bg-surface p-4">
            <h3 class="mb-2 text-xs font-bold uppercase tracking-wider text-on-surface-variant">Formats</h3>
            <div class="flex items-center gap-4">
              <svg viewBox="0 0 100 100" class="h-28 w-28 shrink-0 -rotate-90">
                <circle cx="50" cy="50" r="40" fill="none" stroke="var(--surface-variant)" stroke-width="16" />
                <circle
                  v-for="(s, i) in formatSegments"
                  :key="i"
                  cx="50" cy="50" r="40" fill="none"
                  :stroke="s.color" stroke-width="16"
                  :stroke-dasharray="s.dash" :stroke-dashoffset="s.offset"
                />
              </svg>
              <div class="flex min-w-0 flex-1 flex-col gap-1">
                <div v-for="(r, i) in formatRows" :key="r.format" class="flex items-center gap-1.5 text-xs">
                  <span class="h-2.5 w-2.5 shrink-0 rounded-sm" :style="{ background: DONUT_COLORS[i % DONUT_COLORS.length] }" />
                  <span class="min-w-0 flex-1 truncate text-on-surface">{{ r.format }}</span>
                  <span class="shrink-0 tabular-nums text-on-surface-variant">{{ r.count }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>

      <h2 class="mb-3 mt-8 text-[15px] font-bold text-on-background">Top genres</h2>
      <div class="flex max-w-2xl flex-col gap-2">
        <div
          v-for="row in genreRows.slice(0, 10)"
          :key="row.genre"
          class="grid items-center gap-3"
          style="grid-template-columns: 150px 1fr 44px"
        >
          <span class="truncate text-xs text-on-surface-variant">{{ row.genre }}</span>
          <span class="h-2 overflow-hidden rounded-full bg-surface-variant">
            <span
              class="block h-full rounded-full"
              :style="{ width: `${(row.count / (genreRows[0]?.count ?? 1)) * 100}%`, background: 'var(--primary)' }"
            />
          </span>
          <span class="text-right text-xs text-on-surface-variant">{{ row.count }}</span>
        </div>
      </div>
    </div>
  </div>
</template>
