<template>
  <div>
    <div :class="[SECTION, 'flex items-center gap-1.5']">
      <MageIcon name="playlist" class="h-3.5 w-3.5" />
      <span>Export</span>
    </div>
    <div :class="ROW">
      <div class="min-w-0 flex-1">
        <p class="text-body-lg text-on-surface">Music root folder</p>
        <p class="text-body-sm text-on-surface-variant">
          Used by "Relative paths" M3U export — the folder name in your
          library paths that the exported playlist is written relative to.
        </p>
      </div>
      <input
        type="text"
        :value="settings.musicRootFolder"
        placeholder="e.g. Music"
        autocomplete="off"
        spellcheck="false"
        aria-label="Music root folder"
        class="w-36 rounded-lg bg-surface px-3 py-1.5 text-right text-body-md text-on-surface outline-none focus:ring-2 focus:ring-primary"
        @change="settings.setMusicRootFolder(($event.target as HTMLInputElement).value)"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * Export settings block, mounted once in SettingsView: the music root folder
 * name used by the playlist M3U export's "Relative paths" mode (see
 * PlaylistExportDialog / composables/useM3u). Tracks whose path doesn't
 * contain this folder as a segment are skipped during that export.
 */
import MageIcon from "../MageIcon.vue";
import { useSettingsStore } from "../../stores/settings";

const SECTION = "px-4 pt-5 pb-1 text-label-sm uppercase tracking-[0.8px] text-primary";
const ROW = "flex min-h-14 items-center justify-between gap-4 px-4 py-2";

const settings = useSettingsStore();
</script>
