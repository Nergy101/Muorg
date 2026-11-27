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
import { useProfilesStore } from "../../../stores/profiles";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const store = useCatalogStore();
const playlistStore = usePlaylistStore();
const settingsStore = useSettingsStore();
const profilesStore = useProfilesStore();

const connMode = ref<"local" | "online">(getBackendMode());
const connOnlineUrl = ref(getOnlineServerUrl());
const connOnlineApiKey = ref(getOnlineApiKey());
const connOnlineApiKeyVisible = ref(false);
const connStatus = ref<"idle" | "saving" | "ok" | "error">("idle");
const connError = ref("");

async function applyAndReload() {
  connStatus.value = "saving";
  connError.value = "";
  const previousMode = getBackendMode();
  if (connMode.value === "online") {
    setOnlineServerUrl(connOnlineUrl.value.trim());
    setOnlineApiKey(connOnlineApiKey.value.trim());
  }
  settingsStore.setBackendMode(connMode.value);
  // Q32: the queue and selection hold track ids from the other catalog.
  if (connMode.value !== previousMode) store.clearQueueAndSelection();

  await store.loadRoots();
  if (store.error) { connStatus.value = "error"; connError.value = store.error; return; }
  await store.loadTracks();
  if (store.error) { connStatus.value = "error"; connError.value = store.error; return; }
  await playlistStore.loadPlaylists();
  if (playlistStore.error) { connStatus.value = "error"; connError.value = playlistStore.error; return; }

  // NER-326: profiles are server-specific. After connecting to a server with
  // more than one profile, ask which one to listen as (remembered per server).
  profilesStore.bindContext();
  await profilesStore.refresh();
  if (connMode.value === "online" && profilesStore.enabled && !profilesStore.hasStoredChoice()) {
    profilePickerVisible.value = true;
  }

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

// NER-326: connect-time profile choice for servers with multiple profiles.
const profilePickerVisible = ref(false);
function chooseProfile(id: number) {
  profilesStore.setActive(id);
  profilePickerVisible.value = false;
}

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
            <p class="flex items-center gap-2 text-xs font-semibold text-on-surface-variant">
              <FeatherIcon name="wifi" class="h-3.5 w-3.5 shrink-0 text-on-surface-variant" />
              Server Connection
            </p>

            <!-- Local / Online segmented control -->
            <div class="flex rounded-lg border border-outline bg-surface/60 p-0.5 text-xs font-medium">
              <button
                type="button"
                class="flex-1 rounded-md px-4 py-1.5 transition-colors"
                :class="connMode === 'local' ? 'bg-surface-variant text-on-surface shadow-sm' : 'text-on-surface-variant hover:text-on-surface'"
                @click="switchMode('local')"
              >
                Local
              </button>
              <button
                type="button"
                class="flex-1 rounded-md px-4 py-1.5 transition-colors"
                :class="connMode === 'online' ? 'bg-surface-variant text-on-surface shadow-sm' : 'text-on-surface-variant hover:text-on-surface'"
                @click="switchMode('online')"
              >
                Online
              </button>
            </div>

            <!-- Local: no config needed -->
            <p v-if="connMode === 'local'" class="text-[11px] text-on-surface-variant leading-relaxed">
              Connects to MuorgServer running on this machine. No configuration needed.
            </p>

            <!-- Online fields -->
            <div v-else class="settings-section space-y-3">
              <div>
                <label class="mb-1 block text-xs font-medium text-on-surface-variant">Server URL</label>
                <input
                  v-model="connOnlineUrl"
                  type="url"
                  placeholder="https://muorg.example.com"
                  class="w-full rounded border border-outline bg-surface px-3 py-1.5 text-xs text-on-surface placeholder-on-surface-variant focus:border-outline focus:outline-none"
                  @input="connStatus = 'idle'"
                />
              </div>
              <div>
                <label class="mb-1 block text-xs font-medium text-on-surface-variant">API Key</label>
                <div class="flex items-center gap-2">
                  <input
                    v-model="connOnlineApiKey"
                    :type="connOnlineApiKeyVisible ? 'text' : 'password'"
                    placeholder="Enter API key"
                    class="flex-1 rounded border border-outline bg-surface px-3 py-1.5 text-xs text-on-surface placeholder-on-surface-variant focus:border-outline focus:outline-none"
                    @input="connStatus = 'idle'"
                  />
                  <button
                    type="button"
                    class="rounded border border-outline bg-surface-container px-2 py-1.5 text-on-surface-variant hover:bg-surface-variant"
                    @click="connOnlineApiKeyVisible = !connOnlineApiKeyVisible"
                  >
                    <FeatherIcon :name="connOnlineApiKeyVisible ? 'eye-off' : 'eye'" class="h-3.5 w-3.5" />
                  </button>
                </div>
                <p class="mt-1 text-[11px] text-on-surface-variant">Folders list will be read-only in online mode</p>
              </div>
            </div>

            <!-- NER-326: this server has multiple listening profiles — pick one.
                 The choice is stored per server URL. -->
            <div v-if="profilePickerVisible" class="settings-section space-y-2 rounded-lg border border-primary/40 bg-primary/10 p-3">
              <p class="text-xs font-semibold text-on-surface">Which profile are you listening as?</p>
              <p class="text-[11px] text-on-surface-variant">The server has multiple profiles. You can change this later under Listening profiles.</p>
              <div class="flex flex-col gap-1">
                <button
                  v-for="p in profilesStore.profiles"
                  :key="p.id"
                  type="button"
                  class="flex items-center gap-2 rounded-md px-2.5 py-1.5 text-left text-xs transition-colors hover:bg-surface-variant/60"
                  :class="p.id === profilesStore.activeProfileId ? 'text-primary' : 'text-on-surface'"
                  @click="chooseProfile(p.id)"
                >
                  <FeatherIcon name="user" class="h-3.5 w-3.5" />
                  <span class="truncate">{{ p.name }}</span>
                  <span v-if="p.is_default" class="text-[10px] text-on-surface-variant">(default)</span>
                </button>
              </div>
            </div>

            <!-- Q19: device name, reported with every play recorded to the server -->
            <div class="settings-section space-y-3">
              <div>
                <label class="mb-1 block text-xs font-medium text-on-surface-variant">Device name</label>
                <input
                  :value="settingsStore.deviceName"
                  type="text"
                  autocomplete="off"
                  spellcheck="false"
                  aria-label="Device name"
                  placeholder="Muorg on macOS"
                  class="w-full rounded border border-outline bg-surface px-3 py-1.5 text-xs text-on-surface placeholder-on-surface-variant focus:border-outline focus:outline-none"
                  @change="settingsStore.setDeviceName(($event.target as HTMLInputElement).value)"
                />
                <p class="mt-1 text-[11px] text-on-surface-variant">Shown in play history on the server</p>
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

            <!-- Q25: settings live in a per-device file and are not synced. -->
            <p class="text-[11px] text-on-surface-variant leading-relaxed">
              Settings are kept per device and do not sync through the server.
            </p>
          </div>
</template>
