<script setup lang="ts">
/**
 * The genre field: a free-text input with a filtered list of the ID3v1 genres
 * under it. Free text wins — the list is a shortcut, not a constraint, because
 * people do tag things "Shoegaze".
 */
import { computed, ref } from "vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { useOverlayScrollbars } from "../../composables/useOverlayScrollbars";

const props = defineProps<{ modelValue: string }>();
const emit = defineEmits<{
  (e: "update:modelValue", value: string): void;
  (e: "edited"): void;
  (e: "clear"): void;
}>();

const open = ref(false);
const activeIndex = ref(-1);
const scrollRef = ref<HTMLElement | null>(null);
useOverlayScrollbars(scrollRef);

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

const filtered = computed(() => {
  const q = props.modelValue.toLowerCase().trim();
  if (!q) return COMMON_GENRES;
  return COMMON_GENRES.filter((g) => g.toLowerCase().includes(q));
});

function onInput(e: Event) {
  emit("update:modelValue", (e.target as HTMLInputElement).value);
  emit("edited");
  open.value = true;
  activeIndex.value = -1;
}

function select(genre: string) {
  emit("update:modelValue", genre);
  emit("edited");
  open.value = false;
  activeIndex.value = -1;
}

function onKeydown(e: KeyboardEvent) {
  if (!open.value || !filtered.value.length) return;
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
    open.value = false;
    activeIndex.value = -1;
  }
}
</script>

<template>
  <div class="relative">
    <label class="block text-stone-500">Genre</label>
    <div class="relative mt-0.5">
      <input
        :value="modelValue"
        type="text"
        class="w-full rounded border border-stone-600 bg-stone-900 px-2 py-0.5 text-stone-200 text-sm"
        :class="modelValue ? 'pr-6' : ''"
        @input="onInput"
        @focus="open = true"
        @blur="open = false"
        @keydown="onKeydown"
      />
      <button
        v-if="modelValue"
        type="button"
        tabindex="-1"
        class="absolute right-1 inset-y-0 my-auto h-fit rounded p-0.5 text-stone-500 hover:text-stone-300"
        title="Clear genre"
        @click="emit('clear')"
      >
        <FeatherIcon name="x" class="h-3 w-3" />
      </button>
    </div>
    <div
      v-if="open && filtered.length"
      class="absolute left-0 top-full z-50 mt-0.5 min-w-[240px] rounded border border-stone-600 bg-stone-900 shadow-lg"
    >
      <div ref="scrollRef" class="max-h-48">
        <button
          v-for="(g, i) in filtered"
          :key="g"
          type="button"
          class="flex w-full items-center pl-3 pr-8 py-1 text-left text-sm text-stone-200 hover:bg-stone-700"
          :class="{ 'bg-stone-700': i === activeIndex }"
          @mousedown.prevent="select(g)"
        >{{ g }}</button>
      </div>
    </div>
  </div>
</template>
