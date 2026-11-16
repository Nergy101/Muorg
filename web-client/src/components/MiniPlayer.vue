<template>
  <!-- Error surface: no track, but something went wrong -->
  <div
    v-if="!player.currentTrack && player.errorMessage"
    class="shrink-0 bg-error-container px-4 py-2 text-body-sm text-on-error-container"
    role="alert"
  >
    {{ player.errorMessage }}
  </div>

  <div
    v-else-if="player.currentTrack"
    class="relative shrink-0"
  >
    <!-- Progress line at the bottom of the mini player, on the flat edge above
         the tabs — not the rounded top, where it would clip at the corners. -->
    <div class="absolute inset-x-0 bottom-0 h-0.5 overflow-hidden bg-surface-variant/60">
      <div class="h-full" :style="{ width: `${player.progress * 100}%`, backgroundColor: accentCss ?? undefined }" />
    </div>

    <!-- Bar spans the shell, its controls stay grouped with the rest of the UI.
         Desktop (lg) widens it to the whole content pane: art + info left,
         transport centred in the bar, queue right. Not `content-col` — the
         unlayered .content-col rule would beat the lg:max-w-none utility. -->
    <div
      class="mx-auto flex h-16 w-full max-w-[600px] items-center md:max-w-[900px] lg:h-[72px] lg:max-w-none lg:px-6"
      @contextmenu.prevent="menuOpen = true"
    >
      <!-- Tap body -->
      <div class="flex min-w-0 flex-1 items-center self-stretch" @click="onBodyTap">
        <div
          class="aspect-square h-full shrink-0 overflow-hidden rounded-tl-md bg-surface-variant lg:h-14 lg:rounded-tl-none"
        >
          <img
            v-if="player.currentCoverUrl"
            :src="player.currentCoverUrl"
            :alt="player.currentTrack.album ?? ''"
            class="h-full w-full object-cover"
          />
          <div v-else class="flex h-full w-full items-center justify-center">
            <MageIcon name="music" class="h-5 w-5 text-on-surface-variant/60" />
          </div>
        </div>

        <div class="min-w-0 flex-1 px-3">
          <MarqueeText
            :text="player.currentTrack.title ?? '—'"
            class="text-body-md text-on-surface"
          />
          <div class="truncate text-body-sm text-on-surface-variant">
            <ArtistLink :name="player.currentTrack.artist ?? player.currentTrack.album_artist" />
          </div>
          <div
            v-if="cast.isCasting"
            class="flex items-center gap-1 text-label-sm text-primary"
          >
            <MageIcon name="screencast" class="h-3 w-3" />
            <span class="truncate">{{ cast.deviceName ?? "Casting" }}</span>
          </div>
          <div
            v-if="player.sleepTimerActive"
            class="flex items-center gap-1 text-label-sm text-primary"
          >
            <MageIcon name="moon" class="h-3 w-3" />
            <span>{{ sleepLabel }}</span>
          </div>
        </div>
      </div>

      <!-- Desktop transport in its own centre zone: with the left and right
           zones both flex-1, it stays centred in the bar and can never overlap
           the right-side controls (e.g. the volume slider shown when the rail
           is collapsed). -->
      <div class="hidden shrink-0 items-center gap-1 lg:flex">
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container hover:text-on-surface"
          :style="accentStyle"
          aria-label="Previous track"
          @click="player.skipPrevious()"
        >
          <MageIcon name="previous" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="mx-1 flex h-11 w-11 items-center justify-center rounded-full transition-transform hover:scale-105"
          :class="accentCss ? '' : 'bg-primary text-on-primary'"
          :style="accentCss ? { backgroundColor: accentCss, color: accentOnCss ?? undefined } : undefined"
          :aria-label="transportPlaying ? 'Pause' : 'Play'"
          @click="togglePlay"
        >
          <MageIcon v-if="isLoading" name="refresh" class="h-5 w-5 animate-spin" />
          <MageIcon v-else :name="transportPlaying ? 'pause' : 'play'" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container hover:text-on-surface"
          :style="accentStyle"
          aria-label="Next track"
          @click="player.skipNext()"
        >
          <MageIcon name="next" class="h-5 w-5" />
        </button>
      </div>

      <!-- Right zone: flex-1 mirrors the left zone so the transport stays
           centred, but WITHOUT min-w-0 — flex items then refuse to shrink
           below their content width, so the volume slider and buttons can
           never overflow leftwards onto the transport's next button. -->
      <div class="flex flex-1 items-center justify-end">
      <!-- Volume only when the sidebar rail is collapsed — expanded, it lives
           in the nav's footer instead. -->
      <div v-if="sidebarCollapsed" class="hidden shrink-0 items-center pr-1 lg:flex">
        <VolumeControl />
      </div>

      <div class="flex shrink-0 items-center gap-0.5 pr-2">
        <!-- Format tag of the playing file/stream, left of the favourite;
             desktop only, matching the favourite button. Hover shows the
             bitrate / sample-rate stats. -->
        <AudioFormatTag
          class="hidden h-8 text-on-surface-variant hover:bg-surface-container hover:text-on-surface lg:flex"
        />
        <button
          type="button"
          class="hidden h-10 w-10 items-center justify-center rounded-full transition-colors hover:bg-surface-container lg:flex"
          :class="isFavorite ? '' : 'text-on-surface-variant'"
          :style="isFavorite && accentCss ? { color: accentCss } : accentStyle"
          :aria-label="isFavorite ? 'Remove from favorites' : 'Add to favorites'"
          @click="onToggleFavorite"
        >
          <MageIcon name="heart" class="h-5 w-5" :class="isFavorite ? 'fill-current' : ''" />
        </button>
        <button
          type="button"
          class="hidden h-10 w-10 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container lg:flex"
          :style="accentStyle"
          aria-label="Add to playlist"
          @click="openAddToPlaylist"
        >
          <MageIcon name="playlist-add" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container"
          aria-label="Track actions"
          @click="menuOpen = true"
        >
          <MageIcon name="dots" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full transition-colors hover:bg-surface-container"
          :class="route.name === 'queue' ? 'text-primary' : 'text-on-surface-variant'"
          aria-label="Queue"
          @click="onQueueClick"
        >
          <MageIcon name="stack" class="h-5 w-5" />
        </button>
        <!-- Hidden in an installed PWA: no browser chrome there. -->
        <button
          v-if="popoutSupported && !isPwa"
          type="button"
          class="hidden h-10 w-10 items-center justify-center rounded-full transition-colors hover:bg-surface-container lg:flex"
          :class="pipActive ? 'text-primary' : 'text-on-surface-variant'"
          :aria-label="pipActive ? 'Close popout player' : 'Pop out player'"
          @click="void togglePopout()"
        >
          <MageIcon name="arrow-up-right" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-on-surface lg:hidden"
          :aria-label="transportPlaying ? 'Pause' : 'Play'"
          @click="togglePlay"
        >
          <MageIcon v-if="isLoading" name="refresh" class="h-6 w-6 animate-spin" />
          <MageIcon v-else :name="transportPlaying ? 'pause' : 'play'" class="h-6 w-6" />
        </button>
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-on-surface-variant lg:hidden"
          aria-label="Next track"
          @click="player.skipNext()"
        >
          <MageIcon name="next" class="h-5 w-5" />
        </button>
      </div>
      </div>
    </div>
  </div>

  <TrackActionsSheet
    :open="menuOpen"
    :initial-level="actionsInitialLevel"
    :track="player.currentTrack"
    @close="menuOpen = false"
    @view-artist="onViewArtist"
    @view-album="onViewAlbum"
  />
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import MageIcon from "./MageIcon.vue";
import MarqueeText from "./MarqueeText.vue";
import ArtistLink from "./ArtistLink.vue";
import AudioFormatTag from "./AudioFormatTag.vue";
import TrackActionsSheet from "./TrackActionsSheet.vue";
import VolumeControl from "./VolumeControl.vue";
import { sidebarCollapsed } from "../composables/sidebarCollapsed";
import { usePopoutPlayer } from "../composables/usePopoutPlayer";
import { useIsPwa } from "../composables/useIsPwa";
import { usePlayerStore } from "../stores/player";
import { rawMainColor, useDominantColor } from "../composables/useDominantColor";
import { useSettingsStore } from "../stores/settings";
import { useLibraryStore } from "../stores/library";
import { useCastStore } from "../stores/cast";

