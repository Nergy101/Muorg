<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useCatalogStore } from "../stores/catalog";

const props = defineProps<{ path: string; size?: "small" | "large" }>();
const store = useCatalogStore();

const cover = computed(() => store.getCover(props.path));

const sizeClass = computed(() =>
  props.size === "large" ? "h-40 w-40" : "h-8 w-8",
);

const placeholderIconClass = computed(() =>
  props.size === "large"
    ? "h-10 w-10 text-base"
    : "h-4 w-4 text-[0.6rem]",
);

onMounted(() => {
  store.fetchCover(props.path);
});
</script>

<template>
  <div
    :class="[
      'flex shrink-0 items-center justify-center overflow-hidden rounded bg-stone-800',
      sizeClass,
    ]"
  >
    <img
      v-if="cover"
      :src="`data:${cover.mime};base64,${cover.base64}`"
      alt=""
      class="h-full w-full object-cover"
    />
    <span
      v-else-if="cover === null"
      :class="['inline-flex items-center justify-center rounded-full border border-stone-500 text-stone-400', placeholderIconClass]"
      aria-hidden="true"
    >
      ♪
    </span>
  </div>
</template>
