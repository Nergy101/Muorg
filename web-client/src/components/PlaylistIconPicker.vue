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

const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{ "update:modelValue": [value: string] }>();

const query = ref("");
const iconsMap = ref<LucideIcons | null>(null);
void loadLucideIcons().then((m) => (iconsMap.value = m));

const current = computed(() => resolvePlaylistIcon(props.modelValue));

// Cap the unfiltered grid: rendering all ~2.4k icons at once is janky on
// mobile, and a search narrows the list anyway.
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
  emit("update:modelValue", formatPlaylistIcon(name, current.value.color));
}

function selectColor(color: string): void {
  emit("update:modelValue", formatPlaylistIcon(current.value.name, color));
}
</script>

<template>
  <div>
    <input
      v-model="query"
      type="text"
      placeholder="Search icons…"
      class="w-full rounded-xl bg-surface-variant px-3 py-2 text-body-md text-on-surface outline-none placeholder:text-on-surface-variant"
    />

    <div class="mt-2 grid max-h-48 grid-cols-8 gap-1 overflow-y-auto pr-1">
      <button
        v-for="name in filtered"
        :key="name"
        type="button"
        class="flex h-9 w-9 items-center justify-center rounded-lg"
        :class="name === current.name ? 'bg-primary-container' : ''"
        :title="name"
        @click="selectIcon(name)"
      >
        <component
          :is="iconComponent(name)"
          v-if="iconComponent(name)"
          :color="current.color"
          class="h-5 w-5"
          :stroke-width="2"
        />
      </button>
      <div
        v-if="!filtered.length"
        class="col-span-8 py-4 text-center text-body-sm text-on-surface-variant"
      >
        No icons match “{{ query }}”.
      </div>
    </div>

    <div class="mt-3 flex flex-wrap items-center gap-2">
      <button
        v-for="color in PLAYLIST_ICON_COLORS"
        :key="color"
        type="button"
        class="h-6 w-6 rounded-full border border-black/20"
        :class="color.toLowerCase() === current.color.toLowerCase() ? 'ring-2 ring-primary ring-offset-2 ring-offset-surface' : ''"
        :style="{ backgroundColor: color }"
        :title="color"
        @click="selectColor(color)"
      />
      <label
        class="relative h-6 w-6 cursor-pointer overflow-hidden rounded-full border border-black/20"
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
