<template>
  <div class="flex min-h-screen items-center justify-center bg-stone-900 p-4">
    <div class="w-full max-w-sm">
      <div class="mb-8 text-center">
        <div class="mb-2 text-4xl">🎵</div>
        <h1 class="text-2xl font-bold text-stone-100">Muorg Web</h1>
        <p class="mt-1 text-sm text-stone-400">Connect to your Muorg server</p>
      </div>

      <form class="space-y-4" @submit.prevent="connect">
        <div>
          <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-stone-400">
            Server URL
          </label>
          <input
            v-model="url"
            type="url"
            placeholder="http://192.168.1.10:7700"
            class="w-full rounded-lg border border-stone-600 bg-stone-800 px-3 py-2.5 text-sm text-stone-100 placeholder-stone-500 focus:border-transparent focus:outline-none focus:ring-2 focus:ring-accent"
            required
            autocomplete="off"
            spellcheck="false"
          />
        </div>

        <div>
          <label class="mb-1 block text-xs font-medium uppercase tracking-wide text-stone-400">
            API Key
          </label>
          <input
            v-model="apiKey"
            type="password"
            placeholder="your-api-key"
            class="w-full rounded-lg border border-stone-600 bg-stone-800 px-3 py-2.5 text-sm text-stone-100 placeholder-stone-500 focus:border-transparent focus:outline-none focus:ring-2 focus:ring-accent"
            autocomplete="current-password"
          />
        </div>

        <div
          v-if="errorMsg"
          class="rounded-lg border border-red-800/50 bg-red-950/30 px-3 py-2 text-sm text-red-400"
        >
          {{ errorMsg }}
        </div>

        <button
          type="submit"
          :disabled="busy"
          class="flex w-full items-center justify-center gap-2 rounded-lg bg-accent px-4 py-2.5 text-sm font-medium text-white transition-colors hover:bg-[var(--accent-hover)] disabled:opacity-60"
        >
          <span v-if="busy">
            <svg class="h-4 w-4 animate-spin" viewBox="0 0 24 24" fill="none">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" />
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" />
            </svg>
          </span>
          <span>{{ busy ? "Connecting…" : "Connect" }}</span>
        </button>
      </form>

      <p class="mt-6 text-center text-xs text-stone-600">
        The server URL and API key are saved locally in your browser.
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import { testConnection, setServerUrl, setApiKey } from "../api/client";

const emit = defineEmits<{ connected: [] }>();

const url = ref(localStorage.getItem("muorg-web-url") ?? "");
const apiKey = ref(localStorage.getItem("muorg-web-key") ?? "");
const busy = ref(false);
const errorMsg = ref("");

async function connect(): Promise<void> {
  errorMsg.value = "";
  busy.value = true;
  try {
    await testConnection(url.value.trim(), apiKey.value.trim());
    setServerUrl(url.value.trim());
    setApiKey(apiKey.value.trim());
    emit("connected");
  } catch (e) {
    errorMsg.value = (e as Error).message || "Could not reach server.";
  } finally {
    busy.value = false;
  }
}
</script>
