<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import { useSettingsStore } from "../stores/settings";
import type { MetadataUpdate } from "../types";
import { extractMetadataFromPath } from "../utils/pathFormat";
import { invoke } from "@tauri-apps/api/core";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { selectedTracks, openWikipediaModal, multiSelectMode } = storeToRefs(store);
const { hideWikipediaCoverSearch, pathFormatTemplate } = storeToRefs(settingsStore);

const title = ref("");
const artist = ref("");
const album = ref("");
const albumArtist = ref("");
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

const showWikipediaModal = ref(false);
const wikipediaImageUrl = ref<string | null>(null);
const wikipediaSearchLoading = ref(false);
const wikipediaError = ref<string | null>(null);
const wikipediaApplying = ref(false);

const tooltipPopover = ref<{ text: string; x: number; y: number; position?: "left" | "below" } | null>(null);
let tooltipHideTimeout: ReturnType<typeof setTimeout> | null = null;

function showTooltip(text: string, e: MouseEvent, position: "left" | "below" = "below") {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  if (position === "left") {
    tooltipPopover.value = { text, x: rect.left - 8, y: rect.top + rect.height / 2, position: "left" };
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

/** Snapshot of form state when last synced from tracks (used to detect changes). */
const baseline = ref<{
  title: string;
  artist: string;
  album: string;
  albumArtist: string;
  year: number | "";
  genre: string;
  trackNumber: number | "";
  discNumber: number | "";
  pictureBase64: string | null;
} | null>(null);

/** In bulk mode, only these fields are written on save (user has edited them). */
const editedFields = ref<Set<keyof NonNullable<typeof baseline.value>>>(new Set());

const ONE_MB = 1024 * 1024;

const hasFormChanges = computed(() => {
  const b = baseline.value;
  if (!b) return false;
  return (
    title.value !== b.title ||
    artist.value !== b.artist ||
    album.value !== b.album ||
    albumArtist.value !== b.albumArtist ||
    year.value !== b.year ||
    genre.value !== b.genre ||
    trackNumber.value !== b.trackNumber ||
    discNumber.value !== b.discNumber ||
    pictureBase64.value !== b.pictureBase64 ||
    clearCoverRequested.value
  );
});

function onCoverPopupKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") showCoverPopup.value = false;
}

function onWikipediaModalKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") closeWikipediaModal();
}

const WIKI_API = "https://en.wikipedia.org/w/api.php";

/** Normalize for matching: lowercase, strip "File:", remove extension, collapse non-alphanumeric. */
function normalizeFileTitle(title: string): { name: string; ext: string } {
  const withoutPrefix = title.replace(/^File:/i, "").trim();
  const lastDot = withoutPrefix.lastIndexOf(".");
  const name = (lastDot >= 0 ? withoutPrefix.slice(0, lastDot) : withoutPrefix)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "");
  const ext = (lastDot >= 0 ? withoutPrefix.slice(lastDot + 1) : "").toLowerCase();
  return { name, ext };
}

/** Score image as likely album art: +album name in filename, +cover/album, -icons/svg. */
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

/** Pick best image from list: prefer filename containing album name or "cover"/"album", skip obvious icons. */
function pickBestAlbumImage(imageTitles: { title: string }[], albumName: string): string | null {
  if (!imageTitles?.length) return null;
  const scored = imageTitles
    .filter((img) => img.title.startsWith("File:"))
    .map((img) => ({ title: img.title, score: scoreImageAsAlbumArt(img.title, albumName) }));
  scored.sort((a, b) => b.score - a.score);
  return scored[0]?.title ?? null;
}

/** Search Wikipedia for the album page, pick best image (by album name / cover keywords), resolve its URL. */
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
    // 1) Search for the album page (prefer "AlbumName (album)" to hit the album article)
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

    // 2) Get images on that page and pick the best match (album name / cover in filename, skip icons)
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

    // 3) Get the image URL (prefer 800px width for album art)
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

