<template>
  <!-- id anchors the "Backups OFF" deep link from the album/track editors. -->
  <div id="backup-before-write">
    <div :class="ROW">
      <div class="min-w-0 flex-1">
        <p class="text-body-lg text-on-surface">Back up before writing</p>
        <p class="text-body-sm text-on-surface-variant">
          Keep a server-side copy of the original file before every single-track edit
        </p>
      </div>
      <button
        type="button"
        :class="switchClass(settings.backupBeforeWrite)"
        role="switch"
        aria-label="Back up before writing"
        :data-tip="`Back up before writing is ${settings.backupBeforeWrite ? 'on' : 'off'} — click to turn ${settings.backupBeforeWrite ? 'off' : 'on'}`"
        :aria-checked="settings.backupBeforeWrite"
        @click="settings.setBackupBeforeWrite(!settings.backupBeforeWrite)"
      >
        <span :class="knobClass(settings.backupBeforeWrite)" />
      </button>
    </div>

    <button type="button" :class="[ROW, 'w-full text-left']" @click="router.push({ name: 'path-formats' })">
      <div class="min-w-0 flex-1">
        <p class="text-body-lg text-on-surface">Apply from path formats</p>
        <p class="text-body-sm text-on-surface-variant">
          {{ settings.pathFormatTemplates.filter((t) => t.trim()).length }} pattern{{ settings.pathFormatTemplates.filter((t) => t.trim()).length === 1 ? "" : "s" }} — used by "Apply from path" in the metadata editor
        </p>
      </div>
      <MageIcon name="chevron-right" class="h-5 w-5 shrink-0 text-on-surface-variant" />
    </button>
  </div>
</template>

<script setup lang="ts">
/**
 * Metadata-writing settings block: whether writes back up the original file,
 * plus a link to the "Apply from path" formats subpage (the template editor
 * itself lives in PathFormatsView). Mirrors the desktop app's Smart
 * Suggestions panel.
 */
import { useRouter } from "vue-router";
import MageIcon from "../MageIcon.vue";
import { useSettingsStore } from "../../stores/settings";

const ROW = "flex min-h-14 items-center justify-between gap-4 px-4 py-2";

const router = useRouter();
const settings = useSettingsStore();

function switchClass(on: boolean): string {
  return `flex h-8 w-[52px] shrink-0 items-center rounded-full px-1 transition-colors duration-200 ${
    on ? "bg-primary" : "bg-surface-variant"
  }`;
}

function knobClass(on: boolean): string {
  return `block h-6 w-6 rounded-full bg-white transition-transform duration-200 ${
    on ? "translate-x-5" : "translate-x-0"
  }`;
}
</script>
