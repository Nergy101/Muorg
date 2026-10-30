<template>
  <div
    class="flex h-14 w-full select-none items-center gap-3 px-4 text-left transition-colors lg:cursor-pointer"
    :class="onDark ? 'lg:hover:bg-white/10' : 'lg:hover:bg-surface-container/50'"
    @click="emit('play')"
    @touchstart.passive="lp.onTouchstart"
    @touchmove.passive="lp.onTouchmove"
    @touchend="lp.onTouchend"
    @contextmenu.prevent="emit('actions')"
  >
    <!-- Leading: equalizer while playing, else cover / index / placeholder -->
    <div
      class="relative flex h-12 w-12 shrink-0 items-center justify-center overflow-hidden rounded"
      :class="onDark ? 'bg-white/10' : 'bg-surface-variant'"
    >
      <img
        v-if="coverUrl"
        :src="coverUrl"
        :alt="track.album ?? ''"
        class="h-12 w-12 rounded object-cover"
        decoding="async"
      />
      <span
        v-else-if="leading === 'index'"
        class="text-body-md tabular-nums"
        :class="onDark ? 'text-white/60' : 'text-on-surface-variant'"
      >{{ track.track_number ?? "·" }}</span>
      <MageIcon v-else name="music" class="h-5 w-5" :class="onDark ? 'text-white/50' : 'text-on-surface-variant/60'" />
      <div
        v-if="isPlaying"
        class="absolute inset-0 flex items-center justify-center rounded bg-black/45"
      >
        <EqualizerBars class="text-primary" :paused="!player.isPlaying" />
      </div>
    </div>

    <!-- Title + artist (format tag and duration sit on the artist's line so
         the title row keeps the full width) -->
    <div class="min-w-0 flex-1">
      <MarqueeText
        :text="track.title ?? '—'"
        :class="titleClass"
      />
      <div class="flex min-w-0 items-center gap-1.5">
        <div class="min-w-0 flex-1">
          <MarqueeText
            :text="track.artist ?? track.album_artist ?? '—'"
            class="text-body-md"
            :class="onDark ? 'text-white/75' : 'text-on-surface-variant'"
          />
        </div>
        <span
          v-if="showFormatBadge !== false"
          class="ml-auto shrink-0 rounded-[3px] px-1 py-[1px] text-[10px] leading-none uppercase"
          :class="isLossless(track.format)
            ? 'bg-primary/[0.12] text-primary'
            : onDark
              ? 'bg-white/10 text-white/70'
              : 'bg-on-surface-variant/10 text-on-surface-variant'"
        >{{ track.format.toUpperCase() }}</span>
        <span
          v-if="showDuration !== false"
          class="shrink-0 text-[10px] leading-none tabular-nums"
          :class="onDark ? 'text-white/55' : 'text-on-surface-variant'"
        >{{ duration }}</span>
      </div>
    </div>

    <button
      v-if="showDelete"
      type="button"
      class="-mr-2 flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-error transition-colors hover:bg-error-container/60"
      aria-label="Delete from server"
      @click.stop="emit('delete')"
    >
      <MageIcon name="trash" class="h-5 w-5" />
    </button>

    <button
      type="button"
      class="-mr-2 flex h-9 w-9 shrink-0 items-center justify-center rounded-full transition-colors"
      :class="onDark ? 'text-white/75 hover:bg-white/10' : 'text-on-surface-variant hover:bg-surface-container/70'"
      aria-label="Track actions"
      @click.stop="emit('actions')"
    >
      <MageIcon name="dots" class="h-5 w-5" />
    </button>
  </div>
</template>

<script setup lang="ts">
import { computed } from "vue";
import MageIcon from "./MageIcon.vue";
import EqualizerBars from "@shared/components/EqualizerBars.vue";
import { isLossless } from "@shared/formats";
import MarqueeText from "./MarqueeText.vue";
import { useLibraryStore, formatDuration } from "../stores/library";
import { usePlayerStore } from "../stores/player";
import { useLongPress } from "../composables/useLongPress";
import type { CatalogTrack } from "../types";

const props = withDefaults(
  defineProps<{
    track: CatalogTrack;
    leading?: "cover" | "index";
    isPlaying?: boolean;
    showFormatBadge?: boolean;
    showDuration?: boolean;
    /** Show a destructive "delete from server" button before the actions dot. */
    showDelete?: boolean;
    /** Render for a dark cover-derived backdrop (white text instead of theme). */
    onDark?: boolean;
  }>(),
  {
    leading: "cover",
    showFormatBadge: true,
    showDuration: true,
    showDelete: false,
    onDark: false,
  },
);

const emit = defineEmits<{ play: []; actions: []; delete: [] }>();

const titleClass = computed(() =>
  props.isPlaying
    ? "text-body-lg text-primary"
    : props.onDark
      ? "text-body-lg text-white"
      : "text-body-lg text-on-surface",
);

const lib = useLibraryStore();
// `isPlaying` means "this row is the current track"; the store says whether
// audio is actually running, which is what freezes the bars.
const player = usePlayerStore();
const lp = useLongPress(() => emit("actions"));

const coverUrl = computed(() => {
  if (props.leading !== "cover" || !props.track.has_cover) return null;
  lib.requestCover(props.track.id);
  return lib.coverCache.get(props.track.id) ?? null;
});

const duration = computed(() =>
  props.track.duration_secs ? formatDuration(props.track.duration_secs) : "",
);
</script>
