<script setup lang="ts">
import { computed, watchEffect } from "vue";
import { useCatalogStore } from "../../stores/catalog";

const props = defineProps<{
  path: string;
  size?: "small" | "medium" | "large" | "xlarge";
  /** When set, overrides size with explicit pixel dimensions (e.g. for responsive panel layout). */
  sizePx?: number;
}>();

const store = useCatalogStore();

// watchEffect tracks props.path reactively so fetchCover is called on mount
// AND whenever the path changes (onMounted only fires once on first mount).
watchEffect(() => {
  if (props.path) {
    store.fetchCover(props.path);
  }
});

const cover = computed(() => store.getCover(props.path));

const sizeClass = computed(() => {
  if (props.sizePx != null && props.sizePx > 0) {
    return "";
  }
  const size = props.size ?? "small";
  if (size === "xlarge") return "h-[min(72vmin,420px)] w-[min(72vmin,420px)]";
  if (size === "large") return "h-32 w-32";
  if (size === "medium") return "h-12 w-12";
  return "h-8 w-8";
});

const sizeStyle = computed(() => {
  if (props.sizePx == null || props.sizePx <= 0) return undefined;
  const px = Math.round(props.sizePx);
  return { width: `${px}px`, height: `${px}px` };
});

const iconSizeClass = computed(() => {
  if (props.sizePx != null && props.sizePx > 0) {
    const px = props.sizePx;
    if (px >= 192) return "h-16 w-16 text-2xl";
    if (px >= 96) return "h-10 w-10 text-lg";
    if (px >= 48) return "h-6 w-6 text-sm";
    return "h-4 w-4 text-xs";
  }
  const size = props.size ?? "small";
  if (size === "xlarge") return "h-16 w-16 text-2xl";
  if (size === "large") return "h-6 w-6 text-sm";
  if (size === "medium") return "h-5 w-5 text-xs";
  return "h-4 w-4 text-[0.6rem]";
});
</script>

<template>
  <div
    class="flex items-center justify-center overflow-hidden rounded bg-stone-900 shrink-0"
    :class="sizeClass"
    :style="sizeStyle"
  >
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

