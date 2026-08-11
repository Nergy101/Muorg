<template>
  <!-- Error surface: no track, but something went wrong -->
  <div
    v-if="!player.currentTrack && player.errorMessage"
    class="shrink-0 bg-error-container px-4 py-2 text-body-sm text-on-error-container"
    role="alert"
  >
    {{ player.errorMessage }}
  </div>

  <div v-else-if="player.currentTrack" class="relative shrink-0 bg-surface lg:border-t lg:border-outline/20">
    <!-- Progress line stays on the bar, not the content column: a 900px line
         floating mid-bar would read as broken rather than as progress. -->
    <div class="absolute inset-x-0 top-0 h-0.5 bg-surface-variant">
      <div class="h-full bg-primary" :style="{ width: `${player.progress * 100}%` }" />
    </div>

    <!-- Bar spans the shell, its controls stay grouped with the rest of the UI.
         Desktop (lg) widens it to the whole content pane: art + info left,
         transport centred in the bar, queue right. Not `content-col` — the
         unlayered .content-col rule would beat the lg:max-w-none utility. -->
    <div
      class="mx-auto flex h-16 w-full max-w-[600px] items-center md:max-w-[900px] lg:h-[72px] lg:max-w-none lg:px-6"
    >
      <!-- Tap body -->
      <div class="flex min-w-0 flex-1 items-center self-stretch" @click="onBodyTap">
        <div
          class="aspect-square h-full shrink-0 bg-surface-variant lg:h-14 lg:rounded-md"
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
            {{ player.currentTrack.artist ?? player.currentTrack.album_artist ?? "—" }}
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

      <!-- Desktop transport, centred against the full bar -->
      <div
        class="absolute left-1/2 hidden -translate-x-1/2 items-center gap-1 lg:flex"
      >
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container hover:text-on-surface"
          aria-label="Previous track"
          @click="player.skipPrevious()"
        >
          <MageIcon name="previous" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="mx-1 flex h-11 w-11 items-center justify-center rounded-full bg-primary text-on-primary transition-transform hover:scale-105"
          :aria-label="player.isPlaying ? 'Pause' : 'Play'"
          @click="player.playPause()"
        >
          <MageIcon :name="player.isPlaying ? 'pause' : 'play'" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-on-surface-variant transition-colors hover:bg-surface-container hover:text-on-surface"
          aria-label="Next track"
          @click="player.skipNext()"
        >
          <MageIcon name="next" class="h-5 w-5" />
        </button>
      </div>

      <div class="flex shrink-0 items-center gap-0.5 pr-2">
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full transition-colors hover:bg-surface-container"
          :class="route.name === 'queue' ? 'text-primary' : 'text-on-surface-variant'"
          aria-label="Queue"
          @click="onQueueClick"
        >
          <MageIcon name="stack" class="h-5 w-5" />
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
          class="flex h-10 w-10 items-center justify-center rounded-full text-on-surface lg:hidden"
          :aria-label="player.isPlaying ? 'Pause' : 'Play'"
          @click="player.playPause()"
        >
          <MageIcon :name="player.isPlaying ? 'pause' : 'play'" class="h-6 w-6" />
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

  <TrackActionsSheet
    :open="menuOpen"
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
import TrackActionsSheet from "./TrackActionsSheet.vue";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";
import { useLibraryStore } from "../stores/library";

const route = useRoute();
const router = useRouter();
const player = usePlayerStore();
const settings = useSettingsStore();
const lib = useLibraryStore();

const menuOpen = ref(false);

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
