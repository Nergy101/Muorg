<template>
  <div class="relative py-1.5">
    <label class="mb-1 block text-body-sm text-on-surface-variant">Genre</label>
    <div class="relative">
      <input
        :value="modelValue"
        type="text"
        :placeholder="placeholder"
        class="w-full rounded-xl bg-surface-variant px-3 py-2.5 text-body-lg text-on-surface outline-none"
        :class="modelValue ? 'pr-9' : ''"
        @input="onInput"
        @focus="open = true"
        @blur="onBlur"
        @keydown="onKeydown"
      />
      <button
        v-if="modelValue"
        type="button"
        tabindex="-1"
        class="absolute inset-y-0 right-2 my-auto h-fit p-1 text-on-surface-variant"
        aria-label="Clear genre"
        @mousedown.prevent="emit('clear')"
      >
        <MageIcon name="multiply" class="h-4 w-4" />
      </button>
    </div>
    <ul
      v-if="open && filtered.length"
      class="glass-strong absolute left-0 right-0 top-full z-10 mt-1 max-h-56 overflow-y-auto rounded-xl py-1 shadow-xl"
    >
      <li v-for="(g, i) in filtered" :key="g">
        <button
          type="button"
          class="flex w-full items-center px-3 py-2 text-left text-body-md text-on-surface"
          :class="i === activeIndex ? 'bg-on-surface/10' : ''"
          @mousedown.prevent="select(g)"
        >{{ g }}</button>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
/**
 * The genre field: a free-text input with a filtered list under it — the ID3v1
 * genres (same list as the desktop app's combobox) plus whatever genres are
 * already in this library, deduplicated case-insensitively. Free text wins:
 * the list is a shortcut, not a constraint.
 */
import { computed, ref } from "vue";
import MageIcon from "../MageIcon.vue";
import { useLibraryStore } from "../../stores/library";

const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{
  "update:modelValue": [value: string];
  edited: [];
  clear: [];
}>();

const placeholder = "e.g. Rock";

/** The ID3v1 genre list, which is what most taggers and players still show. */
const COMMON_GENRES = [
  "Blues", "Classic Rock", "Country", "Dance", "Disco", "Funk", "Grunge",
  "Hip-Hop", "Jazz", "Metal", "New Age", "Oldies", "Other", "Pop", "R&B",
  "Rap", "Reggae", "Rock", "Techno", "Industrial", "Alternative", "Ska",
  "Death Metal", "Soundtrack", "Euro-Techno", "Ambient", "Trip-Hop", "Vocal",
  "Trance", "Classical", "Instrumental", "House", "Gospel", "Soul", "Punk",
  "Electronic", "New Wave", "Psychedelic", "Folk", "Folk-Rock", "Swing",
  "Latin", "Celtic", "Bluegrass", "Progressive Rock", "Gothic Rock",
  "Symphonic Rock", "Big Band", "Easy Listening", "Acoustic", "Opera",
  "Chanson", "Ballad", "Samba", "Tango", "Drum & Bass", "Jungle",
  "Garage", "Hardstep", "Hardcore", "Drum Solo", "A cappella",
  "Euro-House", "Dance Hall",
];

const library = useLibraryStore();

/** The ID3v1 list plus this library's own genres, deduped case-insensitively
 *  (a library genre that only differs in case from a common one is dropped —
 *  the common spelling wins so the list stays recognizable). */
const ALL_GENRES = computed(() => {
  const seen = new Map<string, string>();
  for (const g of COMMON_GENRES) seen.set(g.toLowerCase(), g);
  for (const g of library.genres) {
    const key = g.label.toLowerCase();
    if (!seen.has(key)) seen.set(key, g.label);
  }
  return [...seen.values()].sort((a, b) => a.localeCompare(b));
});

const open = ref(false);
const activeIndex = ref(-1);

const filtered = computed(() => {
  const q = props.modelValue.toLowerCase().trim();
  if (!q) return ALL_GENRES.value;
  return ALL_GENRES.value.filter((g) => g.toLowerCase().includes(q));
});

function onInput(e: Event): void {
  emit("update:modelValue", (e.target as HTMLInputElement).value);
  emit("edited");
  open.value = true;
  activeIndex.value = -1;
}

function onBlur(): void {
  // Deferred so a mousedown on an option (which preventDefault()s the blur's
  // focus loss) still fires before the list disappears.
  setTimeout(() => {
    open.value = false;
  }, 100);
}

function select(genre: string): void {
  emit("update:modelValue", genre);
  emit("edited");
  open.value = false;
  activeIndex.value = -1;
}

function onKeydown(e: KeyboardEvent): void {
  if (!open.value || filtered.value.length === 0) return;
  if (e.key === "ArrowDown") {
    e.preventDefault();
    activeIndex.value = Math.min(activeIndex.value + 1, filtered.value.length - 1);
  } else if (e.key === "ArrowUp") {
    e.preventDefault();
    activeIndex.value = Math.max(activeIndex.value - 1, 0);
  } else if (e.key === "Enter" && activeIndex.value >= 0) {
    e.preventDefault();
    select(filtered.value[activeIndex.value]);
  } else if (e.key === "Escape") {
    e.stopPropagation();
    open.value = false;
    activeIndex.value = -1;
  }
}
</script>
