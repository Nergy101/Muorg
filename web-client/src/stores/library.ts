import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { getTracks, getStats, getCoverBlob, issueStreamToken, recordPlay } from "../api/catalog";
import { streamUrl } from "../api/client";
import type {
  CatalogTrack,
  LibraryStats,
  AlbumGridItem,
  ViewMode,
  GridSortBy,
  TableSortCol,
  SortDir,
  GroupBy,
  TableRow,
  TableGroupRow,
} from "../types";

const MAX_COVER_CONCURRENT = 8;

function loadPref<T>(key: string, fallback: T): T {
  try {
    const v = localStorage.getItem(key);
    return v != null ? (JSON.parse(v) as T) : fallback;
  } catch {
    return fallback;
  }
}

function savePref(key: string, value: unknown): void {
  localStorage.setItem(key, JSON.stringify(value));
}

function normalize(s: string | null | undefined): string {
  return (s ?? "").toLowerCase();
}

function formatDuration(secs: number): string {
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  const s = Math.floor(secs % 60);
  if (h > 0) return `${h}:${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`;
  return `${m}:${String(s).padStart(2, "0")}`;
}

export { formatDuration };

export const useLibraryStore = defineStore("library", () => {
  // --- State ---
  const tracks = ref<CatalogTrack[]>([]);
  const stats = ref<LibraryStats | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);

  const viewMode = ref<ViewMode>(loadPref("muorg-web-view", "grid"));
  const tableArtSize = ref<"small" | "large">(loadPref("muorg-web-art-size", "small"));
  const gridSortBy = ref<GridSortBy>(loadPref("muorg-web-grid-sort", "album"));
  const tableSortCol = ref<TableSortCol | null>(loadPref<TableSortCol | null>("muorg-web-table-col", null));
  const tableSortDir = ref<SortDir>(loadPref("muorg-web-table-dir", "asc"));
  const groupBy = ref<GroupBy>(loadPref("muorg-web-group", "album"));
  const searchQuery = ref("");
  const collapsedGroups = ref<Set<string>>(new Set());
  const selectedTrackIds = ref<Set<number>>(new Set());

  // Playlist filter (set by playlist store)
  const playlistTrackIds = ref<number[] | null>(null);

  // Set when the user asks to reveal a track in the main view
  const revealTrackId = ref<number | null>(null);

  // Playback
  const nowPlaying = ref<CatalogTrack | null>(null);
  const isPlaying = ref(false);
  const currentTimeSecs = ref(0);
  const durationSecs = ref(0);
  const volume = ref(loadPref("muorg-web-volume", 1));
  const audioEl = ref<HTMLAudioElement | null>(null);
  const streamToken = ref<string | null>(null);

  // Cover cache: trackId -> object URL
  const coverCache = ref<Map<number, string>>(new Map());
  const coverPending = ref<Set<number>>(new Set());
  let inFlight = 0;
  const coverQueue: number[] = [];

  // --- Computed ---
  const filteredTracks = computed(() => {
    let result = tracks.value;

    // Playlist filter
    if (playlistTrackIds.value !== null) {
      const ids = new Set(playlistTrackIds.value);
      result = result.filter((t) => ids.has(t.id));
    }

    // Search filter
    const q = searchQuery.value.trim().toLowerCase();
    if (q.length >= 2) {
      result = result.filter(
        (t) =>
          normalize(t.title).includes(q) ||
          normalize(t.artist).includes(q) ||
          normalize(t.album).includes(q) ||
          normalize(t.album_artist).includes(q),
      );
    }

    return result;
  });

  const albumGridItems = computed((): AlbumGridItem[] => {
    const map = new Map<string, AlbumGridItem>();

    for (const t of filteredTracks.value) {
      const albumName = t.album ?? "Unknown Album";
      const albumArtist = t.album_artist ?? t.artist ?? "Unknown Artist";
      const key = `${albumName.toLowerCase()}|||${albumArtist.toLowerCase()}`;

      let item = map.get(key);
      if (!item) {
        item = {
          key,
          album: albumName,
          albumArtist,
          year: null,
          trackCount: 0,
          totalDurationSecs: 0,
          coverTrackId: t.has_cover ? t.id : null,
          hasCover: t.has_cover,
          trackIds: [],
        };
        map.set(key, item);
      }

      item.trackCount++;
      item.totalDurationSecs += t.duration_secs ?? 0;
      item.trackIds.push(t.id);
      if (t.year && (item.year === null || t.year < item.year)) item.year = t.year;
      if (t.has_cover && !item.hasCover) {
        item.hasCover = true;
        item.coverTrackId = t.id;
      }
    }

    const items = Array.from(map.values());

    switch (gridSortBy.value) {
      case "artist":
        items.sort(
          (a, b) =>
            a.albumArtist.toLowerCase().localeCompare(b.albumArtist.toLowerCase()) ||
            a.album.toLowerCase().localeCompare(b.album.toLowerCase()),
        );
        break;
      case "year":
        items.sort(
          (a, b) =>
            (a.year ?? 0) - (b.year ?? 0) ||
            a.album.toLowerCase().localeCompare(b.album.toLowerCase()),
        );
        break;
      default:
        items.sort(
          (a, b) =>
            a.album.toLowerCase().localeCompare(b.album.toLowerCase()) ||
            a.albumArtist.toLowerCase().localeCompare(b.albumArtist.toLowerCase()),
        );
    }

    return items;
  });

  const tableRows = computed((): TableRow[] => {
    const sorted = [...filteredTracks.value];

    // Sort tracks — null means default natural order (artist → album → disc → track)
    sorted.sort((a, b) => {
      if (tableSortCol.value === null) {
        const ac = normalize(a.artist ?? a.album_artist).localeCompare(normalize(b.artist ?? b.album_artist));
        if (ac !== 0) return ac;
        const alc = normalize(a.album).localeCompare(normalize(b.album));
        if (alc !== 0) return alc;
        const dc = (a.disc_number ?? 1) - (b.disc_number ?? 1);
        if (dc !== 0) return dc;
        return (a.track_number ?? 0) - (b.track_number ?? 0);
      }
      let cmp = 0;
      switch (tableSortCol.value) {
        case "title":
          cmp = normalize(a.title).localeCompare(normalize(b.title));
          break;
        case "artist":
          cmp = normalize(a.artist ?? a.album_artist).localeCompare(
            normalize(b.artist ?? b.album_artist),
          );
          break;
        case "album":
          cmp = normalize(a.album).localeCompare(normalize(b.album));
          break;
        case "year":
          cmp = (a.year ?? 0) - (b.year ?? 0);
          break;
        case "duration":
          cmp = (a.duration_secs ?? 0) - (b.duration_secs ?? 0);
          break;
      }
      return tableSortDir.value === "asc" ? cmp : -cmp;
    });

    if (groupBy.value === "none") {
      return sorted.map((t) => ({ type: "track" as const, track: t, groupKey: "" }));
    }

    // Group
    const groups = new Map<
      string,
      {
        key: string;
        label: string;
        coverTrackId: number | null;
        hasCover: boolean;
        tracks: CatalogTrack[];
        year: number | null;
      }
    >();

    for (const t of sorted) {
      let key: string;
      let label: string;
      if (groupBy.value === "album") {
        const album = t.album ?? "Unknown Album";
        const artist = t.album_artist ?? t.artist ?? "Unknown Artist";
        key = `${album.toLowerCase()}|||${artist.toLowerCase()}`;
        label = album;
      } else {
        key = normalize(t.artist ?? t.album_artist ?? "Unknown Artist");
        label = t.artist ?? t.album_artist ?? "Unknown Artist";
      }

      let g = groups.get(key);
      if (!g) {
        g = { key, label, coverTrackId: null, hasCover: false, tracks: [], year: null };
        groups.set(key, g);
      }
      g.tracks.push(t);
      if (t.has_cover && !g.hasCover) {
        g.hasCover = true;
        g.coverTrackId = t.id;
      }
      if (t.year && (g.year === null || t.year < g.year)) g.year = t.year;
    }

    const rows: TableRow[] = [];
    for (const g of groups.values()) {
      // Within album groups always sort by disc + track number
      if (groupBy.value === "album") {
        g.tracks.sort((a, b) => {
          const dc = (a.disc_number ?? 1) - (b.disc_number ?? 1);
          if (dc !== 0) return dc;
          return (a.track_number ?? 0) - (b.track_number ?? 0);
        });
      }
      const totalDur = g.tracks.reduce((s, t) => s + (t.duration_secs ?? 0), 0);
      rows.push({
        type: "group",
        key: g.key,
        label: g.label,
        coverTrackId: g.coverTrackId,
        hasCover: g.hasCover,
        trackCount: g.tracks.length,
        totalDurationSecs: totalDur,
        year: g.year,
        collapsed: collapsedGroups.value.has(g.key),
      });
      if (!collapsedGroups.value.has(g.key)) {
        for (const t of g.tracks) {
          rows.push({ type: "track", track: t, groupKey: g.key });
        }
      }
    }
    return rows;
  });

  // --- Actions ---
  async function loadLibrary(): Promise<void> {
    loading.value = true;
    error.value = null;
    try {
      const [t, s] = await Promise.all([getTracks(), getStats()]);
      tracks.value = t;
      stats.value = s;
    } catch (e) {
      error.value = (e as Error).message;
    } finally {
      loading.value = false;
    }
  }

  function setViewMode(m: ViewMode): void {
    viewMode.value = m;
    savePref("muorg-web-view", m);
  }

  function setTableArtSize(s: "small" | "large"): void {
    tableArtSize.value = s;
    savePref("muorg-web-art-size", s);
  }

  function setGridSortBy(s: GridSortBy): void {
    gridSortBy.value = s;
    savePref("muorg-web-grid-sort", s);
  }

  function setTableSort(col: TableSortCol): void {
    if (tableSortCol.value === col) {
      if (tableSortDir.value === "asc") {
        tableSortDir.value = "desc";
        savePref("muorg-web-table-dir", "desc");
      } else {
        tableSortCol.value = null;
        savePref("muorg-web-table-col", null);
      }
    } else {
      tableSortCol.value = col;
      tableSortDir.value = "asc";
      savePref("muorg-web-table-col", col);
      savePref("muorg-web-table-dir", "asc");
    }
  }

  function revealTrack(track: CatalogTrack): void {
    // Clear playlist filter so the track is guaranteed to be visible
    playlistTrackIds.value = null;
    searchQuery.value = "";
    // If grouped by album, make sure the group is expanded
    if (groupBy.value === "album") {
      const album = (track.album ?? "Unknown Album").toLowerCase();
      const artist = (track.album_artist ?? track.artist ?? "Unknown Artist").toLowerCase();
      const key = `${album}|||${artist}`;
      collapsedGroups.value.delete(key);
      collapsedGroups.value = new Set(collapsedGroups.value);
    } else if (groupBy.value === "artist") {
      const key = (track.artist ?? track.album_artist ?? "Unknown Artist").toLowerCase();
      collapsedGroups.value.delete(key);
      collapsedGroups.value = new Set(collapsedGroups.value);
    }
    // Signal listeners to scroll to this track
    revealTrackId.value = null;
    setTimeout(() => { revealTrackId.value = track.id; }, 0);
  }

  function setGroupBy(g: GroupBy): void {
    groupBy.value = g;
    savePref("muorg-web-group", g);
    collapsedGroups.value.clear();
  }

  function toggleGroup(key: string): void {
    if (collapsedGroups.value.has(key)) {
      collapsedGroups.value.delete(key);
    } else {
      collapsedGroups.value.add(key);
    }
    collapsedGroups.value = new Set(collapsedGroups.value);
  }

  function collapseAllGroups(): void {
    const keys = tableRows.value
      .filter((r): r is TableGroupRow => r.type === "group")
      .map((r) => r.key);
    collapsedGroups.value = new Set(keys);
  }

  function expandAllGroups(): void {
    collapsedGroups.value = new Set();
  }

  function toggleTrackSelection(id: number, multi = false): void {
    if (!multi) {
      selectedTrackIds.value = new Set([id]);
      return;
    }
    const next = new Set(selectedTrackIds.value);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    selectedTrackIds.value = next;
  }

  function clearSelection(): void {
    selectedTrackIds.value = new Set();
  }

  // Cover art
  function requestCover(trackId: number): void {
    if (coverCache.value.has(trackId) || coverPending.value.has(trackId)) return;
    coverPending.value.add(trackId);
    coverQueue.unshift(trackId);
    drainCoverQueue();
  }

  function drainCoverQueue(): void {
    while (inFlight < MAX_COVER_CONCURRENT && coverQueue.length > 0) {
      const id = coverQueue.shift()!;
      if (coverCache.value.has(id)) {
        coverPending.value.delete(id);
        continue;
      }
      inFlight++;
      getCoverBlob(id)
        .then((blob) => {
          if (blob) {
            coverCache.value.set(id, URL.createObjectURL(blob));
            coverCache.value = new Map(coverCache.value);
          }
        })
        .finally(() => {
          coverPending.value.delete(id);
          inFlight--;
          drainCoverQueue();
        });
    }
  }

  // Playback
  const trackEndedCallbacks: (() => void)[] = [];

  function onTrackEnded(cb: () => void): () => void {
    trackEndedCallbacks.push(cb);
    return () => {
      const idx = trackEndedCallbacks.indexOf(cb);
      if (idx >= 0) trackEndedCallbacks.splice(idx, 1);
    };
  }

  function initAudio(): HTMLAudioElement {
    if (audioEl.value) return audioEl.value;
    const el = new Audio();
    el.addEventListener("timeupdate", () => {
      currentTimeSecs.value = el.currentTime;
    });
    el.addEventListener("durationchange", () => {
      durationSecs.value = Number.isFinite(el.duration) ? el.duration : (nowPlaying.value?.duration_secs ?? 0);
    });
    el.addEventListener("ended", () => {
      isPlaying.value = false;
      currentTimeSecs.value = 0;
      for (const cb of trackEndedCallbacks) cb();
    });
    el.addEventListener("play", () => (isPlaying.value = true));
    el.addEventListener("pause", () => (isPlaying.value = false));
    el.volume = volume.value;
    audioEl.value = el;
    return el;
  }

  async function playTrack(track: CatalogTrack, startSecs?: number): Promise<void> {
    if (nowPlaying.value?.id === track.id && startSecs === undefined) return;
    try {
      const token = await issueStreamToken(track.id);
      streamToken.value = token;
      const url = streamUrl(track.id, token, startSecs);
      const el = initAudio();
      el.src = url;
      await el.play();
      nowPlaying.value = track;
      currentTimeSecs.value = startSecs ?? 0;
      durationSecs.value = track.duration_secs ?? 0;
      recordPlay(track.id);
    } catch (e) {
      error.value = (e as Error).message;
    }
  }

  async function playAlbum(item: AlbumGridItem): Promise<void> {
    const first = tracks.value.find((t) => t.id === item.trackIds[0]);
    if (first) await playTrack(first);
  }

  function togglePlayPause(): void {
    const el = audioEl.value;
    if (!el) return;
    if (el.paused) el.play().catch(() => null);
    else el.pause();
  }

  async function seekTo(secs: number): Promise<void> {
    if (!nowPlaying.value || !streamToken.value) return;
    // Stream endpoint supports ?start= so we reload from seek point
    const url = streamUrl(nowPlaying.value.id, streamToken.value, secs);
    const el = initAudio();
    el.src = url;
    currentTimeSecs.value = secs;
    await el.play().catch(() => null);
  }

  function setVolume(v: number): void {
    volume.value = v;
    savePref("muorg-web-volume", v);
    if (audioEl.value) audioEl.value.volume = v;
  }

  return {
    tracks,
    stats,
    loading,
    error,
    viewMode,
    tableArtSize,
    gridSortBy,
    tableSortCol,
    tableSortDir,
    groupBy,
    searchQuery,
    collapsedGroups,
    selectedTrackIds,
    playlistTrackIds,
    revealTrackId,
    nowPlaying,
    isPlaying,
    currentTimeSecs,
    durationSecs,
    volume,
    coverCache,
    filteredTracks,
    albumGridItems,
    tableRows,
    loadLibrary,
    setViewMode,
    setTableArtSize,
    setGridSortBy,
    setTableSort,
    setGroupBy,
    toggleGroup,
    revealTrack,
    collapseAllGroups,
    expandAllGroups,
    toggleTrackSelection,
    clearSelection,
    requestCover,
    onTrackEnded,
    playTrack,
    playAlbum,
    togglePlayPause,
    seekTo,
    setVolume,
  };
});
