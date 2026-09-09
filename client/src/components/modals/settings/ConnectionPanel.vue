<script setup lang="ts">
/**
 * Connection tab: local sidecar vs remote server, and the credentials for it.
 *
 * Switching backend reloads the whole catalog, so every step reports its own
 * failure — a half-applied switch is worse than a refused one.
 */
import { ref } from "vue";
import {
  getBackendMode,
  getOnlineServerUrl, setOnlineServerUrl, getOnlineApiKey, setOnlineApiKey,
} from "../../../api/client";
import { useCatalogStore } from "../../../stores/catalog";
import { usePlaylistStore } from "../../../stores/playlists";
import { useSettingsStore } from "../../../stores/settings";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const store = useCatalogStore();
const playlistStore = usePlaylistStore();
const settingsStore = useSettingsStore();

const connMode = ref<"local" | "online">(getBackendMode());
const connOnlineUrl = ref(getOnlineServerUrl());
const connOnlineApiKey = ref(getOnlineApiKey());
const connOnlineApiKeyVisible = ref(false);
const connStatus = ref<"idle" | "saving" | "ok" | "error">("idle");
const connError = ref("");

async function applyAndReload() {
  connStatus.value = "saving";
  connError.value = "";
  if (connMode.value === "online") {
    setOnlineServerUrl(connOnlineUrl.value.trim());
    setOnlineApiKey(connOnlineApiKey.value.trim());
  }
  settingsStore.setBackendMode(connMode.value);

  await store.loadRoots();
  if (store.error) { connStatus.value = "error"; connError.value = store.error; return; }
  await store.loadTracks();
  if (store.error) { connStatus.value = "error"; connError.value = store.error; return; }
  await playlistStore.loadPlaylists();
  if (playlistStore.error) { connStatus.value = "error"; connError.value = playlistStore.error; return; }

  connStatus.value = "ok";
}

async function switchMode(mode: "local" | "online") {
  connMode.value = mode;
  connStatus.value = "idle";
  connError.value = "";
  // Local needs no credentials — apply immediately
  if (mode === "local") await applyAndReload();
}

const refreshStatus = ref<"idle" | "loading" | "ok" | "error">("idle");
const refreshError = ref("");

async function refreshFromServer() {
  refreshStatus.value = "loading";
  refreshError.value = "";
  store.clearCoverCache();
  await store.loadRoots();
  if (store.error) { refreshStatus.value = "error"; refreshError.value = store.error; return; }
  await store.loadTracks();
  if (store.error) { refreshStatus.value = "error"; refreshError.value = store.error; return; }
  await playlistStore.loadPlaylists();
  if (playlistStore.error) { refreshStatus.value = "error"; refreshError.value = playlistStore.error; return; }
  refreshStatus.value = "ok";
}
</script>

