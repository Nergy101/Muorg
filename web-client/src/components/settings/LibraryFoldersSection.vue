<template>
  <div>
    <div :class="[SECTION, 'flex items-center gap-1.5']">
      <MageIcon name="stack" class="h-3.5 w-3.5" />
      <span>Library Folders</span>
    </div>

    <p
      v-if="!folders.loading && folders.roots.length === 0"
      class="px-4 py-2 text-body-sm text-on-surface-variant"
    >
      No folders configured yet — add one below.
    </p>

    <div v-for="root in folders.roots" :key="root" :class="ROW">
      <div class="min-w-0 flex-1">
        <p class="truncate text-body-lg text-on-surface">{{ root }}</p>
        <p class="text-body-sm text-on-surface-variant">
          {{ trackCount(root).toLocaleString() }} track{{ trackCount(root) === 1 ? "" : "s" }}
        </p>
      </div>
      <button
        type="button"
        class="px-2 text-label-lg text-primary disabled:opacity-60"
        :disabled="folders.scanningRoot !== null"
        @click="folders.rescanOne(root)"
      >{{ folders.scanningRoot === root ? "Rescanning…" : "Rescan" }}</button>
      <button
        type="button"
        class="px-2 text-label-lg text-error disabled:opacity-60"
        :disabled="folders.removingRoot !== null"
        @click="removeTarget = root"
      >{{ folders.removingRoot === root ? "Removing…" : "Remove" }}</button>
    </div>

    <div v-if="folders.roots.length > 0" :class="ROW">
      <div class="min-w-0 flex-1">
        <p class="text-body-lg text-on-surface">Rescan all folders</p>
        <p class="text-body-sm text-on-surface-variant">Re-reads tags for every configured folder</p>
      </div>
      <button
        type="button"
        class="px-2 text-label-lg text-primary disabled:opacity-60"
        :disabled="folders.scanningRoot !== null"
        @click="folders.rescanAll()"
      >{{ folders.scanningRoot === "*" ? "Rescanning…" : "Rescan all" }}</button>
    </div>

    <button type="button" :class="[ROW, 'w-full text-left']" @click="pickerOpen = true">
      <div class="flex min-w-0 flex-1 items-center gap-3">
        <MageIcon name="plus" class="h-5 w-5 shrink-0 text-primary" />
        <p class="text-body-lg text-primary">Add folder</p>
      </div>
    </button>

    <FolderPickerSheet :open="pickerOpen" @close="pickerOpen = false" />

    <ConfirmDialog
      :open="removeTarget !== null"
      title="Remove folder?"
      :message="removeMessage"
      confirm-label="Remove"
      danger
      @confirm="onConfirmRemove"
      @cancel="removeTarget = null"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * Library folders block, mounted inside SettingsView's Library section:
 * lists configured roots with per-folder track counts, rescan (one or all),
 * remove (soft-delete, with a confirmation), and "Add folder" — which opens
 * `FolderPickerSheet` to browse the server's own disk.
 */
import { computed, onMounted, ref } from "vue";
import MageIcon from "../MageIcon.vue";
import ConfirmDialog from "../ConfirmDialog.vue";
import FolderPickerSheet from "../FolderPickerSheet.vue";
import { useFoldersStore } from "../../stores/folders";

const SECTION = "px-4 pt-5 pb-1 text-label-sm uppercase tracking-[0.8px] text-primary";
const ROW = "flex min-h-14 items-center justify-between gap-4 px-4 py-2";

const folders = useFoldersStore();
const pickerOpen = ref(false);
const removeTarget = ref<string | null>(null);
const removeMessage = computed(
  () =>
    `Tracks under “${removeTarget.value}” leave the catalog and are purged after the grace period. Files on disk are left untouched.`,
);

onMounted(() => {
  void folders.loadRoots();
});

function trackCount(root: string): number {
  return folders.rootTrackCounts.get(root) ?? 0;
}

async function onConfirmRemove(): Promise<void> {
  const root = removeTarget.value;
  removeTarget.value = null;
  if (!root) return;
  try {
    await folders.removeFolder(root);
  } catch {
    // The store already surfaced a toast with the server's message.
  }
}
</script>
