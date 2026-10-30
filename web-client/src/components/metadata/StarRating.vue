<script setup lang="ts">
import { ref } from "vue";

/**
 * A 1–5 star rating control. `null` means unrated; clicking the currently set
 * star clears it. Shared between the track actions sheet (saves immediately)
 * and the metadata editor (single-track rating, also immediate — Q4 only
 * requires a confirm step for multi-track changes, which callers handle
 * before they ever reach this component).
 */
const props = defineProps<{
  modelValue: number | null;
  readonly?: boolean;
  /** Smaller stars for inline use in rows/sheets. */
  size?: "sm" | "md";
}>();

const emit = defineEmits<{ "update:modelValue": [value: number | null] }>();

const hovered = ref<number | null>(null);

function select(star: number): void {
  if (props.readonly) return;
  emit("update:modelValue", props.modelValue === star ? null : star);
}
</script>

<template>
  <div class="flex items-center gap-0.5" :class="{ 'cursor-pointer': !readonly }" @mouseleave="hovered = null">
    <button
      v-for="star in 5"
      :key="star"
      type="button"
      class="p-0.5 leading-none focus:outline-none"
      :class="{ 'cursor-default': readonly, 'transition-transform hover:scale-110': !readonly }"
      :aria-label="`${star} star${star > 1 ? 's' : ''}`"
      @mouseenter="!readonly && (hovered = star)"
      @click="select(star)"
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        :class="size === 'sm' ? 'h-4 w-4' : 'h-5 w-5'"
        class="transition-colors"
        :style="{
          fill: (hovered ?? modelValue ?? 0) >= star ? 'var(--primary)' : 'transparent',
          color: (hovered ?? modelValue ?? 0) >= star ? 'var(--primary)' : 'var(--on-surface-variant)',
        }"
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
      </svg>
    </button>
  </div>
</template>
