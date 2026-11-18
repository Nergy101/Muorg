<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div class="content-col flex h-14 shrink-0 items-center gap-1 px-2">
      <button
        type="button"
        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface transition-colors lg:w-auto lg:gap-1 lg:px-3 lg:text-label-lg lg:hover:bg-surface-container"
        aria-label="Back"
        @click="router.back()"
      >
        <MageIcon name="chevron-left" class="h-5 w-5" />
        <span class="hidden lg:inline">Back</span>
      </button>

      <div class="min-w-0 flex-1">
        <h1 class="truncate text-title-md text-on-surface">Apply from path formats</h1>
        <p class="text-body-sm text-on-surface-variant">One pattern per folder layout</p>
      </div>
    </div>

    <div class="content-col min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
      <div class="px-4 py-3">
        <p class="text-body-sm text-on-surface-variant">
          "Apply from path" in the metadata editor tries every pattern below against a track's file
          path and uses whichever extracts the most fields.
        </p>

        <div class="mt-3 space-y-1.5">
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
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import { useSettingsStore } from "../stores/settings";
import { extractBestFromPath, PATH_FORMAT_EXAMPLES } from "@shared/utils/pathFormat";

const router = useRouter();
const settings = useSettingsStore();

const DEFAULT_EXAMPLE_PATH = "/music/Linkin Park/Meteora/01 - Foreword.flac";
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
</script>
