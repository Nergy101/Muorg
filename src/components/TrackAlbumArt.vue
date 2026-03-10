<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useCatalogStore } from "../stores/catalog";

const props = defineProps<{ path: string; size?: "small" | "large" }>();
const store = useCatalogStore();

const cover = computed(() => store.getCover(props.path));

const sizeClass = computed(() =>
  props.size === "large" ? "h-40 w-40" : "h-8 w-8",
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
  </div>
</template>
