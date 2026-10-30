<template>
  <div>
    <div :class="[SECTION, 'flex items-center gap-1.5']">
      <MageIcon name="edit" class="h-3.5 w-3.5" />
      <span>Metadata writing</span>
    </div>

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
        :aria-checked="settings.backupBeforeWrite"
        @click="settings.setBackupBeforeWrite(!settings.backupBeforeWrite)"
      >
        <span :class="knobClass(settings.backupBeforeWrite)" />
      </button>
    </div>

    <div class="px-4 py-2">
      <p class="text-body-lg text-on-surface">Apply from path formats</p>
      <p class="pb-2 text-body-sm text-on-surface-variant">
        One pattern per folder layout. "Apply from path" in the metadata editor tries every
        pattern and uses whichever extracts the most fields.
      </p>

      <div class="space-y-1.5">
        <div v-for="(template, i) in settings.pathFormatTemplates" :key="i" class="flex items-center gap-1.5">
          <input
            type="text"
            :value="template"
            class="min-w-0 flex-1 rounded-xl bg-surface-variant px-3 py-2 font-mono text-body-sm text-on-surface outline-none"
            placeholder="e.g. <Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>"
            @input="updateTemplate(i, ($event.target as HTMLInputElement).value)"
          />
          <button
            type="button"
            class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-on-surface-variant"
            aria-label="Remove pattern"
            @click="removeTemplate(i)"
          >
            <MageIcon name="trash" class="h-3.5 w-3.5" />
          </button>
        </div>
        <button type="button" class="flex items-center gap-1 text-label-lg text-primary" @click="addTemplate">
          <MageIcon name="plus" class="h-3.5 w-3.5" />
          Add pattern
        </button>
      </div>

      <p class="mt-3 text-body-sm text-on-surface-variant">
        Placeholders: <code class="font-mono">&lt;Artist&gt;</code>, <code class="font-mono">&lt;AlbumArtist&gt;</code>,
        <code class="font-mono">&lt;Album&gt;</code>, <code class="font-mono">&lt;Year&gt;</code>,
        <code class="font-mono">&lt;Genre&gt;</code>, <code class="font-mono">&lt;TrackNumber&gt;</code>,
        <code class="font-mono">&lt;DiscNumber&gt;</code>, <code class="font-mono">&lt;TrackTitle&gt;</code>,
        <code class="font-mono">&lt;Featuring&gt;</code>, <code class="font-mono">&lt;Format&gt;</code>
      </p>

      <p class="mt-3 text-body-sm text-on-surface-variant">Examples (tap to add):</p>
      <ul class="mt-1 space-y-1">
        <li v-for="ex in PATH_FORMAT_EXAMPLES" :key="ex">
          <button
            type="button"
            class="w-full break-all rounded-xl bg-surface-variant/60 px-3 py-1.5 text-left font-mono text-body-sm text-on-surface-variant disabled:opacity-40"
            :disabled="settings.pathFormatTemplates.includes(ex)"
            @click="addExample(ex)"
          >{{ ex }}</button>
        </li>
      </ul>

      <div class="mt-3 rounded-xl bg-surface-variant/60 p-3">
        <div class="flex items-center justify-between gap-2">
          <p class="text-body-sm text-on-surface-variant">Try a path</p>
          <button type="button" class="shrink-0 text-label-sm text-primary" @click="examplePath = DEFAULT_EXAMPLE_PATH">
            Reset
          </button>
        </div>
        <input
          v-model="examplePath"
          type="text"
          class="mt-1.5 w-full rounded-xl bg-surface px-3 py-2 font-mono text-body-sm text-on-surface outline-none"
          placeholder="e.g. /music/Artist/Album/01 - Title.flac"
        />
        <div v-if="settings.pathFormatTemplates.some((t) => t.trim())" class="mt-2 border-t border-outline/15 pt-2">
          <template v-if="extracted">
            <table class="mt-1 w-full border-collapse text-body-sm">
              <tbody>
                <tr v-for="(val, key) in extracted" :key="key" class="border-b border-outline/10">
                  <td class="py-1 pr-3 font-mono text-on-surface-variant">{{ key }}</td>
                  <td class="py-1 text-on-surface">{{ val || "—" }}</td>
                </tr>
              </tbody>
            </table>
          </template>
          <p v-else class="text-body-sm text-amber-500">No pattern matches this path.</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * "Apply from path" settings block, mounted inside SettingsView's Library
 * section: whether writes back up the original file, plus the path-format
 * templates the metadata editor's "Apply from path" tries against a track's
 * file path. Mirrors the desktop app's Smart Suggestions panel.
 */
import { computed, ref } from "vue";
import MageIcon from "../MageIcon.vue";
import { useSettingsStore } from "../../stores/settings";
import { extractBestFromPath } from "@shared/utils/pathFormat";

const SECTION = "px-4 pt-5 pb-1 text-label-sm uppercase tracking-[0.8px] text-primary";
const ROW = "flex min-h-14 items-center justify-between gap-4 px-4 py-2";

const PATH_FORMAT_EXAMPLES = [
  "<Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>",
  "<Artist>/Albums/<Year> - <Album>/<TrackNumber> - <TrackTitle>.<Format>",
  "<AlbumArtist>/<Album>/<DiscNumber>-<TrackNumber> <TrackTitle>.<Format>",
  "<Genre>/<Artist>/<Year> - <Album>/<TrackNumber> - <TrackTitle>.<Format>",
  "<Artist> - <Album>/<TrackNumber> - <TrackTitle>.<Format>",
];

const DEFAULT_EXAMPLE_PATH = "/music/Linkin Park/Meteora/01 - Foreword.flac";

const settings = useSettingsStore();
const examplePath = ref(DEFAULT_EXAMPLE_PATH);

const extracted = computed(() => {
  const templates = settings.pathFormatTemplates;
  const path = examplePath.value.trim();
  if (!templates.some((t) => t.trim()) || !path) return null;
  return extractBestFromPath(templates, path);
});

function updateTemplate(i: number, value: string): void {
  const next = [...settings.pathFormatTemplates];
  next[i] = value;
  settings.setPathFormatTemplates(next);
}

function removeTemplate(i: number): void {
  settings.setPathFormatTemplates(settings.pathFormatTemplates.filter((_, idx) => idx !== i));
}

function addTemplate(): void {
  settings.setPathFormatTemplates([...settings.pathFormatTemplates, ""]);
}

function addExample(pattern: string): void {
  if (!settings.pathFormatTemplates.includes(pattern)) {
    settings.setPathFormatTemplates([...settings.pathFormatTemplates, pattern]);
  }
}

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
