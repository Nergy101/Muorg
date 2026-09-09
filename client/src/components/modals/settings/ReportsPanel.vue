<script setup lang="ts">
/** Reports tab: which fields count as "missing metadata". */
import { storeToRefs } from "pinia";
import { useSettingsStore } from "../../../stores/settings";
import type { MissingMetadataField } from "../../../stores/settings";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const settingsStore = useSettingsStore();
const { missingMetadataFields } = storeToRefs(settingsStore);

const missingMetadataFieldOptions: {
  value: MissingMetadataField;
  label: string;
}[] = [
  { value: "title", label: "Title" },
  { value: "artist", label: "Artist" },
  { value: "album", label: "Album" },
  { value: "album_artist", label: "Album artist" },
  { value: "year", label: "Year" },
  { value: "genre", label: "Genre" },
  { value: "track_number", label: "Track #" },
  { value: "disc_number", label: "Disc #" },
  { value: "rating", label: "Rating" },
  { value: "has_cover", label: "Album Cover" },
];
</script>

<template>
            <div class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="bar-chart-2"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                Reports
              </p>

              <div class="settings-section">
                <label class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                  <input
                    type="checkbox"
                    :checked="settingsStore.hideReportsSection"
                    class="rounded border-stone-600"
                    @change="(e) => settingsStore.setHideReportsSection((e.target as HTMLInputElement).checked)"
                  />
                  Hide reports section in sidebar
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, the reports block (Missing metadata, Duplicates, Missing album cover) is hidden from the main sidebar layout.
                </p>
              </div>

              <div class="settings-section">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="list"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Missing metadata fields
                </p>
                <p class="mb-1 text-xs text-stone-500">
                  Choose which fields must be present for a track to be
                  considered "complete". Tracks missing any of these fields will
                  appear in the "Missing metadata" report.
                </p>
                <div class="grid grid-cols-2 gap-1">
                  <label
                    v-for="opt in missingMetadataFieldOptions"
                    :key="opt.value"
                    class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                  >
                    <input
                      type="checkbox"
                      :checked="missingMetadataFields.includes(opt.value)"
                      class="rounded border-stone-600"
                      @change="
                        (e) => {
                          const checked = (e.target as HTMLInputElement)
                            .checked;
                          const set = new Set(missingMetadataFields);
                          if (checked) set.add(opt.value);
                          else set.delete(opt.value);
                          settingsStore.setMissingMetadataFields(
                            Array.from(set),
                          );
                        }
                      "
                    />
                    {{ opt.label }}
                  </label>
                </div>
              </div>
            </div>

</template>
