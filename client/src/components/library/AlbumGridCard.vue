<script setup lang="ts">
import { computed, ref, watch, watchEffect } from "vue";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import { PRIMARY_RGB } from "../../composables/useDominantColor";
import CardHoverActions from "../shared/CardHoverActions.vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { vMarquee } from "../../composables/useMarquee";
import type { AlbumGridItem } from "./AlbumGridView.vue";
import { setTracksDragGhost } from "../../utils/dragGhost";

const props = defineProps<{
  album: AlbumGridItem;
  isPlaying?: boolean;
  /** Override the global card layout setting (e.g. search results). */
  styleOverride?: "overlay" | "below";
}>();

const emit = defineEmits<{
  (e: "openAlbum", albumKey: string): void;
  (e: "albumContextMenu", event: MouseEvent, albumKey: string): void;
}>();

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const cardStyle = computed(() => props.styleOverride ?? settingsStore.cardStyle);

function playAlbum() {
  if (!props.album.trackIds.length) return;
  store.queueTrackIds = [...props.album.trackIds];
  store.setSelection([props.album.trackIds[0]]);
  store.setPlayRequestTrackId(props.album.trackIds[0]);
}

function shuffleAlbum() {
  const ids = [...props.album.trackIds];
  for (let i = ids.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [ids[i], ids[j]] = [ids[j], ids[i]];
  }
  if (!ids.length) return;
  store.queueTrackIds = ids;
  store.setSelection([ids[0]]);
  store.setPlayRequestTrackId(ids[0]);
}

function onDragStart(e: DragEvent) {
  if (!e.dataTransfer) return;
  setTracksDragGhost(e, props.album.album, props.album.trackIds.length, coverDataUrl.value);
  e.dataTransfer.setData("application/muorg-tracks", JSON.stringify(props.album.trackIds));
  e.dataTransfer.effectAllowed = "copy";
  store.setInternalQueueDrag(true, props.album.trackIds);
}

function onDragEnd() {
  store.setInternalQueueDrag(false);
}

// Ask for this album's cover, then push it to the front of the queue since the
// card is on screen.
//
// The card used to only boost, which does nothing unless the cover is already
// queued — it was living off a bulk fetch in the library table that walked the
// whole library. Once that was scoped to what the table actually renders, the
// album grid had nothing filling its covers and came up blank. A view that
// shows a cover asks for it.
watchEffect(() => {
  if (!props.album.hasCover) return;
  store.fetchCover(props.album.coverPath);
  store.boostCoverPriority(props.album.coverPath);
});

/** undefined = loading, null = no cover, CoverInfo = has cover */
const coverEntry = computed(() => props.album.hasCover ? store.getCover(props.album.coverPath) : null);
const coverDataUrl = computed(() => store.getCoverDataUrl(props.album.coverPath));

/**
 * imageReady tracks whether the browser has fully decoded and painted the image.
 * We keep the spinner up until @load fires, preventing the half-painted flash.
 */
const imageReady = ref(false);

// Reset imageReady whenever the data URL changes (e.g. cover updated after metadata edit).
watch(coverDataUrl, () => { imageReady.value = false; });

function onImageLoad() { imageReady.value = true; }
function onImageError() { imageReady.value = false; }

/**
 * Three display states:
 *  "spinner"     — cover is fetching OR data URL arrived but img not decoded yet
 *  "image"       — img fully loaded and painted
 *  "placeholder" — no cover (confirmed), or hasCover=false
 */
const displayState = computed(() => {
  if (!props.album.hasCover) return "placeholder";
  const e = coverEntry.value;
  if (e === undefined) return "spinner";   // still fetching from backend
  if (e === null)      return "placeholder"; // fetched, confirmed no art
  if (!imageReady.value) return "spinner"; // data arrived, browser still decoding
  return "image";
});

</script>

