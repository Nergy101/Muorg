<script setup lang="ts">
/**
 * Smart Suggestions tab: path templates that fill tags in from a file's own
 * path, previewed against a sample so the user can see what a template will do
 * before it touches anything.
 */
import { computed } from "vue";
import { storeToRefs } from "pinia";
import { useSettingsStore, DEFAULT_PATH_FORMAT_EXAMPLE_PATH } from "../../../stores/settings";
import { extractBestFromPath, extractMetadataFromPath, buildUpdateFromExtracted } from "../../../utils/pathFormat";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const settingsStore = useSettingsStore();
const { pathFormatTemplates, pathFormatExamplePath, hideWikipediaCoverSearch } =
  storeToRefs(settingsStore);

const pathFormatExamples = [
  "<Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>",
  "<Artist>/Albums/<Year> - <Album>/<TrackNumber> - <TrackTitle>.<Format>",
  "<AlbumArtist>/<Album>/<DiscNumber>-<TrackNumber> <TrackTitle>.<Format>",
  "<Genre>/<Artist>/<Year> - <Album>/<TrackNumber> - <TrackTitle>.<Format>",
  "<Artist> - <Album>/<TrackNumber> - <TrackTitle>.<Format>",
];

const pathFormatExamplePaths = [
  "/music/Linkin Park/Meteora/01 - Foreword.flac",
  "/library/Linkin Park/Albums/2003 - Meteora/04 - Faint.flac",
  "/music/Linkin Park/Meteora/1-04 Faint.flac",
  "/music/Rock/Linkin Park/2003 - Meteora/04 - Faint.flac",
  "/music/Linkin Park - Meteora/04 - Faint.flac",
];

const pathFormatExampleExtracted = computed(() => {
  const templates = pathFormatTemplates.value;
  const examplePath = pathFormatExamplePath.value?.trim();
  if (!templates.some((t) => t.trim()) || !examplePath) return null;
  return extractBestFromPath(templates, examplePath);
});

const pathFormatExampleBestPattern = computed(() => {
  const templates = pathFormatTemplates.value;
  const examplePath = pathFormatExamplePath.value?.trim();
  if (!templates.some((t) => t.trim()) || !examplePath) return null;
  let best: string | null = null;
  let bestScore = -1;
  for (const template of templates) {
    const trimmed = template.trim();
    if (!trimmed) continue;
    const extracted = extractMetadataFromPath(trimmed, examplePath);
    if (!extracted) continue;
    const score = Object.keys(buildUpdateFromExtracted(extracted)).length;
    if (score > bestScore) { best = trimmed; bestScore = score; }
  }
  return best;
});

function addExamplePattern(pattern: string) {
  if (!pathFormatTemplates.value.includes(pattern)) {
    settingsStore.setPathFormatTemplates([...pathFormatTemplates.value, pattern]);
  }
}

function updateTemplate(i: number, value: string) {
  const updated = [...pathFormatTemplates.value];
  updated[i] = value;
  settingsStore.setPathFormatTemplates(updated);
}

function removeTemplate(i: number) {
  settingsStore.setPathFormatTemplates(pathFormatTemplates.value.filter((_, idx) => idx !== i));
}

function addTemplate() {
  settingsStore.setPathFormatTemplates([...pathFormatTemplates.value, ""]);
}

</script>

