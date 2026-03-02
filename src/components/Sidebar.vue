<script setup lang="ts">
import { nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import { useSettingsStore } from "../stores/settings";
import type { ThemeId } from "../stores/settings";
import type { DefaultGroupBy } from "../stores/settings";
import { open } from "@tauri-apps/plugin-dialog";

const store = useCatalogStore();
const settingsStore = useSettingsStore();
const { roots, loading, error } = storeToRefs(store);
const { theme, defaultGroupBy, defaultGroupsExpanded } = storeToRefs(settingsStore);

const themeOptions: { value: ThemeId; label: string }[] = [
  { value: "auto", label: "Auto" },
  { value: "dark", label: "Dark" },
  { value: "light", label: "Light" },
  { value: "doom", label: "DOOM (easter egg)" },
];

const defaultGroupByOptions: { value: DefaultGroupBy; label: string }[] = [
  { value: "none", label: "No grouping" },
  { value: "artist", label: "By artist" },
  { value: "album", label: "By album" },
];

const showSettingsModal = ref(false);
const settingsModalRef = ref<HTMLDivElement | null>(null);
const showKeyMapModal = ref(false);
const keyMapModalRef = ref<HTMLDivElement | null>(null);
const tooltipPopover = ref<{
  text: string;
  x: number;
  y: number;
  isPath?: boolean;
  position: "right" | "below";
} | null>(null);
let tooltipHideTimeout: ReturnType<typeof setTimeout> | null = null;

watch(showSettingsModal, async (open) => {
  if (open) {
    await nextTick();
    settingsModalRef.value?.focus();
  }
});

watch(showKeyMapModal, async (open) => {
  if (open) {
    await nextTick();
    keyMapModalRef.value?.focus();
  }
});

function showTooltip(text: string, e: MouseEvent, isPath = false) {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  if (isPath) {
    tooltipPopover.value = {
      text,
      x: rect.right + 8,
      y: rect.top + rect.height / 2,
      isPath: true,
      position: "right",
    };
  } else {
    tooltipPopover.value = {
      text,
      x: rect.left + rect.width / 2,
      y: rect.bottom + 6,
      position: "below",
    };
  }
}

function scheduleHideTooltip() {
  tooltipHideTimeout = setTimeout(() => {
    tooltipPopover.value = null;
    tooltipHideTimeout = null;
  }, 100);
}

function cancelHideTooltip() {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
}

function hideTooltip() {
  tooltipPopover.value = null;
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
}

onMounted(async () => {
  document.addEventListener("keydown", onDocumentKeydown);
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

function closeSettingsModal() {
  showSettingsModal.value = false;
}

function onSettingsKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") closeSettingsModal();
}

function openKeyMapModal() {
  hideTooltip();
  showKeyMapModal.value = true;
}

function closeKeyMapModal() {
  showKeyMapModal.value = false;
}

function onKeyMapKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") closeKeyMapModal();
}

function onDocumentKeydown(e: KeyboardEvent) {
  if (e.key !== "Escape") return;
  if (showKeyMapModal.value) {
    closeKeyMapModal();
    e.preventDefault();
    e.stopPropagation();
  } else if (showSettingsModal.value) {
    closeSettingsModal();
    e.preventDefault();
    e.stopPropagation();
  }
}

onUnmounted(() => {
  document.removeEventListener("keydown", onDocumentKeydown);
});

const keyMapEntries: { keys: string; description: string }[] = [
  { keys: "Ctrl+F / ⌘F", description: "Focus search bar" },
  { keys: "Escape", description: "Close metadata panel (discard changes)" },
  { keys: "↓ Arrow Down", description: "Move focus down in track list" },
  { keys: "↑ Arrow Up", description: "Move focus up in track list" },
  { keys: "Space", description: "On group row: expand or collapse. On track row: select (add to selection in multi-select)" },
  { keys: "Enter", description: "With one track selected: start playback or pause if already playing" },
];

function setDefaultGroupBy(value: DefaultGroupBy) {
  settingsStore.setDefaultGroupBy(value);
  store.groupBy = value;
}

function setDefaultGroupsExpanded(value: boolean) {
  settingsStore.setDefaultGroupsExpanded(value);
}
</script>

<template>
  <aside class="flex w-56 flex-col border-r border-stone-700 bg-stone-800/80">
    <div class="border-b border-stone-700 p-3">
      <div class="relative z-[210] flex items-center justify-between gap-2">
        <div class="flex min-w-0 items-center gap-2">
          <img src="/favicon.svg" alt="" class="h-6 w-6 shrink-0" />
          <h1 class="text-sm font-semibold text-stone-200">Muorg</h1>
        </div>
        <div class="flex shrink-0 items-center gap-0.5">
          <span
            class="relative z-[220] inline-flex"
            @mouseenter="showTooltip('Key map', $event)"
            @mouseleave="scheduleHideTooltip"
          >
            <button
              type="button"
              class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
              aria-label="Key map"
              @mousedown.stop="openKeyMapModal"
              @click.stop="openKeyMapModal"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16a2 2 0 012 2v8a2 2 0 01-2 2H4a2 2 0 01-2-2V8a2 2 0 012-2z" />
                <path stroke-linecap="round" stroke-linejoin="round" d="M7 10h2M11 10h2M15 10h2M7 14h2M11 14h2M15 14h2" />
              </svg>
            </button>
          </span>
          <span
            class="inline-flex"
            @mouseenter="showTooltip('Settings', $event)"
            @mouseleave="scheduleHideTooltip"
          >
            <button
              type="button"
              class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
              aria-label="Application settings"
              @click="showSettingsModal = true"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
                <path stroke-linecap="round" stroke-linejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
                <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            </button>
          </span>
        </div>
      </div>
      <p class="mt-0.5 text-xs text-stone-500">Library</p>
    </div>
    <div class="flex-1 overflow-y-auto p-2">
      <button
        type="button"
        class="mb-2 flex w-full items-center gap-2 rounded border border-stone-600 bg-stone-700 px-3 py-2 text-left text-sm text-stone-200 hover:bg-stone-600"
        :disabled="loading"
        @click="handleAddFolder"
      >
        <svg class="h-4 w-4 shrink-0 text-stone-400" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
          <path stroke-linecap="round" stroke-linejoin="round" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
        </svg>
        Add folder
      </button>
      <ul v-if="roots.length" class="space-y-1">
        <li
          v-for="root in roots"
          :key="root"
          class="group/parent flex items-center gap-1 rounded border border-stone-700 bg-stone-800/50 px-2 py-1.5"
        >
          <span class="min-w-0 flex-1 truncate text-xs text-stone-300">
            {{ root.split(/[/\\]/).pop() || root }}
          </span>
          <span
            class="flex shrink-0 cursor-help rounded p-0.5 text-stone-500 hover:text-stone-300"
            aria-label="Show full path"
            @mouseenter="showTooltip(root, $event, true)"
            @mouseleave="scheduleHideTooltip"
          >
            <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <circle cx="12" cy="12" r="10" />
              <path stroke-linecap="round" d="M12 16v-4M12 8h.01" />
            </svg>
          </span>
          <span
            class="inline-flex"
            @mouseenter="showTooltip('Rescan folder', $event)"
            @mouseleave="scheduleHideTooltip"
          >
            <button
              type="button"
              class="rounded px-1.5 py-0.5 text-xs text-stone-500 hover:bg-stone-600 hover:text-stone-200"
              aria-label="Rescan folder"
              @click="handleRescan(root)"
            >
              ↻
            </button>
          </span>
          <span
            class="inline-flex"
            @mouseenter="showTooltip('Remove from library (files stay on disk)', $event)"
            @mouseleave="scheduleHideTooltip"
          >
            <button
              type="button"
              class="rounded px-1.5 py-0.5 text-xs text-stone-500 hover:bg-red-600 hover:text-white"
              aria-label="Remove from library (files stay on disk)"
              @click="handleRemoveFolder(root)"
            >
              ✕
            </button>
          </span>
        </li>
      </ul>
    </div>
    <div v-if="error" class="border-t border-stone-700 p-2 text-xs text-red-400">
      {{ error }}
    </div>
    <Teleport to="body">
      <div
        v-if="tooltipPopover"
        class="fixed z-[200] rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 text-xs text-stone-200 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)]"
        :class="{ 'max-w-[320px] break-all font-mono text-stone-300': tooltipPopover.isPath }"
        :style="
          tooltipPopover.position === 'right'
            ? { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translateY(-50%)' }
            : { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translateX(-50%)' }
        "
        @mouseenter="cancelHideTooltip"
        @mouseleave="hideTooltip"
      >
        {{ tooltipPopover.text }}
      </div>
    </Teleport>
    <!-- Settings modal -->
    <Teleport to="body">
      <div
        v-if="showSettingsModal"
        ref="settingsModalRef"
        class="fixed inset-0 z-[300] flex items-center justify-center bg-stone-950/70 p-4 outline-none"
        role="dialog"
        aria-modal="true"
        aria-labelledby="settings-modal-title"
        tabindex="-1"
        @keydown="onSettingsKeydown"
        @click.self="closeSettingsModal"
      >
        <div
          class="w-full max-w-md rounded-lg border border-stone-600 bg-stone-800 shadow-xl"
          @click.stop
        >
          <div class="flex items-center justify-between border-b border-stone-700 px-4 py-3">
            <h2 id="settings-modal-title" class="text-sm font-semibold text-stone-200">Settings</h2>
            <button
              type="button"
              class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
              aria-label="Close"
              @click="closeSettingsModal"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <div class="space-y-4 p-4">
            <div>
              <label class="block text-xs font-medium text-stone-500">Theme</label>
              <select
                :value="theme"
                class="mt-1 w-full rounded border border-stone-600 bg-stone-900 px-3 py-2 text-sm text-stone-200"
                @change="(e) => settingsStore.setTheme((e.target as HTMLSelectElement).value as ThemeId)"
              >
                <option v-for="opt in themeOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </option>
              </select>
            </div>
            <div>
              <label class="block text-xs font-medium text-stone-500">Default grouping</label>
              <select
                :value="defaultGroupBy"
                class="mt-1 w-full rounded border border-stone-600 bg-stone-900 px-3 py-2 text-sm text-stone-200"
                @change="(e) => setDefaultGroupBy((e.target as HTMLSelectElement).value as DefaultGroupBy)"
              >
                <option v-for="opt in defaultGroupByOptions" :key="opt.value" :value="opt.value">
                  {{ opt.label }}
                </option>
              </select>
              <p class="mt-0.5 text-xs text-stone-500">Applied when the app starts.</p>
            </div>
            <div>
              <label class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500">
                <input
                  type="checkbox"
                  :checked="defaultGroupsExpanded"
                  class="rounded border-stone-600"
                  @change="(e) => setDefaultGroupsExpanded((e.target as HTMLInputElement).checked)"
                />
                Groups start expanded
              </label>
              <p class="mt-0.5 text-xs text-stone-500">When grouping is on, expand all groups by default.</p>
            </div>
          </div>
        </div>
      </div>
    </Teleport>
    <!-- Key map modal -->
    <Teleport to="body">
      <div
        v-if="showKeyMapModal"
        ref="keyMapModalRef"
        class="fixed inset-0 z-[300] flex items-center justify-center bg-stone-950/70 p-4 outline-none"
        role="dialog"
        aria-modal="true"
        aria-labelledby="keymap-modal-title"
        tabindex="-1"
        @keydown="onKeyMapKeydown"
        @click.self="closeKeyMapModal"
      >
        <div
          class="w-full max-w-md rounded-lg border border-stone-600 bg-stone-800 shadow-xl"
          @click.stop
        >
          <div class="flex items-center justify-between border-b border-stone-700 px-4 py-3">
            <h2 id="keymap-modal-title" class="text-sm font-semibold text-stone-200">Key map</h2>
            <button
              type="button"
              class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
              aria-label="Close"
              @click="closeKeyMapModal"
            >
              <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <div class="max-h-[70vh] overflow-y-auto p-4">
            <dl class="space-y-3">
              <div v-for="entry in keyMapEntries" :key="entry.keys" class="flex gap-3 text-sm">
                <dt class="w-36 shrink-0 font-mono text-stone-400">{{ entry.keys }}</dt>
                <dd class="min-w-0 text-stone-300">{{ entry.description }}</dd>
              </div>
            </dl>
          </div>
        </div>
      </div>
    </Teleport>
  </aside>
</template>