<template>
  <!-- Web-client AlbumCard grid mode: square art, scrim overlay caption, hover scale -->
  <button
    type="button"
    draggable="true"
    class="group relative w-full select-none text-left transition-transform duration-150 hover:scale-[1.03] active:scale-95"
    :class="[
      cardStyle === 'overlay'
        ? 'aspect-square overflow-hidden rounded-xl bg-surface-variant ring-1 ring-white/10'
        : 'album-card',
      isPlaying ? 'ring-2 ring-primary' : '',
    ]"
    :style="isPlaying ? { boxShadow: `0 0 0 2px rgba(${PRIMARY_RGB},0.5), 0 12px 30px -8px rgba(${PRIMARY_RGB},0.45)` } : {}"
    @click="emit('openAlbum', album.key)"
    @contextmenu.prevent="emit('albumContextMenu', $event, album.key)"
    @dragstart="onDragStart"
    @dragend="onDragEnd"
  >
    <div :class="cardStyle === 'overlay' ? 'absolute inset-0' : 'relative aspect-square w-full overflow-hidden rounded-lg bg-surface-variant'">
    <!-- Skeleton placeholder: shown while fetching OR while browser is decoding the image -->
    <div
      v-if="displayState === 'spinner'"
      class="absolute inset-0 animate-pulse bg-gradient-to-r from-surface-variant via-surface-container to-surface-variant bg-[length:200%_100%]"
    />

    <!-- No cover placeholder -->
    <div
      v-else-if="displayState === 'placeholder'"
      class="absolute inset-0 flex items-center justify-center text-on-surface-variant"
    >
      <span class="inline-flex h-12 w-12 items-center justify-center rounded-full border border-outline text-xl">♪</span>
    </div>

    <!--
      The img is always rendered once coverDataUrl is set so the browser can
      decode it in the background. It stays invisible until imageReady=true,
      at which point displayState switches to "image" and the spinner hides.
    -->
    <img
      v-if="coverDataUrl"
      :src="coverDataUrl"
      alt=""
      class="absolute inset-0 h-full w-full object-cover transition-opacity duration-150"
      :class="displayState === 'image' ? 'opacity-100' : 'opacity-0'"
      decoding="async"
      @load="onImageLoad"
      @error="onImageError"
    />

    </div>

    <!-- Caption: glass overlay on the cover (overlay) or below the cover in the card (below) -->
    <template v-if="cardStyle === 'overlay'">
      <div
        class="pointer-events-none absolute inset-x-0 bottom-0 h-[72px] backdrop-blur-md bg-black/25 [mask-image:linear-gradient(to_top,black_55%,transparent)]"
        aria-hidden="true"
      />
      <div class="absolute inset-x-0 bottom-0 flex h-[72px] items-end gap-1.5 pl-2.5 pr-1 pb-2">
        <div class="min-w-0 flex-1">
          <div v-marquee class="marq text-body-md font-semibold text-white">
            <span class="marq-in">{{ album.album }}</span>
          </div>
          <div v-marquee class="marq text-body-sm text-white/70">
            <span class="marq-in">{{ album.albumArtist }}</span>
          </div>
        </div>
      </div>
    </template>
    <template v-else>
      <div class="mt-0.5 flex items-center gap-2 px-0.5">
        <FeatherIcon name="disc" class="h-4 w-4 shrink-0 text-on-surface-variant" />
        <div class="min-w-0 flex-1">
          <div v-marquee class="marq h-[18px] text-[13px] font-semibold leading-[18px] text-on-surface">
            <span class="marq-in">{{ album.album }}</span>
          </div>
          <div v-marquee class="marq h-[15px] text-[11.5px] leading-[15px] text-on-surface-variant">
            <span class="marq-in">{{ album.albumArtist }}</span>
          </div>
        </div>
      </div>
    </template>
    <!-- Hover actions (Shuffle + Play), play tinted with this album's own accent color -->
    <CardHoverActions :cover-path="album.coverPath" @play="playAlbum" @shuffle="shuffleAlbum" />
  </button>
</template>

