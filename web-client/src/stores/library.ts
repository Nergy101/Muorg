import { defineStore } from "pinia";
import { ref, computed } from "vue";
import { getTracks, getStats, getCoverBlob, issueStreamToken, recordPlay, batchSetRating as apiBatchSetRating } from "../api/catalog";
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
  // Progressive loading: total known from X-Total-Count, pages filled in background
  const totalTracks = ref(0);
  const loadingMore = ref(false);

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
  // For FLAC seeks: el.currentTime resets to 0 after stream reload, so we
  // track how many seconds were skipped to get the real wall-clock position.
  let flacSeekOffset = 0;
  let _seekSeq = 0;

  // Play queue
  const playQueue = ref<CatalogTrack[]>([]);
  const queueIndex = ref(-1);

  // Cover cache: trackId -> object URL
  const coverCache = ref<Map<number, string>>(new Map());
  const coverPending = ref<Set<number>>(new Set());
  let inFlight = 0;
  const coverQueue: number[] = [];

  // --- Computed ---
  const filteredTracks = computed(() => {
    let result = tracks.value;

    // Playlist filter (preserves playlist order)
    if (playlistTrackIds.value !== null) {
      const order = playlistTrackIds.value;
      const byId = new Map<number, CatalogTrack>();
      for (const t of result) byId.set(t.id, t);
      result = order.map((id) => byId.get(id)).filter((t): t is CatalogTrack => t !== undefined);
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
  const PAGE_SIZE = 500;

  async function loadLibrary(): Promise<void> {
    loading.value = true;
    error.value = null;
    try {
      const first = await getTracks(0, PAGE_SIZE);
      tracks.value = first.tracks;
      totalTracks.value = first.total;
      if (first.total > first.tracks.length) {
        // Fill remaining pages in the background — the UI stays usable immediately
        void loadRemainingPages(first.tracks.length);
      }
      stats.value = await getStats();
    } catch (e) {
      error.value = (e as Error).message;
    } finally {
      loading.value = false;
    }
  }

  async function loadRemainingPages(start: number): Promise<void> {
    if (loadingMore.value) return;
    loadingMore.value = true;
    try {
      const seen = new Set(tracks.value.map((t) => t.id));
      for (let offset = start; offset < totalTracks.value; offset += PAGE_SIZE) {
        const page = await getTracks(offset, PAGE_SIZE);
        totalTracks.value = page.total;
        const fresh = page.tracks.filter((t) => !seen.has(t.id));
        for (const t of fresh) seen.add(t.id);
        if (fresh.length > 0) {
          tracks.value = [...tracks.value, ...fresh];
        }
        // Gentle pacing between pages
        await new Promise((r) => setTimeout(r, 150));
      }
    } catch {
      // Background fill failure is non-fatal — the first page is already usable
    } finally {
      loadingMore.value = false;
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

  function addToQueue(track: CatalogTrack): void {
    playQueue.value = [...playQueue.value, track];
  }

  function addMultipleToQueue(tracks: CatalogTrack[]): void {
    playQueue.value = [...playQueue.value, ...tracks];
  }

  /** Insert a track right after the currently playing one. */
  function playNextTrack(track: CatalogTrack): void {
    const idx = queueIndex.value >= 0 ? queueIndex.value : 0;
    const next = [...playQueue.value];
    next.splice(idx + 1, 0, track);
    playQueue.value = next;
  }

  /** The track right after the current one in the queue, if any. */
  function nextQueuedTrack(): CatalogTrack | null {
    if (playQueue.value.length === 0 || queueIndex.value < 0) return null;
    if (queueIndex.value + 1 < playQueue.value.length) {
      return playQueue.value[queueIndex.value + 1];
    }
    return null;
  }

  /** The track right before the current one in the queue, if any. */
  function prevQueuedTrack(): CatalogTrack | null {
    if (queueIndex.value > 0 && queueIndex.value < playQueue.value.length) {
      return playQueue.value[queueIndex.value - 1];
    }
    return null;
  }

  /** Advance to the next queued track. Returns false at the end of the queue. */
  function autoAdvanceQueue(): boolean {
    const next = nextQueuedTrack();
    if (next) {
      queueIndex.value++;
      playTrack(next).catch(() => {});
      return true;
    }
    return false;
  }

  /** Randomize everything after the current track (shuffle on). */
  function shuffleRemainingQueue(): void {
    const start = Math.max(0, queueIndex.value + 1);
    const rest = playQueue.value.slice(start);
    for (let i = rest.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [rest[i], rest[j]] = [rest[j], rest[i]];
    }
    playQueue.value = [...playQueue.value.slice(0, start), ...rest];
  }

  function removeFromQueue(index: number): void {
    const next = [...playQueue.value];
    next.splice(index, 1);
    playQueue.value = next;
    if (index <= queueIndex.value) queueIndex.value--;
  }

  function clearQueue(): void {
    playQueue.value = [];
    queueIndex.value = -1;
  }

  function setQueueIndex(index: number): void {
    queueIndex.value = index;
  }

  function initAudio(): HTMLAudioElement {
    if (audioEl.value) return audioEl.value;
    const el = new Audio();
    el.addEventListener("timeupdate", () => {
      currentTimeSecs.value = el.currentTime + flacSeekOffset;
    });
    el.addEventListener("durationchange", () => {
      if (Number.isFinite(el.duration)) {
        durationSecs.value = el.duration + flacSeekOffset;
      } else {
        durationSecs.value = nowPlaying.value?.duration_secs ?? 0;
      }
    });
    el.addEventListener("ended", () => {
      isPlaying.value = false;
      currentTimeSecs.value = 0;
      for (const cb of trackEndedCallbacks) cb();
    });
    el.addEventListener("play", () => (isPlaying.value = true));
    el.addEventListener("pause", () => (isPlaying.value = false));
    el.volume = volume.value;
    // Must be in the DOM for iOS Now Playing widget to register
    el.style.display = "none";
    document.body.appendChild(el);
    audioEl.value = el;
    return el;
  }

  async function playTrack(track: CatalogTrack, startSecs?: number): Promise<void> {
    if (nowPlaying.value?.id === track.id && startSecs === undefined) return;
    // Queue bookkeeping: if the track is already queued, jump to it; otherwise
    // start a fresh one-track queue.
    const existingIdx = playQueue.value.findIndex((t) => t.id === track.id);
    if (existingIdx >= 0 && queueIndex.value >= 0) {
      queueIndex.value = existingIdx;
    } else {
      playQueue.value = [track];
      queueIndex.value = 0;
    }
    try {
      const token = await issueStreamToken(track.id);
      streamToken.value = token;
      const url = streamUrl(track.id, token, startSecs);
      const el = initAudio();
      flacSeekOffset = 0;
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
    const ordered = tracks.value
      .filter((t) => item.trackIds.includes(t.id))
      .sort((a, b) => {
        const discA = a.disc_number ?? 1;
        const discB = b.disc_number ?? 1;
        if (discA !== discB) return discA - discB;
        return (a.track_number ?? 0) - (b.track_number ?? 0);
      });
    if (ordered.length === 0) return;
    playQueue.value = ordered;
    queueIndex.value = 0;
    await playTrack(ordered[0]);
  }

  function togglePlayPause(): void {
    const el = audioEl.value;
    if (!el) return;
    if (el.paused) el.play().catch(() => null);
    else el.pause();
  }

  async function seekTo(secs: number): Promise<void> {
    if (!nowPlaying.value) return;
    const el = audioEl.value;
    if (!el) return;

    const seq = ++_seekSeq;
    const wasPlaying = !el.paused;

    if (nowPlaying.value.format === 'flac') {
      // FLAC: server transcodes from ?start=, so we must reload the stream.
      // Issue a fresh token — the original expires 60s after track start.
      el.pause();
      flacSeekOffset = secs;
      el.src = '';
      try {
        const token = await issueStreamToken(nowPlaying.value.id);
        if (seq !== _seekSeq) return;
        streamToken.value = token;
        el.src = streamUrl(nowPlaying.value.id, token, secs);
        el.load();
        currentTimeSecs.value = secs;
        if (wasPlaying) {
          el.play().catch(() => {
            el.addEventListener('canplay', () => {
              if (seq === _seekSeq) el.play().catch(() => null);
            }, { once: true });
          });
        }
      } catch {
        // seek aborted or token fetch failed
      }
    } else {
      // MP3: server supports Range requests so the browser can seek natively.
      // ?start= is ignored for MP3 on the server — currentTime is the right approach.
      currentTimeSecs.value = secs;
      el.currentTime = secs;
      if (wasPlaying) {
        el.play().catch(() => {
          el.addEventListener('canplay', () => {
            if (seq === _seekSeq) el.play().catch(() => null);
          }, { once: true });
        });
      }
    }
  }

  function setVolume(v: number): void {
    volume.value = v;
    savePref("muorg-web-volume", v);
    if (audioEl.value) audioEl.value.volume = v;
  }

  async function batchSetRating(trackIds: number[], rating: number | null): Promise<void> {
    await apiBatchSetRating(trackIds, rating);
    // Reload the library to reflect updated ratings
    await loadLibrary();
  }

  return {
    tracks,
    stats,
    loading,
    error,
    totalTracks,
    loadingMore,
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
    playQueue,
    queueIndex,
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
    batchSetRating,
    addToQueue,
    addMultipleToQueue,
    playNextTrack,
    nextQueuedTrack,
    prevQueuedTrack,
    autoAdvanceQueue,
    shuffleRemainingQueue,
    removeFromQueue,
    clearQueue,
    setQueueIndex,
  };
});
