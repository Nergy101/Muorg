<template>
  <!-- Genre filter select in its own pill, styled like the sort pill. Menus
       teleport to body and open downward from the trigger. -->
  <div :class="PILL">
    <div ref="genreMenuRef" class="relative">
      <button
        ref="genreBtnRef"
        type="button"
        :class="[PILL_BTN, 'max-w-28']"
        @click="toggleGenre"
      >
        <span class="truncate">Genre: {{ genreLabel }}</span>
        <MageIcon :name="genreMenuOpen ? 'chevron-up' : 'chevron-down'" class="h-4 w-4 shrink-0" />
      </button>
      <Teleport to="body">
        <div
          v-if="genreMenuOpen"
          class="glass-strong fixed z-50 max-h-72 w-48 overflow-y-auto rounded-xl py-1"
          :style="genreMenuPos"
        >
          <button
            type="button"
            class="flex w-full items-center justify-between px-3 py-2 text-label-lg text-on-surface"
            @click="selectGenre(null)"
          >
            <span>All genres</span>
            <MageIcon v-if="!lib.genreFilter" name="check" class="h-4 w-4 text-primary" />
          </button>
          <button
            v-for="g in lib.genres"
            :key="g.value"
            type="button"
            class="flex w-full items-center justify-between gap-2 px-3 py-2 text-label-lg text-on-surface"
            @click="selectGenre(g.value)"
          >
            <span class="truncate">{{ g.label }}</span>
            <MageIcon v-if="lib.genreFilter === g.value" name="check" class="h-4 w-4 shrink-0 text-primary" />
          </button>
        </div>
      </Teleport>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import MageIcon from "./MageIcon.vue";
import { useLibraryStore } from "../stores/library";

/** Shared pill styling (same as the sort pill in LibraryViewActions). */
const PILL = "glass-field flex items-center gap-0.5 rounded-full p-0.5";
const PILL_BTN = "flex items-center gap-1 rounded-full px-2.5 py-1 text-label-lg text-on-surface";

const lib = useLibraryStore();

const genreMenuOpen = ref(false);
const genreMenuRef = ref<HTMLElement | null>(null);
const genreBtnRef = ref<HTMLElement | null>(null);

const genreLabel = computed(
  () => lib.genres.find((g) => g.value === lib.genreFilter)?.label ?? "All",
);

const genreMenuPos = computed(() => {
  const b = genreBtnRef.value?.getBoundingClientRect();
  if (!b) return {};
  return { top: `${b.bottom + 4}px`, left: `${b.left}px` };
});

function toggleGenre(): void {
  genreMenuOpen.value = !genreMenuOpen.value;
}

function selectGenre(value: string | null): void {
  lib.genreFilter = value;
  genreMenuOpen.value = false;
}

function onDocumentClick(e: MouseEvent): void {
  if (!genreMenuOpen.value) return;
  const target = e.target as Node;
  if (genreMenuRef.value && !genreMenuRef.value.contains(target)) {
    genreMenuOpen.value = false;
  }
}

onMounted(() => document.addEventListener("click", onDocumentClick));
onUnmounted(() => document.removeEventListener("click", onDocumentClick));
</script>
