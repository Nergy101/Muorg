<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div class="flex min-h-0 flex-1 flex-col items-center justify-center gap-6 overflow-y-auto px-6">
      <div class="flex w-full max-w-sm flex-col items-center gap-6">
        <img :src="logoUrl" alt="" class="h-24 w-24" />

        <div class="flex flex-col items-center gap-1 text-center">
          <h1 class="text-headline-lg text-primary">Muorg</h1>
          <p class="text-body-md text-on-surface-variant">Connect to your Muorg server</p>
        </div>

        <form class="w-full space-y-4" @submit.prevent="connect">
          <div>
            <label for="server-url" class="mb-1 block text-body-sm text-on-surface-variant">Server URL</label>
            <input
              id="server-url"
              v-model="url"
              type="url"
              inputmode="url"
              autocomplete="off"
              spellcheck="false"
              placeholder="http://192.168.1.100:7700"
              class="w-full rounded-xl bg-surface px-4 py-3 text-body-lg text-on-surface placeholder:text-on-surface-variant outline-none focus:ring-2 focus:ring-primary"
            />
          </div>

          <div>
            <label for="api-key" class="mb-1 block text-body-sm text-on-surface-variant">API Key</label>
            <div class="relative">
              <input
                id="api-key"
                v-model="apiKey"
                :type="showKey ? 'text' : 'password'"
                autocomplete="current-password"
                class="w-full rounded-xl bg-surface px-4 py-3 text-body-lg text-on-surface placeholder:text-on-surface-variant outline-none focus:ring-2 focus:ring-primary"
              />
              <button
                type="button"
                class="absolute inset-y-0 right-3 flex items-center text-on-surface-variant"
                :aria-label="showKey ? 'Hide API key' : 'Show API key'"
                :aria-pressed="showKey"
                @click="showKey = !showKey"
              >
                <MageIcon :name="showKey ? 'eye-off' : 'eye'" class="h-5 w-5" />
              </button>
            </div>
          </div>

          <p v-if="errorMsg" role="alert" class="text-body-sm text-error">{{ errorMsg }}</p>

          <button
            type="submit"
            :disabled="busy"
            class="flex w-full items-center justify-center rounded-full bg-primary px-4 py-3 text-label-lg text-on-primary disabled:opacity-60"
          >
            <MageIcon v-if="busy" name="refresh" class="h-5 w-5 animate-spin" />
            <span v-else>Connect</span>
          </button>
        </form>

        <!-- NER-326: a server with more than one listening profile asks which
             one to use. The choice is remembered per server URL. -->
        <div v-if="choosingProfile" class="w-full rounded-xl bg-surface p-4">
          <p class="mb-1 text-body-md font-medium text-on-surface">Which profile are you listening as?</p>
          <p class="mb-3 text-body-sm text-on-surface-variant">
            This server has multiple listening profiles. You can change it later in Settings.
          </p>
          <div class="flex flex-col gap-2">
            <button
              v-for="p in profilesStore.profiles"
              :key="p.id"
              type="button"
              class="flex items-center gap-2 rounded-lg px-3 py-2 text-left text-body-md transition-colors hover:bg-surface-variant/60"
              :class="p.id === profilesStore.activeProfileId ? 'text-primary' : 'text-on-surface'"
              @click="chooseProfile(p.id)"
            >
              <MageIcon name="user" class="h-4 w-4" />
              <span class="truncate">{{ p.name }}</span>
              <span v-if="p.is_default" class="text-body-sm text-on-surface-variant">(default)</span>
            </button>
          </div>
        </div>

        <p class="text-center text-body-sm text-on-surface-variant/70">
          The server URL and API key are saved locally in your browser.
        </p>
        <p class="text-center text-body-sm text-on-surface-variant/70">
          Settings are kept per device and do not sync through the server.
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import MageIcon from "../components/MageIcon.vue";
import { getServerUrl, getApiKey, setServerUrl, setApiKey, testConnection } from "../api/client";
import { useProfilesStore } from "../stores/profiles";
import { router, loadData } from "../router";
import logoUrl from "../assets/muorg-logo.svg";

const url = ref(getServerUrl());
const apiKey = ref(getApiKey());
const showKey = ref(false);
const busy = ref(false);
const errorMsg = ref("");
const choosingProfile = ref(false);
const profilesStore = useProfilesStore();

async function proceed(): Promise<void> {
  await loadData();
  router.replace({ name: "library" });
}

function chooseProfile(id: number): void {
  profilesStore.setActive(id);
  choosingProfile.value = false;
  void proceed();
}

async function connect(): Promise<void> {
  errorMsg.value = "";
  const u = url.value.trim();
  const k = apiKey.value.trim();
  if (!u || !k) {
    errorMsg.value = "Please enter both server URL and API key";
    return;
  }
  busy.value = true;
  try {
    await testConnection(u, k);
    setServerUrl(u);
    setApiKey(k);
    // Profiles are server-specific: load this server's list and ask which
    // one to listen as when there is more than one and no choice was stored.
    profilesStore.bindContext();
    await profilesStore.refresh();
    if (profilesStore.enabled && !profilesStore.hasStoredChoice()) {
      choosingProfile.value = true;
      return;
    }
    await proceed();
  } catch (e) {
    const message = e instanceof TypeError ? "" : (e as Error).message;
    errorMsg.value = message || "Could not reach server. Check the URL and try again.";
  } finally {
    busy.value = false;
  }
}
</script>
