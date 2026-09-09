<script setup lang="ts">
/**
 * Embedded lyrics for the maximized player.
 *
 * `GET /api/tracks/{id}/lyrics` returns whatever the file carries plus a
 * `sync_format`. When that is `"lrc"` the text has `[mm:ss]` timestamps and the
 * pane follows the playhead karaoke-style; otherwise it is shown as a plain
 * block. Parsing lives in `@shared/lyrics`, shared with the web client.
 */
import { computed, ref, watch } from "vue";
import { getTrackLyrics } from "../../api/catalog";
import { activeLrcIndex, parseLrc, type LrcLine } from "@shared/lyrics";
import type { TrackLyrics } from "@shared/api";

const props = defineProps<{
  trackId: number | null;
  /** Playhead in seconds, for following synced lyrics. */
  positionSecs: number;
}>();

const emit = defineEmits<{ (e: "availability", hasLyrics: boolean): void }>();

const lyrics = ref<TrackLyrics | null>(null);
const lrcLines = ref<LrcLine[]>([]);
const lineEls = ref<HTMLElement[]>([]);

const isSynced = computed(
  () => lyrics.value?.sync_format === "lrc" && lrcLines.value.length > 0,
);

const activeIndex = computed(() =>
  isSynced.value ? activeLrcIndex(lrcLines.value, props.positionSecs) : -1,
);

watch(activeIndex, (i) => {
  lineEls.value[i]?.scrollIntoView({ block: "center", behavior: "smooth" });
});

watch(
  () => props.trackId,
  (id) => {
    lyrics.value = null;
    lrcLines.value = [];
    emit("availability", false);
    if (id == null) return;
    const requested = id;
    void getTrackLyrics(id).then((l) => {
      // A fast next/previous can land an older response after a newer one.
      if (props.trackId !== requested) return;
      lyrics.value = l;
      if (l?.sync_format === "lrc") lrcLines.value = parseLrc(l.lyrics);
      emit("availability", l != null);
    });
  },
  { immediate: true },
);
</script>

<template>
  <div v-if="lyrics" class="mpx-lyrics">
    <template v-if="isSynced">
      <p
        v-for="(line, i) in lrcLines"
        :key="i"
        ref="lineEls"
        class="mpx-lyrics-line"
        :class="{ 'mpx-lyrics-line--active': i === activeIndex }"
      >{{ line.text }}</p>
    </template>
    <p v-else class="mpx-lyrics-plain">{{ lyrics.lyrics }}</p>
  </div>
</template>
