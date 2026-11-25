<script setup lang="ts">
/** Exports tab: playlist export defaults. */
import { computed } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../../stores/catalog";
import { useSettingsStore } from "../../../stores/settings";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { musicRootFolder, backupBeforeWrite } = storeToRefs(settingsStore);

/** Falls back to the single loaded folder's name, else "Music". */
function musicRootFolderBasename(path: string): string {
  const segments = path.split(/[/\\]/).filter(Boolean);
  return segments[segments.length - 1] ?? "Music";
}
const musicRootFolderPlaceholder = computed(() =>
  store.roots.length === 1 ? musicRootFolderBasename(store.roots[0]) : "Music",
);
</script>

<template>
            <div class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-on-surface-variant"
              >
                <FeatherIcon
                  name="download"
                  class="h-3.5 w-3.5 shrink-0 text-on-surface-variant"
                />
                Exports
              </p>

              <div class="settings-section">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-on-surface-variant"
                >
                  <FeatherIcon
                    name="folder"
                    class="h-3.5 w-3.5 shrink-0 text-on-surface-variant"
                  />
                  Music Root Folder
                </p>
                <input
                  :value="musicRootFolder"
                  type="text"
                  :placeholder="musicRootFolderPlaceholder"
                  class="mt-1 w-full rounded border border-outline bg-surface-container px-2.5 py-1.5 text-xs text-on-surface placeholder-on-surface-variant focus:border-outline focus:outline-none focus:ring-1 focus:ring-outline"
                  @input="
                    settingsStore.setMusicRootFolder(
                      ($event.target as HTMLInputElement).value,
                    )
                  "
                />
                <p class="mt-1.5 text-xs text-on-surface-variant">
                  Enter just the folder name (e.g.
                  <code class="rounded bg-surface-variant px-1 font-mono text-[11px]"
                    >Music</code
                  >), not a full path. This should be the root folder that is
                  scanned into your library. It is used to generate relative
                  paths inside exported playlist files.
                </p>
              </div>
              <div class="settings-section">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-on-surface-variant"
                >
                  <FeatherIcon
                    name="shield"
                    class="h-3.5 w-3.5 shrink-0 text-on-surface-variant"
                  />
                  Metadata Backup
                </p>
                <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-on-surface-variant">
                  <input
                    type="checkbox"
                    :checked="backupBeforeWrite"
                    class="rounded border-outline"
                    @change="settingsStore.setBackupBeforeWrite(($event.target as HTMLInputElement).checked)"
                  />
                  Backup file before metadata writes
                </label>
                <p class="mt-1.5 text-xs text-on-surface-variant">
                  When enabled, Muorg creates a backup copy before writing tags.
                  This gives you a safety net and enables quick restore from the
                  Metadata panel if an edit goes wrong.
                </p>
              </div>
            </div>

</template>
