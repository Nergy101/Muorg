<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import type { MetadataUpdate, TrackMetadataRead, TrackBackupRecord } from "../../types";
import { extractBestFromPath, PATH_FIELD_MAP, buildUpdateFromExtracted } from "../../utils/pathFormat";
import { readFile } from "@tauri-apps/plugin-fs";
import * as catalogApi from "../../api/catalog";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import StarRating from "../shared/StarRating.vue";
import { useOverlayScrollbars } from "../../composables/useOverlayScrollbars";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { selectedTracks, openWikipediaModal, pendingCoverImagePath } = storeToRefs(store);
const { hideWikipediaCoverSearch, pathFormatTemplates } = storeToRefs(settingsStore);

const title = ref("");
const artist = ref("");
const album = ref("");
const albumArtist = ref("");
const featuring = ref("");
const year = ref<number | "">("");
const genre = ref("");
const trackNumber = ref<number | "">("");
const discNumber = ref<number | "">("");
const pictureBase64 = ref<string | null>(null);
const clearCoverRequested = ref(false);
const saving = ref(false);
const saveError = ref<string | null>(null);
const fileInputRef = ref<HTMLInputElement | null>(null);
const showCoverPopup = ref(false);
const coverPopupRef = ref<HTMLDivElement | null>(null);
const coverDimensions = ref<{ width: number; height: number } | null>(null);
const coverSizeBytes = ref<number | null>(null);
const largeImageWarning = ref(false);

// Genre combobox
const showGenreDropdown = ref(false);
const activeGenreIndex = ref(-1);
const genreScrollRef = ref<HTMLElement | null>(null);
useOverlayScrollbars(genreScrollRef);

const COMMON_GENRES = [
  "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge",
  "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B",
  "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska",
  "Death Metal", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal",
  "Trance", "Classical", "Instrumental", "House", "Gospel", "Soul", "Punk",
  "Electronic", "New Wave", "Psychedelic", "Folk", "Folk-Rock", "Swing",
  "Latin", "Celtic", "Bluegrass", "Progressive Rock", "Gothic Rock",
  "Symphonic Rock", "Big Band", "Easy Listening", "Acoustic", "Opera",
  "Chanson", "Ballad", "Samba", "Tango", "Drum & Bass", "Jungle",
  "Garage", "Hardstep", "Hardcore", "Drum Solo", "A cappella",
  "Euro-House", "Dance Hall",
];

const filteredGenres = computed(() => {
  const q = genre.value.toLowerCase().trim();
  if (!q) return COMMON_GENRES;
  return COMMON_GENRES.filter((g) => g.toLowerCase().includes(q));
});

function selectGenre(g: string) {
  genre.value = g;
  markEdited("genre");
  showGenreDropdown.value = false;
  activeGenreIndex.value = -1;
}

function handleGenreKeydown(e: KeyboardEvent) {
  if (!showGenreDropdown.value || !filteredGenres.value.length) return;
  if (e.key === "ArrowDown") {
    e.preventDefault();
    activeGenreIndex.value = Math.min(activeGenreIndex.value + 1, filteredGenres.value.length - 1);
  } else if (e.key === "ArrowUp") {
    e.preventDefault();
    activeGenreIndex.value = Math.max(activeGenreIndex.value - 1, 0);
  } else if (e.key === "Enter" && activeGenreIndex.value >= 0) {
    e.preventDefault();
    selectGenre(filteredGenres.value[activeGenreIndex.value]);
  } else if (e.key === "Escape") {
    showGenreDropdown.value = false;
    activeGenreIndex.value = -1;
  }
}

const showWikipediaModal = ref(false);
const wikipediaImageUrl = ref<string | null>(null);
const wikipediaSearchLoading = ref(false);
const wikipediaError = ref<string | null>(null);
const wikipediaApplying = ref(false);
const replayGainMeta = ref<TrackMetadataRead | null>(null);
const latestBackup = ref<TrackBackupRecord | null>(null);
const backupPreviewText = ref<string>("");
const backupWouldChangeMetadata = ref(false);

const coverDragOver = ref(false);
const coverDragDepth = ref(0);

// Rating (saved immediately, not via the Save button)
const rating = ref<number | null>(null);

const tooltipPopover = ref<{ text: string; x: number; y: number; position?: "left" | "below" | "above" } | null>(null);
let tooltipHideTimeout: ReturnType<typeof setTimeout> | null = null;

const baseline = ref<{
  title: string;
  artist: string;
  album: string;
  albumArtist: string;
  featuring: string;
  year: number | "";
  genre: string;
  trackNumber: number | "";
  discNumber: number | "";
  pictureBase64: string | null;
} | null>(null);

const editedFields = ref<Set<keyof NonNullable<typeof baseline.value>>>(new Set());
// Fields explicitly cleared via the X button — must always be sent as null on save,
// regardless of whether their value differs from baseline (covers the bulk-mode case
// where an empty field would otherwise be treated as "don't change").
const clearedFields = ref<Set<keyof NonNullable<typeof baseline.value>>>(new Set());

const ONE_MB = 1024 * 1024;

const hasFormChanges = computed(() => {
  const b = baseline.value;
  if (!b) return false;
  return (
    title.value !== b.title ||
    artist.value !== b.artist ||
    album.value !== b.album ||
    albumArtist.value !== b.albumArtist ||
    featuring.value !== b.featuring ||
    year.value !== b.year ||
    genre.value !== b.genre ||
    trackNumber.value !== b.trackNumber ||
    discNumber.value !== b.discNumber ||
    pictureBase64.value !== b.pictureBase64 ||
    clearCoverRequested.value
  );
});

function showTooltip(text: string, e: MouseEvent, position: "left" | "below" | "above" = "below") {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  if (position === "left") {
    tooltipPopover.value = { text, x: rect.left - 8, y: rect.top + rect.height / 2, position: "left" };
  } else if (position === "above") {
    tooltipPopover.value = { text, x: rect.left + rect.width / 2, y: rect.top - 6, position: "above" };
  } else {
    tooltipPopover.value = { text, x: rect.left + rect.width / 2, y: rect.bottom + 6, position: "below" };
  }
}

function scheduleHideTooltip() {
  tooltipHideTimeout = setTimeout(() => {
    tooltipPopover.value = null;
    tooltipHideTimeout = null;
  }, 100);
}

function cancelHideTooltip() {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
}

function hideTooltip() {
  tooltipPopover.value = null;
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
}

function onCoverPopupKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") showCoverPopup.value = false;
}

function onWikipediaModalKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") closeWikipediaModal();
}

const WIKI_API = "https://en.wikipedia.org/w/api.php";

function normalizeFileTitle(title: string): { name: string; ext: string } {
  const withoutPrefix = title.replace(/^File:/i, "").trim();
  const lastDot = withoutPrefix.lastIndexOf(".");
  const name = (lastDot >= 0 ? withoutPrefix.slice(0, lastDot) : withoutPrefix)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "");
  const ext = (lastDot >= 0 ? withoutPrefix.slice(lastDot + 1) : "").toLowerCase();
  return { name, ext };
}