<template>
          <div class="w-full space-y-4">
            <p class="flex items-center gap-2 text-xs font-semibold text-stone-400">
              <FeatherIcon name="wifi" class="h-3.5 w-3.5 shrink-0 text-stone-500" />
              Server Connection
            </p>

            <!-- Local / Online segmented control -->
            <div class="flex rounded-lg border border-stone-600 bg-stone-900/60 p-0.5 text-xs font-medium">
              <button
                type="button"
                class="flex-1 rounded-md px-4 py-1.5 transition-colors"
                :class="connMode === 'local' ? 'bg-stone-600 text-stone-100 shadow-sm' : 'text-stone-400 hover:text-stone-200'"
                @click="switchMode('local')"
              >
                Local
              </button>
              <button
                type="button"
                class="flex-1 rounded-md px-4 py-1.5 transition-colors"
                :class="connMode === 'online' ? 'bg-stone-600 text-stone-100 shadow-sm' : 'text-stone-400 hover:text-stone-200'"
                @click="switchMode('online')"
              >
                Online
              </button>
            </div>

            <!-- Local: no config needed -->
            <p v-if="connMode === 'local'" class="text-[11px] text-stone-500 leading-relaxed">
              Connects to MuorgServer running on this machine. No configuration needed.
            </p>

            <!-- Online fields -->
            <div v-else class="settings-section space-y-3">
              <div>
                <label class="mb-1 block text-xs font-medium text-stone-400">Server URL</label>
                <input
                  v-model="connOnlineUrl"
                  type="url"
                  placeholder="https://muorg.example.com"
                  class="w-full rounded border border-stone-600 bg-stone-900 px-3 py-1.5 text-xs text-stone-200 placeholder-stone-600 focus:border-stone-400 focus:outline-none"
                  @input="connStatus = 'idle'"
                />
              </div>
              <div>
                <label class="mb-1 block text-xs font-medium text-stone-400">API Key</label>
                <div class="flex items-center gap-2">
                  <input
                    v-model="connOnlineApiKey"
                    :type="connOnlineApiKeyVisible ? 'text' : 'password'"
                    placeholder="Enter API key"
                    class="flex-1 rounded border border-stone-600 bg-stone-900 px-3 py-1.5 text-xs text-stone-200 placeholder-stone-600 focus:border-stone-400 focus:outline-none"
                    @input="connStatus = 'idle'"
                  />
                  <button
                    type="button"
                    class="rounded border border-stone-600 bg-stone-800 px-2 py-1.5 text-stone-400 hover:bg-stone-700"
                    @click="connOnlineApiKeyVisible = !connOnlineApiKeyVisible"
                  >
                    <FeatherIcon :name="connOnlineApiKeyVisible ? 'eye-off' : 'eye'" class="h-3.5 w-3.5" />
                  </button>
                </div>
                <p class="mt-1 text-[11px] text-stone-500">Folders list will be read-only in online mode</p>
              </div>
            </div>

            <!-- Actions -->
            <div class="flex items-center gap-3">
              <button
                type="button"
                class="settings-action-btn rounded px-3 py-1.5 text-xs font-medium disabled:opacity-50"
                :disabled="connStatus === 'saving'"
                @click="applyAndReload"
              >
                {{ connStatus === 'saving' ? 'Connecting…' : 'Save & reload' }}
              </button>
              <span v-if="connStatus === 'ok'" class="flex items-center gap-1.5 text-xs text-green-400">
                <FeatherIcon name="check-circle" class="h-3.5 w-3.5" /> Connected
              </span>
              <span v-if="connStatus === 'error'" class="flex items-center gap-1.5 text-xs text-red-400">
                <FeatherIcon name="x-circle" class="h-3.5 w-3.5" /> {{ connError || 'Failed' }}
              </span>
            </div>

            <!-- Refresh -->
            <div class="flex items-center gap-3">
              <button
                type="button"
                class="settings-action-btn rounded px-3 py-1.5 text-xs font-medium disabled:opacity-50"
                :disabled="refreshStatus === 'loading'"
                @click="refreshFromServer"
              >
                <span class="flex items-center gap-1.5">
                  <FeatherIcon :name="refreshStatus === 'loading' ? 'loader' : 'refresh-cw'" class="h-3 w-3" :class="refreshStatus === 'loading' ? 'animate-spin' : ''" />
                  {{ refreshStatus === 'loading' ? 'Refreshing…' : 'Refresh data from server' }}
                </span>
              </button>
              <span v-if="refreshStatus === 'ok'" class="flex items-center gap-1.5 text-xs text-green-400">
                <FeatherIcon name="check-circle" class="h-3.5 w-3.5" /> Done
              </span>
              <span v-if="refreshStatus === 'error'" class="flex items-center gap-1.5 text-xs text-red-400">
                <FeatherIcon name="x-circle" class="h-3.5 w-3.5" /> {{ refreshError || 'Failed' }}
              </span>
            </div>
          </div>
</template>
