import { computed, ref, watch } from "vue";
import { usePlayerStore } from "../stores/player";
import { api } from "../api/client";
import { offlineStreamUrl } from "../stores/offline";
import { probeAudioStats, type AudioStats } from "../audio/audioStats";
import type { CatalogTrack } from "../types";

/**
 * Probe one track's stream URL (tokened online, SW-cached offline URL when
 * the server is down) and return its bitrate / sample rate, or null when the
 * stream can't be read.
 */
async function probeTrack(track: CatalogTrack): Promise<AudioStats | null> {
  let url: string;
  try {
    const info = await api.getStreamTokenInfo(track.id);
    url = api.streamUrlForTrack(track, info);
  } catch {
    url = offlineStreamUrl(track.id);
  }
  try {
    return await probeAudioStats(url, track.format ?? "", track.duration_secs ?? null);
  } catch {
    return null;
  }
}

/**
 * Bitrate / sample-rate of the track the player is playing — read from the
 * stream bytes themselves (see audio/audioStats.ts), so it reflects the live
 * stream (e.g. a transcoded MP3) as well as a passthrough file.
 *
 * The state is module-level and the watcher is installed once, so every
 * consumer (maximized player panel, mini player, fullscreen bottom bar)
 * shares a single probe. Re-probes whenever the current track changes; a
 * stale probe for a previous track is discarded via the sequence counter.
 */
const stats = ref<AudioStats | null>(null);
const format = ref<string>("");
let seq = 0;
let watching = false;

export function useAudioStats() {
  if (!watching) {
    watching = true;
    const player = usePlayerStore();
    watch(
      () => player.currentTrack,
      async (track) => {
        const my = ++seq;
        stats.value = null;
        format.value = "";
        if (!track) return;
        format.value = track.format?.toUpperCase() ?? "";
        const probed = await probeTrack(track);
        if (my === seq) stats.value = probed;
      },
      { immediate: true },
    );
  }

  /** e.g. "353 kbps · 44.1 kHz"; missing parts are omitted. */
  const statsLabel = computed(() => {
    const parts: string[] = [];
    if (stats.value?.bitrateKbps != null) parts.push(`${stats.value.bitrateKbps} kbps`);
    if (stats.value?.sampleRateHz != null) {
      const khz = stats.value.sampleRateHz / 1000;
      parts.push(`${Number.isInteger(khz) ? khz : khz.toFixed(1)} kHz`);
    }
    return parts.join(" · ");
  });

  return { stats, formatTag: format, statsLabel, probeTrack };
}