function scoreImageAsAlbumArt(fileTitle: string, albumName: string): number {
  const { name, ext } = normalizeFileTitle(fileTitle);
  const albumNorm = albumName
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "");
  let score = 0;
  if (albumNorm && name.includes(albumNorm)) score += 2;
  if (/cover|albumcover|albumart|albumartwork/i.test(fileTitle)) score += 1;
  if (/album/i.test(fileTitle)) score += 0.5;
  if (/icon|edit|button|star|arrow|progressive|\.svg$/i.test(fileTitle) || ext === "svg") score -= 2;
  if (ext === "svg") score -= 1;
  if (["jpg", "jpeg", "png", "webp"].includes(ext)) score += 0.5;
  return score;
}

function pickBestAlbumImage(imageTitles: { title: string }[], albumName: string): string | null {
  if (!imageTitles?.length) return null;
  const scored = imageTitles
    .filter((img) => img.title.startsWith("File:"))
    .map((img) => ({ title: img.title, score: scoreImageAsAlbumArt(img.title, albumName) }));
  scored.sort((a, b) => b.score - a.score);
  return scored[0]?.title ?? null;
}

async function openFromWikipedia() {
  const albumName = album.value.trim();
  const artistName = artist.value.trim();
  const query = [albumName, artistName].filter(Boolean).join(" ") || albumName;
  if (!query) {
    wikipediaError.value = "Enter an album (or artist) name first.";
    showWikipediaModal.value = true;
    wikipediaImageUrl.value = null;
    return;
  }
  wikipediaError.value = null;
  wikipediaImageUrl.value = null;
  showWikipediaModal.value = true;
  wikipediaSearchLoading.value = true;
  try {
    const searchQuery = albumName ? `${albumName} (album)` : query;
    const searchParams = new URLSearchParams({
      action: "query",
      generator: "search",
      gsrsearch: searchQuery,
      gsrlimit: "5",
      format: "json",
      origin: "*",
    });
    const searchRes = await fetch(`${WIKI_API}?${searchParams}`);
    const searchData = (await searchRes.json()) as {
      query?: { pages?: Record<string, { pageid: number; title: string; index?: number }> };
    };
    const searchPages = searchData?.query?.pages;
    const sortedPages = searchPages
      ? Object.values(searchPages).sort((a, b) => (a.index ?? 99) - (b.index ?? 99))
      : [];
    const firstPage = sortedPages[0];
    if (!firstPage?.pageid) {
      wikipediaError.value = "No Wikipedia page found for this album.";
      return;
    }

    const imagesParams = new URLSearchParams({
      action: "query",
      pageids: String(firstPage.pageid),
      prop: "images",
      format: "json",
      origin: "*",
    });
    const imagesRes = await fetch(`${WIKI_API}?${imagesParams}`);
    const imagesData = (await imagesRes.json()) as {
      query?: { pages?: Record<string, { images?: { title: string }[] }> };
    };
    const pageData = imagesData?.query?.pages?.[String(firstPage.pageid)];
    const images = pageData?.images ?? [];
    const firstImageTitle = pickBestAlbumImage(images, albumName || query);
    if (!firstImageTitle) {
      wikipediaError.value = "No image found on this Wikipedia page.";
      return;
    }

    const imageInfoParams = new URLSearchParams({
      action: "query",
      titles: firstImageTitle,
      prop: "imageinfo",
      iiprop: "url",
      iiurlwidth: "800",
      format: "json",
      origin: "*",
    });
    const imageInfoRes = await fetch(`${WIKI_API}?${imageInfoParams}`);
    const imageInfoData = (await imageInfoRes.json()) as {
      query?: { pages?: Record<string, { imageinfo?: { url: string }[] }> };
    };
    const filePage = imageInfoData?.query?.pages && Object.values(imageInfoData.query.pages)[0];
    const imageUrl = filePage?.imageinfo?.[0]?.url;
    if (imageUrl) {
      wikipediaImageUrl.value = imageUrl;
      wikipediaError.value = null;
    } else {
      wikipediaError.value = "Could not get image URL.";
    }
  } catch (e) {
    wikipediaImageUrl.value = null;
    wikipediaError.value = e instanceof Error ? e.message : "Search failed.";
  } finally {
    wikipediaSearchLoading.value = false;
  }
}

function closeWikipediaModal() {
  showWikipediaModal.value = false;
  wikipediaImageUrl.value = null;
  wikipediaError.value = null;
}

function dataUrlToJpegBase64(dataUrl: string): string {
  const i = dataUrl.indexOf(",");
  return i >= 0 ? dataUrl.slice(i + 1) : dataUrl;
}

function guessImageMimeFromPath(path: string): string {
  const ext = path.split(".").pop()?.toLowerCase() ?? "";
  if (ext === "png") return "image/png";
  if (ext === "webp") return "image/webp";
  if (ext === "gif") return "image/gif";
  if (ext === "bmp") return "image/bmp";
  return "image/jpeg";
}

function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

async function pngDataUrlToJpegBase64(dataUrl: string): Promise<string> {
  return await new Promise<string>((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement("canvas");
      canvas.width = img.naturalWidth;
      canvas.height = img.naturalHeight;
      const ctx = canvas.getContext("2d");
      if (!ctx) {
        reject(new Error("Canvas not supported"));
        return;
      }
      ctx.drawImage(img, 0, 0);
      try {
        resolve(dataUrlToJpegBase64(canvas.toDataURL("image/jpeg", 0.92)));
      } catch (e) {
        reject(e);
      }
    };
    img.onerror = () => reject(new Error("Failed to decode image"));
    img.src = dataUrl;
  });
}

async function applyWikipediaImage() {
  const url = wikipediaImageUrl.value;
  if (!url) return;
  wikipediaApplying.value = true;
  try {
    const result = await catalogApi.fetchImageUrl(url);
    if (!result) throw new Error("No image returned");
    const { base64, mime } = result;
    const normMime = mime.toLowerCase().split(";")[0].trim();
    clearCoverRequested.value = false;
    if (normMime === "image/png") {
      const dataUrl = `data:image/png;base64,${base64}`;
      const jpegBase64 = await new Promise<string>((resolve, reject) => {
        const img = new Image();
        img.onload = () => {
          const canvas = document.createElement("canvas");
          canvas.width = img.naturalWidth;
          canvas.height = img.naturalHeight;
          const ctx = canvas.getContext("2d");
          if (!ctx) {
            reject(new Error("Canvas not supported"));
            return;
          }
          ctx.drawImage(img, 0, 0);
          try {
            resolve(dataUrlToJpegBase64(canvas.toDataURL("image/jpeg", 0.92)));
          } catch (e) {
            reject(e);
          }
        };
        img.onerror = () => reject(new Error("Failed to decode image"));
        img.src = dataUrl;
      });
      pictureBase64.value = jpegBase64;
    } else {
      pictureBase64.value = base64;
    }
    loadCoverMeta(
      pictureBase64.value ? `data:image/jpeg;base64,${pictureBase64.value}` : "",
      undefined,
    );
    markEdited("pictureBase64");
    closeWikipediaModal();
    await save();
  } catch (e) {
    wikipediaError.value = e instanceof Error ? e.message : String(e);
  } finally {
    wikipediaApplying.value = false;
  }
}

