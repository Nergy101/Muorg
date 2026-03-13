<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from "vue";
import { storeToRefs } from "pinia";
import { useCatalogStore } from "../stores/catalog";
import packageJson from "../../package.json";

const props = defineProps<{
  activeTab: "library" | "metadata" | "play";
}>();

const emit = defineEmits<{
  (e: "update:activeTab", value: "library" | "metadata" | "play"): void;
  (e: "openSettings"): void;
  (e: "openKeyMap"): void;
}>();

const store = useCatalogStore();
const { searchQuery, groupBy } = storeToRefs(store);

const appVersion = packageJson.version;

type TooltipPosition = "left" | "below" | "below-left";
const tooltipPopover = ref<{ text: string; x: number; y: number; position?: TooltipPosition } | null>(null);
let tooltipHideTimeout: ReturnType<typeof setTimeout> | null = null;

function showTooltip(text: string, e: MouseEvent, position: TooltipPosition = "below") {
  if (tooltipHideTimeout) clearTimeout(tooltipHideTimeout);
  tooltipHideTimeout = null;
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  if (position === "left") {
    tooltipPopover.value = { text, x: rect.left - 8, y: rect.top + rect.height / 2, position: "left" };
  } else if (position === "below-left") {
    tooltipPopover.value = { text, x: rect.left, y: rect.bottom + 6, position: "below-left" };
  } else {
    tooltipPopover.value = { text, x: rect.left + rect.width / 2, y: rect.bottom + 6, position: "below" };
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

function onGlobalKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === "f") {
    e.preventDefault();
    nextTick(() => searchInputRef.value?.focus());
  }
}

const searchInputRef = ref<HTMLInputElement | null>(null);

onMounted(() => {
  document.addEventListener("keydown", onGlobalKeydown);
});

onUnmounted(() => {
  document.removeEventListener("keydown", onGlobalKeydown);
});

watch(
  () => props.activeTab,
  () => {
    hideTooltip();
  },
);

const groupByValue = computed(() => groupBy.value);
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-3 border-b border-stone-700 px-4 py-2">
    <div class="flex flex-1 flex-wrap items-center gap-3">
      <input
        ref="searchInputRef"
        :value="searchQuery"
        type="search"
        placeholder="Search title, artist, album…"
        class="min-w-[200px] rounded border border-stone-600 bg-stone-800 px-2 py-1 text-sm text-stone-200 placeholder-stone-500"
        @input="store.setSearchQuery(($event.target as HTMLInputElement).value)"
      />
      <select
        :value="groupByValue"
        class="rounded border border-stone-600 bg-stone-800 px-2.5 py-1.5 text-sm text-stone-200"
        @change="store.setGroupBy(($event.target as HTMLSelectElement).value as 'none' | 'artist' | 'album')"
      >
        <option value="album">Group by album</option>
        <option value="artist">Group by artist</option>
        <option value="none">No grouping</option>
      </select>
    </div>

    <div class="flex items-center justify-start gap-2">
      <button
        type="button"
        class="primary-tab rounded-full px-3 py-1 text-xs font-medium transition-colors"
        :class="props.activeTab === 'library' ? 'primary-tab--active' : undefined"
        @click="emit('update:activeTab', 'library')"
      >
        Library
      </button>
      <button
        type="button"
        class="primary-tab rounded-full px-3 py-1 text-xs font-medium transition-colors"
        :class="props.activeTab === 'metadata' ? 'primary-tab--active' : undefined"
        @click="emit('update:activeTab', 'metadata')"
      >
        Metadata
      </button>
      <button
        type="button"
        class="primary-tab rounded-full px-3 py-1 text-xs font-medium transition-colors"
        :class="props.activeTab === 'play' ? 'primary-tab--active' : undefined"
        @click="emit('update:activeTab', 'play')"
      >
        Player
      </button>
    </div>

    <div
      class="relative z-[210] flex shrink-0 items-center gap-2"
      @mouseenter="showTooltip('Version ' + appVersion, $event)"
      @mouseleave="scheduleHideTooltip"
    >
      <img src="/favicon.svg" alt="" class="h-6 w-6 shrink-0" />
      <span class="text-sm font-semibold text-stone-200">Muorg</span>
      <span
        class="relative z-[220] inline-flex"
        @mouseenter="showTooltip('Key map', $event, 'left')"
        @mouseleave="scheduleHideTooltip"
      >
        <button
          type="button"
          class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
          aria-label="Key map"
          @mousedown.stop="emit('openKeyMap')"
          @click.stop="emit('openKeyMap')"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" d="M4 6h16a2 2 0 012 2v8a2 2 0 01-2 2H4a2 2 0 01-2-2V8a2 2 0 012-2z" />
            <path stroke-linecap="round" stroke-linejoin="round" d="M7 10h2M11 10h2M15 10h2M7 14h2M11 14h2M15 14h2" />
          </svg>
        </button>
      </span>
      <span
        class="inline-flex"
        @mouseenter="showTooltip('Settings', $event, 'left')"
        @mouseleave="scheduleHideTooltip"
      >
        <button
          type="button"
          class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
          aria-label="Application settings"
          @click="emit('openSettings')"
        >
          <svg class="h-4 w-4" fill="none" stroke="currentColor" stroke-width="2" viewBox="0 0 24 24" aria-hidden="true">
            <path stroke-linecap="round" stroke-linejoin="round" d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
            <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
          </svg>
        </button>
      </span>
    </div>
  </div>

  <Teleport to="body">
    <div
      v-if="tooltipPopover"
      class="fixed z-[500] rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 text-xs text-stone-200 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)] whitespace-nowrap"
      :style="tooltipPopover.position === 'left'
        ? { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translate(-100%, -50%)' }
        : tooltipPopover.position === 'below-left'
          ? { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px' }
          : { left: tooltipPopover.x + 'px', top: tooltipPopover.y + 'px', transform: 'translateX(-50%)' }"
      @mouseenter="cancelHideTooltip"
      @mouseleave="hideTooltip"
    >
      {{ tooltipPopover.text }}
    </div>
  </Teleport>
</template>