const route = useRoute();
const router = useRouter();
const player = usePlayerStore();

// Accent = dominant color of the current track's cover — the same source the
// circular visualizer in the full player uses, so the mini player's transport
// and progress line color-match it.
const glowRgb = useDominantColor(computed(() => player.currentCoverUrl));
const accentCss = computed(() => (glowRgb.value ? `rgb(${rawMainColor(glowRgb.value)})` : null));
const accentStyle = computed(() => (accentCss.value ? { color: accentCss.value } : undefined));
// Contrasting icon color for the filled round play button (accent background).
const accentOnCss = computed(() => {
  if (!glowRgb.value) return null;
  const [r, g, b] = rawMainColor(glowRgb.value).split(",").map(Number);
  const lum = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
  return lum > 0.6 ? "rgba(0,0,0,0.85)" : "rgba(255,255,255,0.95)";
});
const cast = useCastStore();

/** While casting, the device is the thing that is playing, not this tab. */
const transportPlaying = computed(() =>
  cast.isCasting ? cast.isPlaying : player.isPlaying,
);

/** Spinner on the play button while the current track is still loading. */
const isLoading = computed(
  () =>
    !cast.isCasting &&
    (player.isBuffering ||
      (player.pendingTrackId !== null && player.pendingTrackId === player.currentTrack?.id)),
);