/** Convert image data URL (e.g. from canvas) to JPEG base64 only (for backend which expects JPEG). */
function dataUrlToJpegBase64(dataUrl: string): string {
  const i = dataUrl.indexOf(",");
  return i >= 0 ? dataUrl.slice(i + 1) : dataUrl;
}

/** Download image via backend and set as album cover. Backend expects JPEG base64; we convert PNG if needed. */
async function applyWikipediaImage() {
  const url = wikipediaImageUrl.value;
  if (!url) return;
  wikipediaApplying.value = true;
  try {
    const { base64, mime } = await invoke<{ base64: string; mime: string }>("fetch_image_url", {
      url,
    });
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
      undefined
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

/** Load dimensions (and optionally size) from a data URL. Using the correct MIME in the URL ensures PNG/other types decode. */
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

/** Data URL for the cover image (correct MIME so dimensions load and display is consistent). */
const displayCover = computed(() => {
  if (clearCoverRequested.value) return null;
  if (pictureBase64.value) return `data:image/jpeg;base64,${pictureBase64.value}`;
  const tracks = selectedTracks.value;
  if (tracks.length > 0) return store.getCoverDataUrl(tracks[0].path);
  return null;
});

/** Refresh dimensions/size from current cover (single track or pictureBase64). Call after fetch so dimensions always show. */
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

/** If all items have the same value for key(), return it; otherwise return "". */
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
    year.value = t.year ?? "";
    genre.value = t.genre ?? "";
    trackNumber.value = t.track_number ?? "";
    discNumber.value = t.disc_number ?? "";
    pictureBase64.value = null;
    clearCoverRequested.value = false;
    store.fetchCover(t.path);
  } else {
    title.value = "";
    artist.value = same(tracks, (t) => t.artist) || "";
    album.value = same(tracks, (t) => t.album) || "";
    albumArtist.value = same(tracks, (t) => t.album_artist) || "";
    year.value = same(tracks, (t) => t.year) ?? "";
    genre.value = same(tracks, (t) => t.genre) || "";
    const tn = same(tracks, (t) => t.track_number);
    trackNumber.value = tn === "" || tn == null ? "" : tn;
    const dn = same(tracks, (t) => t.disc_number);
    discNumber.value = dn === "" || dn == null ? "" : dn;
    pictureBase64.value = null;
    clearCoverRequested.value = false;
    if (tracks.length > 0) store.fetchCover(tracks[0].path);
  }
  baseline.value = {
    title: title.value,
    artist: artist.value,
    album: album.value,
    albumArtist: albumArtist.value,
    year: year.value,
    genre: genre.value,
    trackNumber: trackNumber.value,
    discNumber: discNumber.value,
    pictureBase64: pictureBase64.value,
  };
  editedFields.value = new Set();
}

watch(selectedTracks, syncFromTracks, { immediate: true });

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

