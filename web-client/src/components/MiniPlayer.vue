<template>
  <!-- Error surface: no track, but something went wrong -->
  <div
    v-if="!player.currentTrack && player.errorMessage"
    class="shrink-0 bg-error-container px-4 py-2 text-body-sm text-on-error-container"
    role="alert"
  >
    {{ player.errorMessage }}
  </div>

  <div v-else-if="player.currentTrack" class="relative shrink-0 bg-surface shadow-lg">
    <!-- Progress line stays on the bar, not the content column: a 900px line
         floating mid-bar would read as broken rather than as progress. -->
    <div class="absolute inset-x-0 top-0 h-0.5 bg-surface-variant">
      <div class="h-full bg-primary" :style="{ width: `${player.progress * 100}%` }" />
    </div>

    <!-- Bar spans the shell, its controls stay grouped with the rest of the UI. -->
    <div class="content-col flex h-16 items-center">
      <!-- Tap body -->
      <div class="flex min-w-0 flex-1 items-center self-stretch" @click="onBodyTap">
        <div class="aspect-square h-full shrink-0 bg-surface-variant">
          <img
            v-if="player.currentCoverUrl"
            :src="player.currentCoverUrl"
            :alt="player.currentTrack.album ?? ''"
            class="h-full w-full object-cover"
          />
          <div v-else class="flex h-full w-full items-center justify-center">
            <FeatherIcon name="music" class="h-5 w-5 text-on-surface-variant/60" />
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
            <FeatherIcon name="moon" class="h-3 w-3" />
            <span>{{ sleepLabel }}</span>
          </div>
        </div>
      </div>

      <div class="flex shrink-0 items-center gap-0.5 pr-2">
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-on-surface-variant"
          aria-label="Queue"
          @click="router.push({ name: 'queue' })"
        >
          <FeatherIcon name="list" class="h-5 w-5" />
        </button>
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-on-surface"
          :aria-label="player.isPlaying ? 'Pause' : 'Play'"
          @click="player.playPause()"
        >
          <FeatherIcon :name="player.isPlaying ? 'pause' : 'play'" class="h-6 w-6" />
        </button>
        <button
          type="button"
          class="flex h-10 w-10 items-center justify-center rounded-full text-on-surface-variant"
          aria-label="Next track"
          @click="player.skipNext()"
        >
          <FeatherIcon name="skip-forward" class="h-5 w-5" />
        </button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import MarqueeText from "./MarqueeText.vue";
import { usePlayerStore } from "../stores/player";
import { useSettingsStore } from "../stores/settings";

const router = useRouter();
const player = usePlayerStore();
const settings = useSettingsStore();

const sleepLabel = computed(() => {
  const total = Math.ceil(player.sleepTimerRemainingMs / 1000);
  return `${Math.floor(total / 60)}m ${total % 60}s`;
});

function onBodyTap(): void {
  if (settings.miniPlayerTapOpensPlayer) void router.push({ name: "player" });
  else player.playPause();
}
</script>
