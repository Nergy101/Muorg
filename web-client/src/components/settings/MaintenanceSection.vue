<template>
  <div>
    <div :class="[SECTION, 'flex items-center gap-1.5']">
      <MageIcon name="trash" class="h-3.5 w-3.5" />
      <span>Maintenance</span>
    </div>
    <div :class="ROW">
      <div class="min-w-0 flex-1">
        <p class="text-body-lg text-on-surface">Purge removed tracks</p>
        <p class="text-body-sm text-on-surface-variant">
          Permanently drops catalog rows for tracks already removed, bypassing the 30-day grace period
        </p>
      </div>
      <button
        type="button"
        class="px-2 text-label-lg text-error disabled:opacity-60"
        :disabled="folders.purging"
        @click="confirmOpen = true"
      >{{ folders.purging ? "Purging…" : "Purge" }}</button>
    </div>

    <ConfirmDialog
      :open="confirmOpen"
      title="Purge removed tracks?"
      message="This permanently drops catalog rows for tracks whose folder was removed, bypassing the normal 30-day grace period. This cannot be undone."
      confirm-label="Purge"
      danger
      @confirm="onConfirmPurge"
      @cancel="confirmOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * Maintenance block, mounted in SettingsView: a single destructive action —
 * permanently purging soft-deleted catalog rows ahead of their grace period
 * (`POST /api/admin/purge-deleted`). This is not a "clear cache" action: it
 * drops real catalog data, hence the confirmation.
 */
import { ref } from "vue";
import MageIcon from "../MageIcon.vue";
import ConfirmDialog from "../ConfirmDialog.vue";
import { useFoldersStore } from "../../stores/folders";

const SECTION = "px-4 pt-5 pb-1 text-label-sm uppercase tracking-[0.8px] text-primary";
const ROW = "flex min-h-14 items-center justify-between gap-4 px-4 py-2";

const folders = useFoldersStore();
const confirmOpen = ref(false);

async function onConfirmPurge(): Promise<void> {
  confirmOpen.value = false;
  try {
    await folders.purgeDeleted();
  } catch {
    // The store already surfaced a toast with the server's message.
  }
}
</script>