<template>
            <div class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="zap"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                Smart Suggestions
              </p>

              <div class="settings-section space-y-1.5">
                <label
                  class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="hideWikipediaCoverSearch"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setHideWikipediaCoverSearch(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Hide Wikipedia album cover search
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, the "From Wikipedia" (globe) button for album
                  art is hidden in the metadata editor and on album group
                  headers.
                </p>
              </div>

              <div class="settings-section space-y-3">
                <div>
                  <label class="block text-xs font-medium text-stone-500">Path formats (for metadata suggestions)</label>
                  <p class="mt-0.5 text-xs text-stone-500">
                    Add one pattern per folder structure. When applying from path, all patterns are tried and the one that extracts the most fields wins.
                  </p>

                  <!-- Active patterns list -->
                  <div class="mt-2 space-y-1.5">
                    <div
                      v-for="(template, i) in pathFormatTemplates"
                      :key="i"
                      class="flex items-center gap-1.5"
                    >
                      <input
                        type="text"
                        :value="template"
                        class="min-w-0 flex-1 rounded border border-stone-600 bg-stone-900 px-2 py-1.5 font-mono text-xs text-stone-200"
                        placeholder="e.g. <Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>"
                        @input="updateTemplate(i, ($event.target as HTMLInputElement).value)"
                      />
                      <button
                        type="button"
                        class="shrink-0 rounded p-1 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
                        title="Remove pattern"
                        @click="removeTemplate(i)"
                      >
                        <FeatherIcon name="x" class="h-3.5 w-3.5" />
                      </button>
                    </div>
                    <button
                      type="button"
                      class="mt-1 flex items-center gap-1 text-xs text-stone-500 hover:text-stone-300"
                      @click="addTemplate"
                    >
                      <FeatherIcon name="plus" class="h-3.5 w-3.5" />
                      Add pattern
                    </button>
                  </div>

                  <!-- Example patterns to add -->
                  <p class="mt-3 text-xs font-medium text-stone-500">
                    Example patterns (click to add):
                  </p>
                  <ul class="mt-0.5 space-y-0.5 text-xs">
                    <li v-for="(ex, i) in pathFormatExamples" :key="i">
                      <button
                        type="button"
                        class="path-format-example-btn w-full break-all rounded border px-2 py-1 font-mono text-left"
                        :class="pathFormatTemplates.includes(ex) ? 'opacity-40 cursor-default' : ''"
                        :title="pathFormatTemplates.includes(ex) ? 'Already added' : 'Add this pattern'"
                        @click="addExamplePattern(ex)"
                      >
                        {{ i + 1 }}. {{ ex }}
                      </button>
                    </li>
                  </ul>
                </div>
              </div>

              <div class="settings-section space-y-3">
                <div>
                  <p class="text-xs font-medium text-stone-500">
                    Matching path examples (click to try):
                  </p>
                  <ul class="mt-0.5 space-y-0.5 text-xs">
                    <li
                      v-for="(p, i) in pathFormatExamplePaths"
                      :key="'path-' + i"
                    >
                      <button
                        type="button"
                        class="path-format-example-btn w-full break-all rounded border px-2 py-1 font-mono text-left"
                        @click="settingsStore.setPathFormatExamplePath(p)"
                      >
                        {{ i + 1 }}. {{ p }}
                      </button>
                    </li>
                  </ul>
                </div>

                <div class="mt-2 rounded border border-stone-600 bg-stone-900/70 p-3">
                  <div class="flex items-center justify-between gap-2">
                    <p class="text-xs font-medium text-stone-400">Try your path</p>
                    <button
                      type="button"
                      class="shrink-0 rounded border border-stone-600 px-2 py-0.5 text-xs text-stone-500 hover:bg-stone-600 hover:text-stone-200"
                      title="Restore default example path"
                      @click="settingsStore.setPathFormatExamplePath(DEFAULT_PATH_FORMAT_EXAMPLE_PATH)"
                    >
                      Reset to default
                    </button>
                  </div>
                  <input
                    type="text"
                    :value="pathFormatExamplePath"
                    class="mt-1.5 w-full rounded border border-stone-600 bg-stone-900 px-2 py-1.5 font-mono text-xs text-stone-200 placeholder:text-stone-500"
                    placeholder="e.g. /path/to/Artist/Album/01 - Title.flac"
                    @input="(e) => settingsStore.setPathFormatExamplePath((e.target as HTMLInputElement).value)"
                  />
                  <div v-if="pathFormatTemplates.some(t => t.trim())" class="mt-2 border-t border-stone-700/60 pt-2">
                    <div v-if="pathFormatExampleExtracted">
                      <p class="text-[11px] text-stone-500">
                        Matched by:
                        <span class="font-mono text-stone-400">{{ pathFormatExampleBestPattern }}</span>
                      </p>
                      <p class="mt-1.5 text-xs font-medium text-stone-400">Extracted fields</p>
                      <table class="mt-1.5 w-full border-collapse text-xs">
                        <thead>
                          <tr class="border-b border-stone-600">
                            <th class="py-1.5 pr-3 text-left font-medium text-stone-500">Field</th>
                            <th class="py-1.5 text-left font-medium text-stone-500">Value</th>
                          </tr>
                        </thead>
                        <tbody>
                          <tr
                            v-for="(val, key) in pathFormatExampleExtracted"
                            :key="key"
                            class="border-b border-stone-700/50"
                          >
                            <td class="py-1.5 pr-3 font-mono text-stone-400">{{ key }}</td>
                            <td class="py-1.5 text-stone-300">{{ val || "—" }}</td>
                          </tr>
                        </tbody>
                      </table>
                    </div>
                    <p v-else class="mt-1 text-xs text-amber-500">
                      No pattern matches the example path.
                    </p>
                  </div>
                </div>
              </div>
            </div>
          <!-- ── Smart Transform ───────────────────────────────────── -->
</template>
