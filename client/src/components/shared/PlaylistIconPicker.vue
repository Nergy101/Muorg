<script setup lang="ts">
import { computed, ref } from "vue";
import { loadLucideIcons, type LucideIcons } from "@shared/lucideIcons";
import {
  PLAYLIST_ICON_COLORS,
  formatPlaylistIcon,
  lucideComponentName,
  resolvePlaylistIcon,
} from "@shared/playlistIcons";
import { LUCIDE_ICON_NAMES, LUCIDE_ICON_TAGS } from "@shared/lucideIconNames";

const props = defineProps<{ open: boolean; value?: string }>();
const emit = defineEmits<{ pick: [value: string] }>();

const query = ref("");
const iconsMap = ref<LucideIcons | null>(null);
void loadLucideIcons().then((m) => (iconsMap.value = m));

const current = computed(() => resolvePlaylistIcon(props.value));

// Cap the unfiltered grid: rendering all ~2.4k icons at once is janky, and a
// search narrows the list anyway.
const MAX_VISIBLE = 400;

const filtered = computed(() => {
  const q = query.value.trim().toLowerCase();
  const matches = !q
    ? LUCIDE_ICON_NAMES
    : LUCIDE_ICON_NAMES.filter(
        (n) => n.includes(q) || (LUCIDE_ICON_TAGS[n] ?? []).some((t) => t.includes(q)),
      );
  return matches.length > MAX_VISIBLE ? matches.slice(0, MAX_VISIBLE) : matches;
});

const iconComponent = (name: string) => {
  const map = iconsMap.value;
  if (!map) return null;
  return map[lucideComponentName(name) as keyof LucideIcons] ?? map.EighthNotes;
};

function selectIcon(name: string): void {
  emit("pick", formatPlaylistIcon(name, current.value.color));
}

function selectColor(color: string): void {
  emit("pick", formatPlaylistIcon(current.value.name, color));
}
</script>

<template>
  <div
    v-show="open"
    class="w-72 rounded-lg border border-stone-600 bg-stone-800 p-2 shadow-xl shadow-black/50"
  >
    <input
      v-model="query"
      type="text"
      placeholder="Search icons…"
      class="w-full rounded border border-stone-600 bg-stone-700 px-2 py-1 text-xs text-stone-200 outline-none placeholder:text-stone-500 focus:border-stone-400"
    />

    <div class="mt-2 grid max-h-48 grid-cols-8 gap-0.5 overflow-y-auto pr-1">
      <button
        v-for="name in filtered"
        :key="name"
        type="button"
        class="flex h-7 w-7 items-center justify-center rounded"
        :class="name === current.name ? 'bg-stone-600 ring-1 ring-stone-400' : 'hover:bg-stone-700'"
        :title="name"
        @click="selectIcon(name)"
      >
        <component
          :is="iconComponent(name)"
          v-if="iconComponent(name)"
          :color="current.color"
          class="h-4 w-4"
          :stroke-width="2"
        />
      </button>
      <div
        v-if="!filtered.length"
        class="col-span-8 py-3 text-center text-xs text-stone-500"
      >
        No icons match “{{ query }}”.
      </div>
    </div>

    <div class="mt-2 flex flex-wrap items-center gap-1.5 border-t border-stone-700 pt-2">
      <button
        v-for="color in PLAYLIST_ICON_COLORS"
        :key="color"
        type="button"
        class="h-5 w-5 rounded-full border border-black/40"
        :class="color.toLowerCase() === current.color.toLowerCase() ? 'ring-2 ring-stone-300' : ''"
        :style="{ backgroundColor: color }"
        :title="color"
        @click="selectColor(color)"
      />
      <label
        class="relative h-5 w-5 cursor-pointer overflow-hidden rounded-full border border-black/40"
        :style="{ backgroundColor: current.color }"
        title="Custom color"
      >
        <input
          type="color"
          class="absolute inset-0 h-full w-full cursor-pointer opacity-0"
          :value="current.color"
          @input="selectColor(($event.target as HTMLInputElement).value)"
        />
      </label>
    </div>
  </div>
</template>