function formatSize(bytes: number): string {
  if (bytes >= ONE_MB) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024).toFixed(0)} KB`;
}

function loadCoverMeta(dataUrl: string, sizeBytes?: number) {
  coverDimensions.value = null;
  const base64Part = dataUrl.includes(",") ? dataUrl.split(",")[1] : "";
  coverSizeBytes.value =
    sizeBytes ?? (base64Part ? Math.round((base64Part.length * 3) / 4) : null);
  const img = new Image();
  img.onload = () => {
    coverDimensions.value = { width: img.naturalWidth, height: img.naturalHeight };
  };
  img.src = dataUrl;
}

const displayCover = computed(() => {
  if (clearCoverRequested.value) return null;
  if (pictureBase64.value) return `data:image/jpeg;base64,${pictureBase64.value}`;
  const tracks = selectedTracks.value;
  if (!tracks.length) return null;
  return store.getCoverDataUrl(tracks[0].path);
});

const multiSelectionSharedCover = computed(() => {
  const tracks = selectedTracks.value;
  if (tracks.length <= 1) return false;
  const urls = new Set(
    tracks.map((t) => store.getCoverDataUrl(t.path) ?? null),
  );
  return urls.size === 1 && urls.has(null) === false;
});

function refreshCoverMetaForSelection() {
  const tracks = selectedTracks.value;
  if (clearCoverRequested.value) {
    coverDimensions.value = null;
    coverSizeBytes.value = null;
    return;
  }
  if (pictureBase64.value) {
    const dataUrl = `data:image/jpeg;base64,${pictureBase64.value}`;
    loadCoverMeta(dataUrl, undefined);
    return;
  }
  if (tracks.length !== 1) {
    coverDimensions.value = null;
    coverSizeBytes.value = null;
    return;
  }
  const path = tracks[0].path;
  const dataUrl = store.getCoverDataUrl(path);
  if (!dataUrl) {
    coverDimensions.value = null;
    coverSizeBytes.value = null;
    return;
  }
  const coverInfo = store.getCover(path);
  const sizeBytes = coverInfo && "size_bytes" in coverInfo ? coverInfo.size_bytes : undefined;
  loadCoverMeta(dataUrl, sizeBytes);
}

function same<T, V>(arr: T[], key: (t: T) => V | null | undefined): V | "" {
  if (arr.length === 0) return "" as V;
  const first = key(arr[0]);
  if (arr.every((t) => key(t) === first)) return first ?? ("" as V);
  return "" as V;
}

function syncFromTracks() {
  const tracks = selectedTracks.value;
  if (tracks.length === 0) return;
  if (tracks.length === 1) {
    const t = tracks[0];
    title.value = t.title ?? "";
    artist.value = t.artist ?? "";
    album.value = t.album ?? "";
    albumArtist.value = t.album_artist ?? "";
    featuring.value = t.featuring ?? "";
    year.value = t.year ?? "";
    genre.value = t.genre ?? "";
    trackNumber.value = t.track_number ?? "";
    discNumber.value = t.disc_number ?? "";
    pictureBase64.value = null;
    clearCoverRequested.value = false;
    store.fetchCover(t.path);
  } else {
    title.value = "";
    artist.value = same(tracks, (tr) => tr.artist) || "";
    album.value = same(tracks, (tr) => tr.album) || "";
    albumArtist.value = same(tracks, (tr) => tr.album_artist) || "";
    featuring.value = same(tracks, (tr) => tr.featuring) || "";
    year.value = same(tracks, (tr) => tr.year) ?? "";
    genre.value = same(tracks, (tr) => tr.genre) || "";
    const tn = same(tracks, (tr) => tr.track_number);
    trackNumber.value = tn === "" || tn == null ? "" : tn;
    const dn = same(tracks, (tr) => tr.disc_number);
    discNumber.value = dn === "" || dn == null ? "" : dn;
    pictureBase64.value = null;
    clearCoverRequested.value = false;
    if (tracks.length > 0) store.fetchCover(tracks[0].path);
  }
  // Rating: show common value across all tracks, or null if mixed
  const firstRating = tracks[0].rating ?? null;
  rating.value = tracks.every((t) => (t.rating ?? null) === firstRating) ? firstRating : null;
  baseline.value = {
    title: title.value,
    artist: artist.value,
    album: album.value,
    albumArtist: albumArtist.value,
    featuring: featuring.value,
    year: year.value,
    genre: genre.value,
    trackNumber: trackNumber.value,
    discNumber: discNumber.value,
    pictureBase64: pictureBase64.value,
  };
  editedFields.value = new Set();
  clearedFields.value = new Set();
}

async function setRating(value: number | null) {
  rating.value = value;
  const paths = selectedTracks.value.map((t) => t.path);
  if (paths.length) await store.setRating(paths, value);
}

watch(selectedTracks, syncFromTracks, { immediate: true });

watch(
  selectedTracks,
  async (tracks) => {
    if (tracks.length !== 1) {
      replayGainMeta.value = null;
      latestBackup.value = null;
      backupWouldChangeMetadata.value = false;
      backupPreviewText.value = "";
      return;
    }
    const trackId = tracks[0].id;
    replayGainMeta.value = await catalogApi.getMetadata(trackId);
    try {
      const bk = await catalogApi.getLatestBackup(trackId);
      latestBackup.value = bk ? { backup_path: bk.path } as TrackBackupRecord : null;
    } catch {
      latestBackup.value = null;
    }
    backupPreviewText.value = "";
    // Backup metadata diff comparison is not available via HTTP API.
    backupWouldChangeMetadata.value = latestBackup.value != null;
  },
  { immediate: true },
);

watch(displayCover, (dataUrl) => {
  if (!dataUrl) {
    coverDimensions.value = null;
    coverSizeBytes.value = null;
    largeImageWarning.value = false;
    return;
  }
  const tracks = selectedTracks.value;
  const coverInfo = tracks.length ? store.getCover(tracks[0].path) : null;
  const sizeBytes = coverInfo && "size_bytes" in coverInfo ? coverInfo.size_bytes : undefined;
  loadCoverMeta(dataUrl, sizeBytes);
});

watch(
  selectedTracks,
  async (tracks) => {
    if (tracks.length !== 1) return;
    await store.fetchCover(tracks[0].path);
    await nextTick();
    refreshCoverMetaForSelection();
  },
  { immediate: true },
);

watch(showCoverPopup, async (open) => {
  if (open) {
    await nextTick();
    coverPopupRef.value?.focus();
    document.addEventListener("keydown", onCoverPopupKeydown);
  } else {
    document.removeEventListener("keydown", onCoverPopupKeydown);
  }
});

watch(showWikipediaModal, (open) => {
  if (open) {
    document.addEventListener("keydown", onWikipediaModalKeydown);
  } else {
    document.removeEventListener("keydown", onWikipediaModalKeydown);
  }
});

watch(
  openWikipediaModal,
  (requested) => {
    if (requested) {
      store.setOpenWikipediaModal(false);
      nextTick(() => openFromWikipedia());
    }
  },
  { immediate: true },
);

watch(
  pendingCoverImagePath,
  (p) => {
    if (!p) return;
    store.setPendingCoverImagePath(null);
    void applyCoverPath(p).catch(() => {});
  },
  { immediate: true },
);

onMounted(() => {
  document.addEventListener("keydown", onPanelKeydown);
});

onUnmounted(() => {
  document.removeEventListener("keydown", onCoverPopupKeydown);
  document.removeEventListener("keydown", onWikipediaModalKeydown);
  document.removeEventListener("keydown", onPanelKeydown);
});

function clearField(field: keyof NonNullable<typeof baseline.value>) {
  switch (field) {
    case "title": title.value = ""; break;
    case "artist": artist.value = ""; break;
    case "album": album.value = ""; break;
    case "albumArtist": albumArtist.value = ""; break;
    case "featuring": featuring.value = ""; break;
    case "genre": genre.value = ""; break;
    case "year": year.value = ""; break;
    case "trackNumber": trackNumber.value = ""; break;
    case "discNumber": discNumber.value = ""; break;
  }
  clearedFields.value = new Set(clearedFields.value).add(field);
  markEdited(field);
}

function markEdited(field: keyof NonNullable<typeof baseline.value>) {
  editedFields.value = new Set(editedFields.value).add(field);
}

const trackNumberPadded = computed({
  get() {
    const v = trackNumber.value;
    if (v === "" || v == null) return "";
    const n = Number(v);
    if (Number.isNaN(n) || n < 0) return String(v);
    return n >= 1 && n <= 9 ? `0${n}` : String(n);
  },
  set(raw: string) {
    const s = raw.trim();
    if (s === "") {
      trackNumber.value = "";
      return;
    }
    const n = parseInt(s, 10);
    trackNumber.value = Number.isNaN(n) ? trackNumber.value : n;
  },
});


const applyFromPathPreviewText = computed(() => {
  const tracks = selectedTracks.value;
  const templates = pathFormatTemplates.value;
  if (!tracks.length || !templates.some((t) => t.trim())) return "";
  const extracted = extractBestFromPath(templates, tracks[0].path);
  if (!extracted) return "No pattern matches this path.";
  return Object.entries(extracted)
    .map(([k, v]) => `${k}: ${v ?? "—"}`)
    .join("\n");
});

const applyFromPathHelpText =
  "Fill fields from the selected track path using the path formats in Settings → Smart Suggestions.";

const applyFromPathMatchCount = computed(() => {
  const tracks = selectedTracks.value;
  const templates = pathFormatTemplates.value;
  if (!tracks.length || !templates.some((t) => t.trim())) return 0;
  return tracks.filter((t) => {
    const extracted = extractBestFromPath(templates, t.path);
    return extracted && Object.keys(buildUpdateFromExtracted(extracted)).length > 0;
  }).length;
});

const applyFromPathPopoverText = computed(() => {
  const preview = applyFromPathPreviewText.value;
  const n = applyFromPathMatchCount.value;
  const total = selectedTracks.value.length;
  const matchLine = total === n && total === 1 ? "" : `Applying to ${n}/${total} track${total === 1 ? "" : "s"}`;
  if (!preview) return matchLine ? `${applyFromPathHelpText}\n\n${matchLine}` : applyFromPathHelpText;
  return matchLine ? `${applyFromPathHelpText}\n\n${matchLine}\n\n${preview}` : `${applyFromPathHelpText}\n\n${preview}`;
});


async function applyFromPath() {
  const tracks = selectedTracks.value;
  const templates = pathFormatTemplates.value;
  if (!tracks.length || !templates.some((t) => t.trim())) return;

  if (tracks.length === 1) {
    const extracted = extractBestFromPath(templates, tracks[0].path);
    if (!extracted) return;
    for (const [key, value] of Object.entries(extracted)) {
      const normalized = key.toLowerCase().replace(/_/g, "");
      const field = PATH_FIELD_MAP[normalized] ?? PATH_FIELD_MAP[key.toLowerCase()];
      if (!field || field === "pictureBase64") continue;
      type EditableField = keyof NonNullable<typeof baseline.value>;
      const editableField = field as EditableField;
      if (field === "trackNumber" || field === "discNumber" || field === "year") {
        const n = value.trim() ? parseInt(value, 10) : "";
        if (n === "" || !Number.isNaN(n)) {
          if (field === "trackNumber") trackNumber.value = n === "" ? "" : n;
          else if (field === "discNumber") discNumber.value = n === "" ? "" : n;
          else year.value = n === "" ? "" : n;
          markEdited(editableField);
        }
      } else {
        const s = value ?? "";
        if (field === "title") title.value = s;
        else if (field === "artist") artist.value = s;
        else if (field === "album") album.value = s;
        else if (field === "albumArtist") albumArtist.value = s;
        else if (field === "featuring") featuring.value = s;
        else if (field === "genre") genre.value = s;
        markEdited(editableField);
      }
    }
  } else {
    // Multi-select: write each track from its own path, then reload once
    saving.value = true;
    saveError.value = null;
    const total = tracks.length;
    store.setBulkProgress({ current: 0, total });
    try {
      for (let i = 0; i < tracks.length; i++) {
        const track = tracks[i];
        const extracted = extractBestFromPath(templates, track.path);
        if (extracted) {
          const update = buildUpdateFromExtracted(extracted);
          if (Object.keys(update).length > 0) {
            const id = store._trackIdByPath(track.path);
            if (id != null) await catalogApi.patchMetadata(id, update, settingsStore.backupBeforeWrite);
          }
        }
        store.setBulkProgress({ current: i + 1, total });
      }
      await store.loadTracks();
      await nextTick();
      syncFromTracks();
    } catch (e) {
      saveError.value = e instanceof Error ? e.message : String(e);
    } finally {
      store.setBulkProgress(null);
      saving.value = false;
    }
  }
}

function buildUpdate(): MetadataUpdate {
  const tracks = selectedTracks.value;
  const isBulk = tracks.length > 1;
  const edited = editedFields.value;
  const cleared = clearedFields.value;

  const titleVal = title.value || undefined;
  const artistVal = artist.value || undefined;
  const albumVal = album.value || undefined;
  const albumArtistVal = albumArtist.value || undefined;
  const yearVal = year.value === "" ? undefined : Number(year.value);
  const genreVal = genre.value || undefined;
  const trackNumVal = trackNumber.value === "" ? undefined : Number(trackNumber.value);
  const discNumVal = discNumber.value === "" ? undefined : Number(discNumber.value);
  const pictureVal = clearCoverRequested.value ? "" : pictureBase64.value ?? undefined;

  const update: MetadataUpdate = {};
  if (!isBulk || edited.has("title") || cleared.has("title")) update.title = cleared.has("title") ? null : (titleVal ?? null);
  if (!isBulk || edited.has("artist") || cleared.has("artist")) update.artist = cleared.has("artist") ? null : (artistVal ?? null);
  if (!isBulk || edited.has("album") || cleared.has("album")) update.album = cleared.has("album") ? null : (albumVal ?? null);
  if (!isBulk || edited.has("albumArtist") || cleared.has("albumArtist")) update.album_artist = cleared.has("albumArtist") ? null : (albumArtistVal ?? null);
  const featuringVal = featuring.value || undefined;
  if (!isBulk || edited.has("featuring") || cleared.has("featuring")) update.featuring = cleared.has("featuring") ? null : (featuringVal ?? null);
  if (!isBulk || edited.has("year") || cleared.has("year")) update.year = cleared.has("year") ? null : (yearVal ?? null);
  if (!isBulk || edited.has("genre") || cleared.has("genre")) update.genre = cleared.has("genre") ? null : (genreVal ?? null);
  if (!isBulk || edited.has("trackNumber") || cleared.has("trackNumber")) update.track_number = cleared.has("trackNumber") ? null : (trackNumVal ?? null);
  if (!isBulk || edited.has("discNumber") || cleared.has("discNumber")) update.disc_number = cleared.has("discNumber") ? null : (discNumVal ?? null);
  if (!isBulk || edited.has("pictureBase64")) update.picture_base64 = pictureVal;

  return update;
}

async function save() {
  const tracks = selectedTracks.value;
  if (!tracks.length) return;
  saving.value = true;
  saveError.value = null;
  try {
    const update = buildUpdate();
    if (tracks.length > 1) {
      await store.writeMetadataBulk(
        tracks.map((t) => t.path),
        update,
      );
    } else {
      await store.writeMetadata(tracks[0].path, update);
    }
    clearCoverRequested.value = false;
    await nextTick();
    syncFromTracks();
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : String(e);
  } finally {
    saving.value = false;
  }
}

async function restoreLatestBackup() {
  const track = selectedTracks.value[0];
  if (!track) return;
  saving.value = true;
  saveError.value = null;
  try {
    await catalogApi.restoreFromLatestBackup(track.id);
    await store.loadTracks();
    await nextTick();
    syncFromTracks();
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : String(e);
  } finally {
    saving.value = false;
  }
}

async function showRestoreBackupPreview(e: MouseEvent) {
  if (!latestBackup.value) return;
  if (!backupPreviewText.value) {
    backupPreviewText.value = "Backup available. Click restore to apply.";
  }
  showTooltip(backupPreviewText.value, e, "above");
}

function clearCover() {
  clearCoverRequested.value = true;
  pictureBase64.value = null;
  largeImageWarning.value = false;
  markEdited("pictureBase64");
}

function discard() {
  syncFromTracks();
}

function onPanelKeydown(e: KeyboardEvent) {
  if (e.key !== "Escape") return;
  if (showCoverPopup.value) {
    showCoverPopup.value = false;
  } else {
    discard();
  }
}

function onCoverFile(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file) return;
  applyCoverFile(file);
  input.value = "";
}

function applyCoverFile(file: File) {
  if (!file.type.startsWith("image/")) return;
  if (file.size > ONE_MB) largeImageWarning.value = true;
  clearCoverRequested.value = false;
  const reader = new FileReader();
  reader.onload = () => {
    const data = reader.result as string;
    const base64 = data.includes(",") ? data.split(",")[1] : data;
    pictureBase64.value = base64 ?? null;
    loadCoverMeta(data, file.size);
    markEdited("pictureBase64");
  };
  reader.readAsDataURL(file);
}

function onCoverDrop(e: DragEvent) {
  e.preventDefault();
  coverDragDepth.value = 0;
  coverDragOver.value = false;
  const file = e.dataTransfer?.files?.[0];
  if (!file) return;
  applyCoverFile(file);
}

async function applyCoverPath(path: string) {
  const bytes = await readFile(path);
  const size = bytes.length;
  if (size > ONE_MB) largeImageWarning.value = true;
  clearCoverRequested.value = false;

  const mime = guessImageMimeFromPath(path);
  const base64 = bytesToBase64(bytes);

  if (mime === "image/png") {
    const dataUrl = `data:image/png;base64,${base64}`;
    pictureBase64.value = await pngDataUrlToJpegBase64(dataUrl);
    loadCoverMeta(`data:image/jpeg;base64,${pictureBase64.value}`, size);
  } else {
    pictureBase64.value = base64;
    loadCoverMeta(`data:image/jpeg;base64,${pictureBase64.value}`, size);
  }

  markEdited("pictureBase64");
}

function isDraggingImage(e: DragEvent): boolean {
  const dt = e.dataTransfer;
  if (!dt) return false;
  if (dt.files && dt.files.length > 0) {
    const f = dt.files[0];
    return !!f && f.type.startsWith("image/");
  }
  // Some browsers only expose types during dragover
  return dt.types?.includes?.("Files") ?? false;
}

function onCoverDragEnter(e: DragEvent) {
  if (!isDraggingImage(e)) return;
  coverDragDepth.value += 1;
  coverDragOver.value = true;
  if (e.dataTransfer) e.dataTransfer.dropEffect = "copy";
}

function onCoverDragOver(e: DragEvent) {
  if (!isDraggingImage(e)) return;
  e.preventDefault();
  coverDragOver.value = true;
  if (e.dataTransfer) e.dataTransfer.dropEffect = "copy";
}

function onCoverDragLeave(e: DragEvent) {
  // Ignore internal moves between children.
  const current = e.currentTarget as HTMLElement | null;
  const related = e.relatedTarget as Node | null;
  if (current && related && current.contains(related)) return;
  coverDragDepth.value = Math.max(0, coverDragDepth.value - 1);
  if (coverDragDepth.value === 0) coverDragOver.value = false;
}

// ── Apply cover to whole album ────────────────────────────────────────────
const applyToAlbumConfirm = ref(false);

const albumTracksForApply = computed(() => {
  const tracks = selectedTracks.value;
  if (!tracks.length) return [];
  const albumVal = (tracks[0].album ?? "").trim();
  const albumArtistVal = (tracks[0].album_artist ?? "").trim();
  if (!albumVal) return [];
  return store.getTracksForAlbum(albumVal, albumArtistVal);
});

/**
 * For single-track view: determines the cover relationship between the current
 * track and the rest of its album.
 *   "can-apply-to-album"    — current track has a cover but not all album tracks share it
 *   "can-apply-from-others" — current track has no cover but other album tracks do
 *   "n/a"                   — no action needed / not applicable
 */
const albumCoverState = computed((): "can-apply-to-album" | "can-apply-from-others" | "n/a" => {
  const tracks = albumTracksForApply.value;
  if (tracks.length <= 1) return "n/a";
  if (selectedTracks.value.length !== 1) return "n/a";

  const currentTrack = selectedTracks.value[0];

  // Pending new cover: always allow applying to album
  if (pictureBase64.value) return "can-apply-to-album";
  if (clearCoverRequested.value) return "n/a";

  const currentCover = store.getCover(currentTrack.path);
  if (currentCover === undefined) return "n/a"; // still loading

  if (currentCover === null) {
    // Current track has no cover — offer to pull it from another track that has one
    const hasSource = tracks.some(t => {
      const c = store.getCover(t.path);
      return c != null;
    });
    return hasSource ? "can-apply-from-others" : "n/a";
  }

  // Current track has a cover — hide button if every album track already has the same one
  const allSame = tracks.every(t => {
    const c = store.getCover(t.path);
    if (c === undefined) return true; // optimistically skip still-loading tracks
    if (c === null) return false;
    if (c.size_bytes !== currentCover.size_bytes) return false;
    return c.base64 === currentCover.base64;
  });

  return allSame ? "n/a" : "can-apply-to-album";
});

/** True when we have a cover to apply and some album tracks differ. */
const canApplyToAlbum = computed(() => {
  if (albumTracksForApply.value.length <= 1) return false;
  if (selectedTracks.value.length === 1) return albumCoverState.value === "can-apply-to-album";
  // Bulk selection: keep original behaviour
  return !!displayCover.value;
});

/** True when current track has no cover but other tracks in its album do. */
const canApplyFromOthers = computed(() => albumCoverState.value === "can-apply-from-others");

async function applyToWholeAlbum() {
  const tracks = albumTracksForApply.value;
  if (!tracks.length) return;
  // Determine the base64 to use: pending picture or current stored cover
  let base64 = pictureBase64.value;
  if (!base64) {
    const firstTrack = selectedTracks.value[0];
    if (!firstTrack) return;
    const coverInfo = store.getCover(firstTrack.path);
    if (!coverInfo) return;
    base64 = coverInfo.base64;
  }
  if (!base64) return;
  applyToAlbumConfirm.value = false;
  saving.value = true;
  saveError.value = null;
  try {
    await store.writeMetadataBulk(
      tracks.map((t) => t.path),
      { picture_base64: base64 },
    );
    await nextTick();
    syncFromTracks();
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : String(e);
  } finally {
    saving.value = false;
  }
}

async function applyFromOtherTracks() {
  const currentTrack = selectedTracks.value[0];
  if (!currentTrack) return;
  const sourceTrack = albumTracksForApply.value.find(t => {
    const c = store.getCover(t.path);
    return c != null;
  });
  if (!sourceTrack) return;
  const coverInfo = store.getCover(sourceTrack.path);
  if (!coverInfo) return;
  saving.value = true;
  saveError.value = null;
  try {
    await store.writeMetadataBulk(
      [currentTrack.path],
      { picture_base64: coverInfo.base64 },
    );
    await nextTick();
    syncFromTracks();
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : String(e);
  } finally {
    saving.value = false;
  }
}
</script>

<template>
  <div
    v-if="selectedTracks.length"
    class="p-3"
  >
    <p v-if="selectedTracks.length > 1" class="mb-2 text-xs text-stone-500">
      Set these fields for all selected tracks. Shared values are pre-filled; change only what you want to update. Only the fields you edit are written—others (e.g. title) stay as-is per track.
    </p>
    <div class="flex flex-col gap-3 sm:flex-row sm:items-start">
      <div class="min-w-0 flex-1">
        <div class="grid grid-cols-2 gap-2 text-sm md:grid-cols-4">
          <div>
            <label class="block text-stone-500">Title</label>
            <div class="relative mt-0.5">
              <input
                v-model="title"
                type="text"
                class="w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
                :class="title ? 'pr-6' : ''"
                @input="markEdited('title')"
              />
              <button v-if="title" type="button" tabindex="-1" class="absolute right-1 inset-y-0 my-auto h-fit rounded p-0.5 text-stone-500 hover:text-stone-300" title="Clear title" @click="clearField('title')"><FeatherIcon name="x" class="h-3 w-3" /></button>
            </div>
          </div>
          <div>
            <label class="block text-stone-500">Artist</label>
            <div class="relative mt-0.5">
              <input
                v-model="artist"
                type="text"
                class="w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
                :class="artist ? 'pr-6' : ''"
                @input="markEdited('artist')"
              />
              <button v-if="artist" type="button" tabindex="-1" class="absolute right-1 inset-y-0 my-auto h-fit rounded p-0.5 text-stone-500 hover:text-stone-300" title="Clear artist" @click="clearField('artist')"><FeatherIcon name="x" class="h-3 w-3" /></button>
            </div>
          </div>
          <div>
            <label class="block text-stone-500">Album</label>
            <div class="relative mt-0.5">
              <input
                v-model="album"
                type="text"
                class="w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
                :class="album ? 'pr-6' : ''"
                @input="markEdited('album')"
              />
              <button v-if="album" type="button" tabindex="-1" class="absolute right-1 inset-y-0 my-auto h-fit rounded p-0.5 text-stone-500 hover:text-stone-300" title="Clear album" @click="clearField('album')"><FeatherIcon name="x" class="h-3 w-3" /></button>
            </div>
          </div>
          <div>
            <label class="block text-stone-500">Year</label>
            <div class="relative mt-0.5">
              <input
                v-model.number="year"
                type="number"
                min="1"
                max="9999"
                class="w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
                :class="year !== '' ? 'pr-6' : ''"
                @input="markEdited('year')"
              />
              <button v-if="year !== ''" type="button" tabindex="-1" class="absolute right-1 inset-y-0 my-auto h-fit rounded p-0.5 text-stone-500 hover:text-stone-300" title="Clear year" @click="clearField('year')"><FeatherIcon name="x" class="h-3 w-3" /></button>
            </div>
          </div>
          <div>
            <label class="block text-stone-500">Album artist</label>
            <div class="relative mt-0.5">
              <input
                v-model="albumArtist"
                type="text"
                class="w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
                :class="albumArtist ? 'pr-6' : ''"
                @input="markEdited('albumArtist')"
              />
              <button v-if="albumArtist" type="button" tabindex="-1" class="absolute right-1 inset-y-0 my-auto h-fit rounded p-0.5 text-stone-500 hover:text-stone-300" title="Clear album artist" @click="clearField('albumArtist')"><FeatherIcon name="x" class="h-3 w-3" /></button>
            </div>
          </div>
          <div>
            <label class="block text-stone-500">Featuring</label>
            <div class="relative mt-0.5">
              <input
                v-model="featuring"
                type="text"
                class="w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
                :class="featuring ? 'pr-6' : ''"
                placeholder="Guest / second artist"
                @input="markEdited('featuring')"
              />
              <button v-if="featuring" type="button" tabindex="-1" class="absolute right-1 inset-y-0 my-auto h-fit rounded p-0.5 text-stone-500 hover:text-stone-300" title="Clear featuring" @click="clearField('featuring')"><FeatherIcon name="x" class="h-3 w-3" /></button>
            </div>
          </div>
          <div class="relative">
            <label class="block text-stone-500">Genre</label>
            <div class="relative mt-0.5">
              <input
                v-model="genre"
                type="text"
                class="w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
                :class="genre ? 'pr-6' : ''"
                @input="markEdited('genre'); showGenreDropdown = true; activeGenreIndex = -1"
                @focus="showGenreDropdown = true"
                @blur="showGenreDropdown = false"
                @keydown="handleGenreKeydown"
              />
              <button v-if="genre" type="button" tabindex="-1" class="absolute right-1 inset-y-0 my-auto h-fit rounded p-0.5 text-stone-500 hover:text-stone-300" title="Clear genre" @click="clearField('genre')"><FeatherIcon name="x" class="h-3 w-3" /></button>
            </div>
            <div
              v-if="showGenreDropdown && filteredGenres.length"
              class="absolute left-0 top-full z-50 mt-0.5 min-w-[240px] rounded border border-stone-600 bg-stone-900 shadow-lg"
            >
              <div ref="genreScrollRef" class="max-h-48">
                <button
                  v-for="(g, i) in filteredGenres"
                  :key="g"
                  type="button"
                  class="flex w-full items-center pl-3 pr-8 py-1 text-left text-sm text-stone-200 hover:bg-stone-700"
                  :class="{ 'bg-stone-700': i === activeGenreIndex }"
                  @mousedown.prevent="selectGenre(g)"
                >{{ g }}</button>
              </div>
            </div>
          </div>
          <div>
            <label class="block text-stone-500">Rating</label>
            <div class="mt-1 flex items-center gap-1.5">
              <StarRating :model-value="rating" @update:model-value="setRating" />
              <button
                v-if="rating !== null"
                type="button"
                class="text-[10px] text-stone-500 hover:text-stone-300"
                title="Clear rating"
                @click="setRating(null)"
              >
                Clear
              </button>
            </div>
          </div>
          <div>
            <label class="block text-stone-500">Track #</label>
            <div class="relative mt-0.5">
              <input
                v-model="trackNumberPadded"
                type="text"
                inputmode="numeric"
                pattern="[0-9]*"
                class="w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
                :class="trackNumber !== '' ? 'pr-6' : ''"
                @input="markEdited('trackNumber')"
              />
              <button v-if="trackNumber !== ''" type="button" tabindex="-1" class="absolute right-1 inset-y-0 my-auto h-fit rounded p-0.5 text-stone-500 hover:text-stone-300" title="Clear track number" @click="clearField('trackNumber')"><FeatherIcon name="x" class="h-3 w-3" /></button>
            </div>
          </div>
          <div>
            <label class="block text-stone-500">Disc #</label>
            <div class="relative mt-0.5">
              <input
                v-model.number="discNumber"
                type="number"
                min="0"
                class="w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
                :class="discNumber !== '' ? 'pr-6' : ''"
                @input="markEdited('discNumber')"
              />
              <button v-if="discNumber !== ''" type="button" tabindex="-1" class="absolute right-1 inset-y-0 my-auto h-fit rounded p-0.5 text-stone-500 hover:text-stone-300" title="Clear disc number" @click="clearField('discNumber')"><FeatherIcon name="x" class="h-3 w-3" /></button>
            </div>
          </div>
          <div>
            <label class="block text-stone-500">ReplayGain Track (dB)</label>
            <input
              :value="replayGainMeta?.replaygain_track_gain_db ?? ''"
              type="text"
              readonly
              class="mt-0.5 w-full rounded border border-stone-700 bg-stone-900/40 px-2 py-0.5 text-stone-400 text-sm"
              placeholder="—"
            />
          </div>
          <div>
            <label class="block text-stone-500">ReplayGain Album (dB)</label>
            <input
              :value="replayGainMeta?.replaygain_album_gain_db ?? ''"
              type="text"
              readonly
              class="mt-0.5 w-full rounded border border-stone-700 bg-stone-900/40 px-2 py-0.5 text-stone-400 text-sm"
              placeholder="—"
            />
          </div>
        </div>
        <div class="mt-2 flex flex-wrap items-center gap-2">
          <button
            type="button"
            class="accent-btn inline-flex items-center gap-1.5 rounded border border-stone-600 px-3 py-1.5 text-sm text-white hover:opacity-90"
            style="background-color: #5b7c32"
            :disabled="saving"
            @click="save"
          >
            <FeatherIcon name="save" class="h-4 w-4 shrink-0" />
            {{
              saving
                ? "Saving…"
                : selectedTracks.length === 1
                  ? "Save to file"
                  : "Save to files"
            }}
          </button>
          <button
            type="button"
            class="inline-flex items-center gap-1.5 rounded border border-stone-600 px-2.5 py-1.5 text-xs text-stone-400 hover:bg-stone-600 hover:text-stone-200 disabled:opacity-50 disabled:pointer-events-none"
            title="Discard unsaved changes"
            :disabled="saving || !hasFormChanges"
            @click="discard"
          >
            <FeatherIcon name="rotate-ccw" class="h-4 w-4 shrink-0" />
            Discard
          </button>
          <button
            v-if="selectedTracks.length === 1 && latestBackup && backupWouldChangeMetadata"
            type="button"
            class="inline-flex items-center gap-1.5 rounded border border-stone-600 px-2.5 py-1.5 text-xs text-stone-400 hover:bg-stone-600 hover:text-stone-200 disabled:opacity-50"
            :disabled="saving"
            @mouseenter="showRestoreBackupPreview($event)"
            @mouseleave="scheduleHideTooltip"
            @click="restoreLatestBackup"
          >
            <FeatherIcon name="rotate-ccw" class="h-4 w-4 shrink-0" />
            Restore backup
          </button>
          <template v-if="pathFormatTemplates.some(t => t.trim()) && selectedTracks.length">
            <span class="ml-1 border-l border-stone-600 pl-2" aria-hidden="true" />
            <button
              type="button"
              class="inline-flex items-center gap-1.5 rounded border border-stone-600 px-2 py-1 text-xs text-stone-400 hover:bg-stone-600 hover:text-stone-200"
              @click="applyFromPath"
              @mouseenter="showTooltip(applyFromPathPopoverText, $event, 'above')"
              @mouseleave="scheduleHideTooltip"
            >
              <FeatherIcon name="zap" class="h-3.5 w-3.5 shrink-0" />
              Apply from path
            </button>
            <span
              class="flex shrink-0 cursor-help rounded p-0.5 text-stone-500 hover:text-stone-300"
              aria-label="Show resolved values from path"
              @mouseenter="showTooltip(applyFromPathPopoverText, $event, 'above')"
              @mouseleave="scheduleHideTooltip"
            >
              <FeatherIcon name="info" class="h-3.5 w-3.5" />
            </span>
            <span
              class="inline-flex"
              @mouseenter="showTooltip('Open path format settings', $event, 'above')"
              @mouseleave="scheduleHideTooltip"
            >
              <button
                type="button"
                class="flex shrink-0 rounded p-0.5 text-stone-500 hover:bg-stone-600 hover:text-stone-300"
                aria-label="Open path format settings"
                @click="settingsStore.setOpenSettingsAtTab('smart_suggestions')"
              >
                <FeatherIcon name="settings" class="h-3.5 w-3.5" />
              </button>
            </span>
          </template>
        </div>
      </div>
      <div class="shrink-0 border-t border-stone-700 pt-3 sm:border-l sm:border-t-0 sm:pl-3 sm:pt-0">
        <div class="flex flex-col items-start gap-1">
          <label v-if="!displayCover" class="text-stone-500">Album cover</label>
          <input
            ref="fileInputRef"
            type="file"
            accept="image/*"
            class="hidden"
            @change="onCoverFile"
          />
          <div v-if="!displayCover" class="flex items-center gap-1.5">
            <button
              type="button"
              class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded border border-stone-600 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
              aria-label="Add image"
              title="Add image"
              @click="fileInputRef?.click()"
            >
              <FeatherIcon name="image" class="h-4 w-4" />
            </button>
            <button
              v-if="!hideWikipediaCoverSearch"
              type="button"
              class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded border border-stone-600 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
              aria-label="From Wikipedia"
              title="From Wikipedia"
              @click="openFromWikipedia"
            >
              <FeatherIcon name="globe" class="h-4 w-4" />
            </button>
          </div>
        </div>
        <div class="mt-0.5 flex flex-col items-start gap-1.5">
          <div
            v-if="displayCover"
            class="flex flex-col items-start gap-1.5"
          >
            <p
              v-if="coverDimensions || coverSizeBytes != null"
              class="text-xs text-stone-500"
            >
              <span v-if="coverDimensions">{{ coverDimensions.width }}×{{ coverDimensions.height }} px</span>
              <span v-if="coverDimensions && coverSizeBytes != null"> · </span>
              <span v-if="coverSizeBytes != null">{{ formatSize(coverSizeBytes) }}</span>
            </p>
            <div
              class="group relative inline-block cursor-pointer"
              role="button"
              tabindex="0"
              @click="showCoverPopup = true"
              @keydown.enter="showCoverPopup = true"
              @keydown.space.prevent="showCoverPopup = true"
            >
              <img
                :src="displayCover"
                alt="Album cover"
                class="h-28 w-28 rounded object-cover border border-stone-600 shadow-md"
              />
              <div
                class="magnify absolute inset-0 flex items-center justify-center rounded bg-stone-900/60 opacity-0 transition-opacity group-hover:opacity-100"
                aria-hidden="true"
              >
                <FeatherIcon name="zoom-in" class="h-8 w-8 text-stone-300" />
              </div>
            </div>
            <div class="mt-1 flex flex-wrap items-center gap-2">
              <button
                type="button"
                class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded border border-stone-600 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
                aria-label="Choose image"
                title="Choose image"
                @click="fileInputRef?.click()"
              >
                <FeatherIcon name="edit-2" class="h-3.5 w-3.5" />
              </button>
              <button
                v-if="selectedTracks.length === 1 || multiSelectionSharedCover"
                type="button"
                class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded border border-stone-600 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
                aria-label="Remove album cover"
                title="Remove album cover"
                @click="clearCover"
              >
                <FeatherIcon name="trash-2" class="h-3.5 w-3.5" />
              </button>
            </div>
            <div v-if="canApplyToAlbum" class="mt-1">
              <template v-if="!applyToAlbumConfirm">
                <button
                  type="button"
                  class="inline-flex items-center gap-1 rounded border border-stone-600 px-2 py-0.5 text-xs text-stone-400 hover:bg-stone-600 hover:text-stone-200"
                  @click="applyToAlbumConfirm = true"
                >
                  <FeatherIcon name="layers" class="h-3 w-3 shrink-0" />
                  Apply to album
                </button>
              </template>
              <template v-else>
                <p class="mb-1 text-xs text-stone-400">Apply to {{ albumTracksForApply.length }} tracks?</p>
                <div class="flex items-center gap-1.5">
                  <button
                    type="button"
                    class="rounded border border-stone-600 px-2 py-0.5 text-xs text-stone-200 hover:bg-stone-600 disabled:opacity-50"
                    :disabled="saving"
                    @click="applyToWholeAlbum"
                  >
                    {{ saving ? '…' : 'Confirm' }}
                  </button>
                  <button
                    type="button"
                    class="rounded border border-stone-600 px-2 py-0.5 text-xs text-stone-400 hover:bg-stone-600"
                    @click="applyToAlbumConfirm = false"
                  >
                    Cancel
                  </button>
                </div>
              </template>
            </div>
            <p
              v-if="largeImageWarning"
              class="max-w-[200px] text-xs text-amber-400"
            >
              This image is over 1 MB. Embedding large artwork can degrade performance in media players and in Muorg, as it is stored inside each file.
            </p>
          </div>
          <div
            v-else
            class="group relative inline-flex h-28 w-28 items-center justify-center rounded border border-stone-600 bg-stone-900 shadow-md"
            :class="coverDragOver ? 'ring-2 ring-inset ring-[#5b7c32]' : undefined"
            role="button"
            tabindex="0"
            aria-label="Album cover placeholder"
            @click="fileInputRef?.click()"
            @keydown.enter="fileInputRef?.click()"
            @keydown.space.prevent="fileInputRef?.click()"
            @dragenter.prevent="onCoverDragEnter"
            @dragover="onCoverDragOver"
            @dragleave="onCoverDragLeave"
            @drop="onCoverDrop"
          >
            <span class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-stone-500 text-stone-400">
              ♪
            </span>
            <div
              class="pointer-events-none absolute inset-0 flex items-center justify-center rounded bg-stone-900/60 opacity-0 transition-opacity group-hover:opacity-100"
              aria-hidden="true"
            >
              <FeatherIcon name="upload" class="h-8 w-8 text-stone-300" />
            </div>
          </div>
          <div v-if="canApplyFromOthers" class="mt-1">
            <button
              type="button"
              class="inline-flex items-center gap-1 rounded border border-stone-600 px-2 py-0.5 text-xs text-stone-400 hover:bg-stone-600 hover:text-stone-200"
              :disabled="saving"
              @click="applyFromOtherTracks"
            >
              <FeatherIcon name="download" class="h-3 w-3 shrink-0" />
              Apply from other tracks
            </button>
          </div>
        </div>
      </div>
    </div>
    <p v-if="saveError" class="mt-1.5 text-xs text-red-400">{{ saveError }}</p>

    <!-- Cover art popup -->
    <Teleport to="body">
      <div
        v-if="showCoverPopup && displayCover"
        ref="coverPopupRef"
        tabindex="-1"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-stone-950/80 p-4 outline-none"
        role="dialog"
        aria-modal="true"
        aria-label="Album cover"
        @click.self="showCoverPopup = false"
      >
        <img
          :src="displayCover"
          alt="Album cover (enlarged)"
          class="max-h-[90vh] max-w-[90vw] rounded-lg shadow-xl object-contain"
          @click="showCoverPopup = false"
        />
      </div>
    </Teleport>
    <!-- Wikipedia image modal -->
    <Teleport to="body">
      <div
        v-if="showWikipediaModal"
        class="fixed inset-0 z-[100] flex items-center justify-center bg-stone-950/80 p-4"
        role="dialog"
        aria-modal="true"
        aria-label="Image from Wikipedia"
        @click.self="closeWikipediaModal"
      >
        <div
          class="flex max-h-[90vh] max-w-lg flex-col gap-4 rounded-lg border border-stone-600 bg-stone-800 p-4 shadow-xl"
          @click.stop
        >
          <p v-if="wikipediaImageUrl" class="text-sm font-medium text-stone-300">Use this image?</p>
          <p v-if="wikipediaSearchLoading" class="text-xs text-stone-500">Searching Wikipedia…</p>
          <p v-else-if="wikipediaError" class="text-xs text-amber-400">{{ wikipediaError }}</p>
          <template v-else-if="wikipediaImageUrl">
            <img
              :src="wikipediaImageUrl"
              alt="Wikipedia result"
              class="max-h-[60vh] w-full rounded object-contain border border-stone-600"
            />
            <div class="flex justify-end gap-2">
              <button
                type="button"
                class="rounded border border-stone-600 px-3 py-1.5 text-xs text-stone-400 hover:bg-stone-600 hover:text-stone-200"
                @click="closeWikipediaModal"
              >
                Cancel
              </button>
              <button
                type="button"
                class="accent-btn rounded px-3 py-1.5 text-xs text-white disabled:opacity-50"
                style="background-color: #5b7c32"
                :disabled="wikipediaApplying"
                @click="applyWikipediaImage"
              >
                {{ wikipediaApplying ? "Applying…" : "Yes, use this image" }}
              </button>
            </div>
          </template>
          <template v-else>
            <p class="text-sm text-stone-400">No image was found on Wikipedia.</p>
            <div class="flex justify-end">
              <button
                type="button"
                class="rounded border border-stone-600 px-3 py-1.5 text-xs text-stone-400 hover:bg-stone-600 hover:text-stone-200"
                @click="closeWikipediaModal"
              >
                Close
              </button>
            </div>
          </template>
        </div>
      </div>
    </Teleport>
    <!-- Tooltip popover -->
    <Teleport to="body">
      <div
        v-if="tooltipPopover"
        class="fixed z-[200] whitespace-pre-line rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 text-xs text-stone-200 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
        :style="
          tooltipPopover.position === 'left'
            ? { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translate(-100%, -50%)' }
            : tooltipPopover.position === 'above'
              ? { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translate(-50%, -100%)' }
              : { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translateX(-50%)' }
        "
        @mouseenter="cancelHideTooltip"
        @mouseleave="hideTooltip"
      >
        {{ tooltipPopover.text }}
      </div>
    </Teleport>
  </div>
</template>
