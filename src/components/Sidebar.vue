<script setup lang="ts">
import { onMounted, ref } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import { open } from "@tauri-apps/plugin-dialog";

defineProps<{ collapsed: boolean }>();
const emit = defineEmits<{ toggle: [] }>();

const store = useCatalogStore();
const { roots, loading, error } = storeToRefs(store);

const tooltipPopover = ref<{
  text: string;
  x: number;
  y: number;
  isPath?: boolean;
  position: "right" | "below";
} | null>(null);
let tooltipHideTimeout: ReturnType<typeof setTimeout> | null = null;

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
  <aside
    :class="['flex flex-col border-r border-stone-700 bg-stone-800/80 transition-[width] duration-200', collapsed ? 'w-12' : 'w-56']"
  >
    <!-- Collapsed: only expand button -->
    <template v-if="collapsed">
      <div class="flex flex-1 flex-col items-center justify-start pt-3">
        <span
          class="inline-flex"
          @mouseenter="showTooltip('Expand library', $event, true)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="rounded p-2 text-stone-500 hover:bg-stone-700 hover:text-stone-200"
            aria-label="Expand library"
            @click="emit('toggle')"
          >
            <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M9 5l7 7-7 7" />
            </svg>
          </button>
        </span>
      </div>
    </template>
    <!-- Expanded: Library header + list -->
    <template v-else>
      <div class="flex items-center justify-between border-b border-stone-700 px-3 py-2">
        <span class="text-xs text-stone-500">Library</span>
        <span
          class="inline-flex"
          @mouseenter="showTooltip('Collapse library', $event)"
          @mouseleave="scheduleHideTooltip"
        >
          <button
            type="button"
            class="rounded p-1 text-stone-500 hover:bg-stone-700 hover:text-stone-200"
            aria-label="Collapse library"
            @click="emit('toggle')"
          >
            <svg class="h-3.5 w-3.5" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
              <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
            </svg>
          </button>
        </span>
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
      <div v-if="error" class="mt-2 border-t border-stone-700 pt-2 text-xs text-red-400">
        {{ error }}
      </div>
    </template>
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
  </aside>
</template>
