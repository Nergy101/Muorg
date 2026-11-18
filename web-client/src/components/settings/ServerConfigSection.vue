<template>
  <div>
    <div v-if="loading" class="flex items-center gap-2 px-4 py-3 text-body-md text-on-surface-variant">
      <MageIcon name="refresh" class="h-4 w-4 animate-spin" />
      <span>Loading server settings…</span>
    </div>
    <div v-else-if="error" class="px-4 py-3 text-body-md text-on-surface-variant">
      Could not load the server settings ({{ error }}).
    </div>
    <template v-else>
      <div v-for="section in sections" :key="section.name" class="pb-1">
        <p class="px-4 pb-1 pt-3 text-label-sm font-semibold uppercase tracking-[0.8px] text-on-surface-variant">
          [{{ section.name }}]
        </p>
        <div
          v-for="e in section.entries"
          :key="e.key"
          class="flex min-h-10 items-center justify-between gap-4 px-4 py-1.5"
        >
          <span class="min-w-0 truncate text-body-md text-on-surface-variant">{{ e.key }}</span>
          <span
            class="min-w-0 truncate text-right text-body-md tabular-nums"
            :class="e.secret ? 'text-on-surface-variant' : 'text-on-surface'"
            :title="e.value"
          >{{ e.value }}</span>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from "vue";
import MageIcon from "../MageIcon.vue";
import { api } from "../../api/client";
import type { ConfigSectionView } from "@shared/api";

/**
 * The server's TOML settings, fetched from `GET /api/admin/config`. Secrets
 * arrive already redacted by the server (asterisks + last 3 chars) — the full
 * secret is never sent over the network, so rendering `value` as-is is safe.
 */
const sections = ref<ConfigSectionView[]>([]);
const loading = ref(true);
const error = ref("");

onMounted(async () => {
  try {
    sections.value = await api.getServerConfig();
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e);
  } finally {
    loading.value = false;
  }
});
</script>