/** When a single track is selected, ensure cover is fetched then refresh dimensions/size so they always show. */
watch(
  selectedTracks,
  async (tracks) => {
    if (tracks.length !== 1) return;
    await store.fetchCover(tracks[0].path);
    await nextTick();
    refreshCoverMetaForSelection();
  },
  { immediate: true }
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

watch(openWikipediaModal, (requested) => {
  if (requested) {
    store.setOpenWikipediaModal(false);
    nextTick(() => openFromWikipedia());
  }
}, { immediate: true });

onMounted(() => {
  document.addEventListener("keydown", onPanelKeydown);
});

onUnmounted(() => {
  document.removeEventListener("keydown", onCoverPopupKeydown);
  document.removeEventListener("keydown", onWikipediaModalKeydown);
  document.removeEventListener("keydown", onPanelKeydown);
});

function markEdited(field: keyof NonNullable<typeof baseline.value>) {
  editedFields.value = new Set(editedFields.value).add(field);
}

/** Track # display with single 0-padding (1 → "01", 10 → "10"). */
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

/** Map extracted path placeholders (e.g. Artist, TrackTitle) to form field names. */
const PATH_FIELD_MAP: Record<string, keyof NonNullable<typeof baseline.value>> = {
  artist: "artist",
  album: "album",
  title: "title",
  tracktitle: "title",
  tracknumber: "trackNumber",
  track_number: "trackNumber",
  year: "year",
  genre: "genre",
  albumartist: "albumArtist",
  album_artist: "albumArtist",
  discnumber: "discNumber",
  disc_number: "discNumber",
};

/** Resolved key/value pairs from the first selected track's path (for tooltip preview). */
const applyFromPathPreviewText = computed(() => {
  const tracks = selectedTracks.value;
  const format = pathFormatTemplate.value?.trim();
  if (!tracks.length || !format) return "";
  const extracted = extractMetadataFromPath(format, tracks[0].path);
  if (!extracted) return "Format does not match this path.";
  return Object.entries(extracted)
    .map(([k, v]) => `${k}: ${v ?? "—"}`)
    .join("\n");
});

function applyFromPath() {
  const tracks = selectedTracks.value;
  const format = pathFormatTemplate.value?.trim();
  if (!tracks.length || !format) return;
  const path = tracks[0].path;
  const extracted = extractMetadataFromPath(format, path);
  if (!extracted) return;
  for (const [key, value] of Object.entries(extracted)) {
    const normalized = key.toLowerCase().replace(/_/g, "");
    const field = PATH_FIELD_MAP[normalized] ?? PATH_FIELD_MAP[key.toLowerCase()];
    if (!field || field === "pictureBase64") continue;
    if (field === "trackNumber" || field === "discNumber" || field === "year") {
      const n = value.trim() ? parseInt(value, 10) : "";
      if (n === "" || !Number.isNaN(n)) {
        if (field === "trackNumber") trackNumber.value = n === "" ? "" : n;
        else if (field === "discNumber") discNumber.value = n === "" ? "" : n;
        else year.value = n === "" ? "" : n;
        markEdited(field);
      }
    } else {
      const s = value ?? "";
      if (field === "title") title.value = s;
      else if (field === "artist") artist.value = s;
      else if (field === "album") album.value = s;
      else if (field === "albumArtist") albumArtist.value = s;
      else if (field === "genre") genre.value = s;
      markEdited(field);
    }
  }
}

/** Build update for save. In bulk mode, only includes fields the user has edited so other fields stay per-track. */
function buildUpdate(): MetadataUpdate {
  const tracks = selectedTracks.value;
  const isBulk = tracks.length > 1;
  const edited = editedFields.value;

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
  if (!isBulk || edited.has("title")) update.title = titleVal ?? null;
  if (!isBulk || edited.has("artist")) update.artist = artistVal ?? null;
  if (!isBulk || edited.has("album")) update.album = albumVal ?? null;
  if (!isBulk || edited.has("albumArtist")) update.album_artist = albumArtistVal ?? null;
  if (!isBulk || edited.has("year")) update.year = yearVal ?? null;
  if (!isBulk || edited.has("genre")) update.genre = genreVal ?? null;
  if (!isBulk || edited.has("trackNumber")) update.track_number = trackNumVal ?? null;
  if (!isBulk || edited.has("discNumber")) update.disc_number = discNumVal ?? null;
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
        update
      );
    } else {
      await store.writeMetadata(tracks[0].path, update);
    }
    clearCoverRequested.value = false;
    store.clearSelection();
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : String(e);
  } finally {
    saving.value = false;
  }
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
    store.clearSelection();
  }
}

function onCoverFile(e: Event) {
  const input = e.target as HTMLInputElement;
  const file = input.files?.[0];
  if (!file || !file.type.startsWith("image/")) return;
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
  input.value = "";
}
</script>

