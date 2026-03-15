<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import type { MetadataUpdate } from "../../types";
import { extractMetadataFromPath } from "../../utils/pathFormat";
import { invoke } from "@tauri-apps/api/core";
import FeatherIcon from "../shared/FeatherIcon.vue";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { selectedTracks, openWikipediaModal } = storeToRefs(store);
const { hideWikipediaCoverSearch, pathFormatTemplate } = storeToRefs(settingsStore);

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

const showWikipediaModal = ref(false);
const wikipediaImageUrl = ref<string | null>(null);
const wikipediaSearchLoading = ref(false);
const wikipediaError = ref<string | null>(null);
const wikipediaApplying = ref(false);

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
  featuring: "featuring",
  discnumber: "discNumber",
  disc_number: "discNumber",
};

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

const applyFromPathHelpText =
  "Fill fields from the selected track path using the path format in Settings → Smart Suggestions.";

const applyFromPathPopoverText = computed(() => {
  const preview = applyFromPathPreviewText.value;
  if (!preview) return applyFromPathHelpText;
  return `${applyFromPathHelpText}\n\n${preview}`;
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
      else if (field === "featuring") featuring.value = s;
      else if (field === "genre") genre.value = s;
      markEdited(field);
    }
  }
}

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
  const featuringVal = featuring.value || undefined;
  if (!isBulk || edited.has("featuring")) update.featuring = featuringVal ?? null;
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
            <label class="block text-stone-500">Featuring</label>
            <input
              v-model="featuring"
              type="text"
              class="mt-0.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
              placeholder="Guest / second artist"
              @input="markEdited('featuring')"
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
          <template v-if="pathFormatTemplate.trim() && selectedTracks.length">
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
            class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded border border-stone-600 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
            aria-label="Add image"
            title="Add image"
            @click="fileInputRef?.click()"
          >
            <FeatherIcon name="image" class="h-4 w-4" />
          </button>
          <button
            v-if="!displayCover && !hideWikipediaCoverSearch"
            type="button"
            class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded border border-stone-600 text-stone-400 hover:bg-stone-600 hover:text-stone-200"
            aria-label="From Wikipedia"
            title="From Wikipedia"
            @click="openFromWikipedia"
          >
            <FeatherIcon name="globe" class="h-4 w-4" />
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
                <FeatherIcon name="zoom-in" class="h-8 w-8 text-stone-300" />
              </div>
            </div>
            <div class="mt-1 flex items-center gap-2">
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
                class="accent-btn rounded px-3 py-1.5 text-xs text-white disabled:opacity-50"
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

