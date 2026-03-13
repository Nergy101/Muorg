<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useCatalogStore } from "../../stores/catalog";

const props = defineProps<{
  path: string;
  size?: "small" | "medium" | "large" | "xlarge";
}>();

const store = useCatalogStore();

onMounted(() => {
  if (props.path) {
    store.fetchCover(props.path);
  }
});

const cover = computed(() => store.getCover(props.path));

const sizeClass = computed(() => {
  const size = props.size ?? "small";
  if (size === "xlarge") return "h-[min(72vmin,420px)] w-[min(72vmin,420px)]";
  if (size === "large") return "h-32 w-32";
  if (size === "medium") return "h-12 w-12";
  return "h-8 w-8";
});

const iconSizeClass = computed(() => {
  const size = props.size ?? "small";
  if (size === "xlarge") return "h-16 w-16 text-2xl";
  if (size === "large") return "h-6 w-6 text-sm";
  if (size === "medium") return "h-5 w-5 text-xs";
  return "h-4 w-4 text-[0.6rem]";
});
</script>

<template>
  <div class="flex items-center justify-center overflow-hidden rounded bg-stone-900" :class="sizeClass">
    <img
      v-if="cover"
      :src="store.getCoverDataUrl(props.path) || undefined"
      alt=""
      class="h-full w-full object-cover"
    />
    <span
      v-else
      class="inline-flex items-center justify-center rounded-full border border-stone-500 text-stone-400"
      :class="iconSizeClass"
      aria-hidden="true"
    >
      ♪
    </span>
  </div>
</template>

