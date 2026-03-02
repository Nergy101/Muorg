<script setup lang="ts">
import { onMounted } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import { open } from "@tauri-apps/plugin-dialog";

const store = useCatalogStore();
const { roots, loading, error } = storeToRefs(store);

onMounted(async () => {
  await store.loadRoots();
  await store.loadTracks();
});

async function handleAddFolder() {
  const selected = await open({
    directory: true,
    multiple: false,
  });
  if (selected && typeof selected === "string") {
    try {
      await store.addFolder(selected);
    } catch {
      // error shown in store
    }
  }
}

async function handleRescan(rootPath: string) {
  try {
    await store.rescan(rootPath);
  } catch {
    // error shown in store
  }
}

async function handleRemoveFolder(rootPath: string) {
  if (!confirm(`Remove "${rootPath.split(/[/\\]/).pop() || rootPath}" from library? Files on disk are not deleted.`)) {
    return;
  }
  try {
    await store.removeFolder(rootPath);
  } catch {
    // error shown in store
  }
}
</script>

<template>
  <aside class="flex w-56 flex-col border-r border-stone-700 bg-stone-800/80">
    <div class="border-b border-stone-700 p-3">
      <div class="flex items-center gap-2">
        <img src="/favicon.svg" alt="" class="h-6 w-6 shrink-0" />
        <h1 class="text-sm font-semibold text-stone-200">Muorg</h1>
      </div>
      <p class="mt-0.5 text-xs text-stone-500">Library</p>
    </div>
    <div class="flex-1 overflow-y-auto p-2">
      <button
        type="button"
        class="mb-2 w-full rounded border border-stone-600 bg-stone-700 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-600"
        :disabled="loading"
        @click="handleAddFolder"
      >
        + Add folder
      </button>
      <ul v-if="roots.length" class="space-y-1">
        <li
          v-for="root in roots"
          :key="root"
          class="group flex items-center gap-1 rounded border border-stone-700 bg-stone-800/50 px-2 py-1.5"
        >
          <span class="min-w-0 flex-1 truncate text-xs text-stone-300" :title="root">
            {{ root.split(/[/\\]/).pop() || root }}
          </span>
          <button
            type="button"
            class="rounded px-1.5 py-0.5 text-xs text-stone-500 hover:bg-stone-600 hover:text-stone-200"
            title="Rescan"
            @click="handleRescan(root)"
          >
            ↻
          </button>
          <button
            type="button"
            class="rounded px-1.5 py-0.5 text-xs text-stone-500 hover:bg-red-600 hover:text-white"
            title="Remove from library (files stay on disk)"
            @click="handleRemoveFolder(root)"
          >
            ✕
          </button>
        </li>
      </ul>
    </div>
    <div v-if="error" class="border-t border-stone-700 p-2 text-xs text-red-400">
      {{ error }}
    </div>
  </aside>
</template>