function togglePlay(): void {
  if (!cast.isCasting) {
    player.playPause();
    return;
  }
  void (cast.isPlaying ? cast.pause() : cast.resume());
}
const settings = useSettingsStore();
const lib = useLibraryStore();

const menuOpen = ref(false);
const actionsInitialLevel = ref<"main" | "playlists">("main");
const { pipActive, supported: popoutSupported, togglePopout } = usePopoutPlayer();
const isPwa = useIsPwa();

/** The "+" jumps straight to the sheet's playlist picker. */
function openAddToPlaylist(): void {
  actionsInitialLevel.value = "playlists";
  menuOpen.value = true;
}

const isFavorite = computed(() =>
  player.currentTrack ? player.favorites.has(player.currentTrack.id) : false,
);

function onToggleFavorite(): void {
  const t = player.currentTrack;
  if (!t) return;
  void player.toggleFavorite(t);
}

function onViewArtist(): void {
  const t = player.currentTrack;
  if (!t) return;
  const name = t.artist ?? t.album_artist;
  menuOpen.value = false;
  if (name) void router.push({ name: "artist", params: { name } });
}

function onViewAlbum(): void {
  const t = player.currentTrack;
  if (!t) return;
  menuOpen.value = false;
  void router.push({ name: "album", params: { albumKey: lib.keyForTrack(t) } });
}

const sleepLabel = computed(() => {
  const total = Math.ceil(player.sleepTimerRemainingMs / 1000);
  return `${Math.floor(total / 60)}m ${total % 60}s`;
});

function onBodyTap(): void {
  if (settings.miniPlayerTapOpensPlayer) void router.push({ name: "player" });
  else player.playPause();
}

/** Queue icon doubles as a toggle: already looking at the queue → back. */
function onQueueClick(): void {
  if (route.name === "queue") {
    if (window.history.state?.back) router.back();
    else void router.push({ name: "home" });
  } else {
    void router.push({ name: "queue" });
  }
}
</script>
