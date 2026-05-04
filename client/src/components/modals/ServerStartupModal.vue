<script setup lang="ts">
import { onMounted, onUnmounted } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../../stores/catalog";
import { usePlaylistStore } from "../../stores/playlists";
import { useSettingsStore } from "../../stores/settings";
import { getServerUrl } from "../../api/client";

const store = useCatalogStore();
const playlistStore = usePlaylistStore();
const settingsStore = useSettingsStore();
const { backendMode, localServerReady } = storeToRefs(settingsStore);

let pollTimer: ReturnType<typeof setTimeout> | null = null;

async function checkHealth(): Promise<boolean> {
  try {
    const res = await fetch(`${getServerUrl()}/api/health`, {
      signal: AbortSignal.timeout(1500),
    });
    return res.ok;
  } catch {
    return false;
  }
}

async function onServerReady() {
  await store.loadRoots();
  await store.loadTracks();
  await playlistStore.loadPlaylists();
  if (settingsStore.sessionQueueTrackIds.length > 0) {
    store.restoreSession(settingsStore.sessionQueueTrackIds);
  }
  settingsStore.localServerReady = true;
}

async function poll() {
  if (await checkHealth()) {
    await onServerReady();
  } else {
    pollTimer = setTimeout(poll, 600);
  }
}

onMounted(() => {
  if (backendMode.value !== "local") {
    settingsStore.localServerReady = true;
    return;
  }
  poll();
});

onUnmounted(() => {
  if (pollTimer) clearTimeout(pollTimer);
});
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-200"
      leave-active-class="transition-opacity duration-300"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div
        v-if="backendMode === 'local' && !localServerReady"
        class="fixed inset-0 z-[999] flex items-center justify-center bg-stone-900/80 backdrop-blur-sm"
      >
        <div class="flex flex-col items-center gap-5 rounded-xl border border-stone-700 bg-stone-800 px-12 py-10 shadow-[0_20px_60px_rgba(0,0,0,0.6)]">
          <svg
            class="h-9 w-9 animate-spin text-stone-400"
            viewBox="0 0 24 24"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <circle class="opacity-20" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="3" />
            <path
              class="opacity-80"
              fill="currentColor"
              d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
            />
          </svg>
          <p class="text-sm font-medium tracking-wide text-stone-300">
            Starting local Muorg server…
          </p>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
