<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div class="flex min-h-0 flex-1 flex-col items-center justify-center gap-6 overflow-y-auto px-6">
      <div class="flex w-full max-w-sm flex-col items-center gap-6">
        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" class="h-24 w-24">
          <defs>
            <radialGradient id="metalCenter" cx="50%" cy="50%" r="50%">
              <stop offset="0%" stop-color="#ffffff"/>
              <stop offset="100%" stop-color="#999999"/>
            </radialGradient>
          </defs>
          <path d="M150,200 L450,450 L512,512 L574,450 L874,200 L750,700 L512,880 L274,700 Z" fill="#5b7c32" stroke="#ffffff" stroke-width="5" stroke-linejoin="round"/>
          <circle cx="512" cy="512" r="300" fill="#759346" stroke="#ffffff" stroke-width="15"/>
          <circle cx="512" cy="512" r="220" fill="none" stroke="#5b7c32" stroke-width="8" opacity="0.4"/>
          <circle cx="512" cy="512" r="150" fill="none" stroke="#5b7c32" stroke-width="8" opacity="0.4"/>
          <circle cx="512" cy="512" r="75" fill="#5b7c32"/>
          <circle cx="512" cy="512" r="18" fill="url(#metalCenter)"/>
        </svg>

        <div class="flex flex-col items-center gap-1 text-center">
          <h1 class="text-headline-lg text-primary">Muorg</h1>
          <p class="text-body-md text-on-surface-variant">Connect to your Muorg server</p>
        </div>

        <form class="w-full space-y-4" @submit.prevent="connect">
          <div>
            <label class="mb-1 block text-body-sm text-on-surface-variant">Server URL</label>
            <input
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
            <label class="mb-1 block text-body-sm text-on-surface-variant">API Key</label>
            <div class="relative">
              <input
                v-model="apiKey"
                :type="showKey ? 'text' : 'password'"
                autocomplete="current-password"
                class="w-full rounded-xl bg-surface px-4 py-3 text-body-lg text-on-surface placeholder:text-on-surface-variant outline-none focus:ring-2 focus:ring-primary"
              />
              <button
                type="button"
                class="absolute inset-y-0 right-3 flex items-center text-on-surface-variant"
                @click="showKey = !showKey"
              >
                <MageIcon :name="showKey ? 'eye-off' : 'eye'" class="h-5 w-5" />
              </button>
            </div>
          </div>

          <p v-if="errorMsg" class="text-body-sm text-error">{{ errorMsg }}</p>

          <button
            type="submit"
            :disabled="busy"
            class="flex w-full items-center justify-center rounded-full bg-primary px-4 py-3 text-label-lg text-on-primary disabled:opacity-60"
          >
            <MageIcon v-if="busy" name="refresh" class="h-5 w-5 animate-spin" />
            <span v-else>Connect</span>
          </button>
        </form>

        <p class="text-center text-body-sm text-on-surface-variant/70">
          The server URL and API key are saved locally in your browser.
        </p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import MageIcon from "../components/MageIcon.vue";
import { getServerUrl, getApiKey, setServerUrl, setApiKey, testConnection } from "../api/client";
import { router, loadData } from "../router";

const url = ref(getServerUrl());
const apiKey = ref(getApiKey());
const showKey = ref(false);
const busy = ref(false);
const errorMsg = ref("");

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
    await loadData();
    router.replace({ name: "library" });
  } catch (e) {
    const message = e instanceof TypeError ? "" : (e as Error).message;
    errorMsg.value = message || "Could not reach server. Check the URL and try again.";
  } finally {
    busy.value = false;
  }
}
</script>