<template>
  <div
    v-if="selectedTracks.length"
    class="border-t border-stone-700 bg-stone-800/90 p-3"
  >
    <div class="mb-0.5 flex items-center gap-2">
      <span
        class="inline-flex"
        @mouseenter="showTooltip('Close (discard changes)', $event)"
        @mouseleave="scheduleHideTooltip"
      >
        <button
          type="button"
          class="rounded p-1.5 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
          aria-label="Close (discard changes)"
          @click="discard(); store.clearSelection()"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </span>
      <h3 class="text-sm font-medium text-stone-300">
        Edit metadata{{ multiSelectMode && selectedTracks.length ? ` (${selectedTracks.length} selected)` : '' }}
      </h3>
      <button
        v-if="pathFormatTemplate.trim() && selectedTracks.length"
        type="button"
        class="rounded border border-stone-600 px-2 py-1 text-xs text-stone-400 hover:bg-stone-600 hover:text-stone-200"
        title="Fill fields from the selected track path using the path format in Settings → Smart Suggestions"
        @click="applyFromPath"
      >
        Apply from path
      </button>
      <span
        v-if="pathFormatTemplate.trim() && selectedTracks.length"
        class="flex shrink-0 cursor-help rounded p-0.5 text-stone-500 hover:text-stone-300"
        aria-label="Show resolved values from path"
        @mouseenter="showTooltip(applyFromPathPreviewText || 'No match', $event)"
        @mouseleave="scheduleHideTooltip"
      >
        <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
          <circle cx="12" cy="12" r="10" />
          <path stroke-linecap="round" d="M12 16v-4M12 8h.01" />
        </svg>
      </span>
      <span
        v-if="pathFormatTemplate.trim() && selectedTracks.length"
        class="inline-flex"
        @mouseenter="showTooltip('Open path format settings', $event)"
        @mouseleave="scheduleHideTooltip"
      >
        <button
          type="button"
          class="flex shrink-0 rounded p-0.5 text-stone-500 hover:bg-stone-600 hover:text-stone-300"
          aria-label="Open path format settings"
          @click="settingsStore.setOpenSettingsAtTab('smart_suggestions')"
        >
          <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
            <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
          </svg>
        </button>
      </span>
    </div>
    <p v-if="selectedTracks.length > 1" class="mb-2 text-xs text-stone-500">
      Set these fields for all selected tracks. Shared values are pre-filled; change only what you want to update. Only the fields you edit are written—others (e.g. title) stay as-is per track.
    </p>
    <div class="flex flex-col gap-3 sm:flex-row sm:items-start">
      <div class="min-w-0 flex-1">
        <div class="grid grid-cols-2 gap-2 text-sm md:grid-cols-4">
          <div>
            <label class="block text-stone-500">Title</label>
            <input
              v-model="title"
              type="text"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
              @input="markEdited('title')"
            />
          </div>
          <div>
            <label class="block text-stone-500">Artist</label>
            <input
              v-model="artist"
              type="text"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
              @input="markEdited('artist')"
            />
          </div>
          <div>
            <label class="block text-stone-500">Album</label>
            <input
              v-model="album"
              type="text"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
              @input="markEdited('album')"
            />
          </div>
          <div>
            <label class="block text-stone-500">Album artist</label>
            <input
              v-model="albumArtist"
              type="text"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
              @input="markEdited('albumArtist')"
            />
          </div>
          <div>
            <label class="block text-stone-500">Year</label>
            <input
              v-model.number="year"
              type="number"
              min="1"
              max="9999"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
              @input="markEdited('year')"
            />
          </div>
          <div>
            <label class="block text-stone-500">Genre</label>
            <input
              v-model="genre"
              type="text"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
              @input="markEdited('genre')"
            />
          </div>
          <div>
            <label class="block text-stone-500">Track #</label>
            <input
              v-model="trackNumberPadded"
              type="text"
              inputmode="numeric"
              pattern="[0-9]*"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
              @input="markEdited('trackNumber')"
            />
          </div>
          <div>
            <label class="block text-stone-500">Disc #</label>
            <input
              v-model.number="discNumber"
              type="number"
              min="0"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
              @input="markEdited('discNumber')"
            />
          </div>
        </div>
        <div class="mt-2 flex flex-wrap items-center gap-2">
          <button
            type="button"
            class="rounded border border-stone-600 px-3 py-1.5 text-sm text-white hover:opacity-90"
            style="background-color: #5b7c32"
            :disabled="saving"
            @click="save"
          >
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
            <svg class="h-4 w-4 shrink-0" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 15L3 9m0 0l6-6M3 9h12a6 6 0 010 12h-3" />
            </svg>
            Discard
          </button>
        </div>
      </div>
      <div class="shrink-0 border-t border-stone-700 pt-3 sm:border-l sm:border-t-0 sm:pl-3 sm:pt-0">
        <div class="flex items-center gap-1.5">
          <label v-if="!displayCover" class="text-stone-500">Album cover</label>
          <input
            ref="fileInputRef"
            type="file"
            accept="image/*"
            class="hidden"
            @change="onCoverFile"
          />
          <button
            v-if="!displayCover"
            type="button"
            class="rounded border border-stone-600 p-0.5 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
            aria-label="Add image"
            title="Add image"
            @click="fileInputRef?.click()"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M4 5a2 2 0 012-2h4l2 2h6a2 2 0 012 2v10a2 2 0 01-2 2H6a2 2 0 01-2-2V5z" />
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 13l2.5 3 3.5-5 4 6H7l2-4z" />
            </svg>
          </button>
          <button
            v-if="!displayCover && !hideWikipediaCoverSearch"
            type="button"
            class="rounded border border-stone-600 p-0.5 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
            aria-label="From Wikipedia"
            title="From Wikipedia"
            @click="openFromWikipedia"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="10" />
              <path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z" />
            </svg>
          </button>
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
                <svg class="h-8 w-8 text-stone-300" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0zM10 7v3m0 0v3m0-3h3m-3 0H7" />
                </svg>
              </div>
            </div>
            <div class="mt-1 flex items-center gap-2">
              <button
                type="button"
                class="rounded border border-stone-600 p-1 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
                aria-label="Choose image"
                title="Choose image"
                @click="fileInputRef?.click()"
              >
                <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z" />
                </svg>
              </button>
              <button
                v-if="selectedTracks.length === 1"
                type="button"
                class="rounded border border-stone-600 p-1 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
                aria-label="Remove album cover"
                title="Remove album cover"
                @click="clearCover"
              >
                <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M3 6h18" />
                  <path stroke-linecap="round" stroke-linejoin="round" d="M8 6V4h8v2" />
                  <path stroke-linecap="round" stroke-linejoin="round" d="M10 11v6M14 11v6" />
                  <path stroke-linecap="round" stroke-linejoin="round" d="M6 6l1 12a2 2 0 002 2h6a2 2 0 002-2l1-12" />
                </svg>
              </button>
            </div>
            <p
              v-if="largeImageWarning"
              class="max-w-[200px] text-xs text-amber-400"
            >
              This image is over 1 MB. Embedding large artwork can degrade performance in media players and in Muorg, as it is stored inside each file.
            </p>
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
          <p class="text-sm font-medium text-stone-300">Use this image?</p>
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
                class="rounded px-3 py-1.5 text-xs text-white disabled:opacity-50"
                style="background-color: #5b7c32"
                :disabled="wikipediaApplying"
                @click="applyWikipediaImage"
              >
                {{ wikipediaApplying ? "Applying…" : "Yes, use this image" }}
              </button>
            </div>
          </template>
          <div v-else class="flex justify-end">
            <button
              type="button"
              class="rounded border border-stone-600 px-3 py-1.5 text-xs text-stone-400 hover:bg-stone-600 hover:text-stone-200"
              @click="closeWikipediaModal"
            >
              Close
            </button>
          </div>
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
