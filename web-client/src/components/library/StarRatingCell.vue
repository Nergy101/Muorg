<script setup lang="ts">
import { ref } from "vue";

/**
 * Compact 5-star rating control for table cells and group headers. Clicking
 * the already-set star clears the rating (`null`). `readonly` is used for
 * group-header averages, which are display-only.
 */
const props = withDefaults(
  defineProps<{
    modelValue: number | null;
    readonly?: boolean;
    /** Hollow stars instead of filled — used for a group's average rating. */
    partial?: boolean;
  }>(),
  { readonly: false, partial: false },
);

const emit = defineEmits<{ "update:modelValue": [value: number | null] }>();

const hovered = ref<number | null>(null);

function select(star: number): void {
  if (props.readonly) return;
  emit("update:modelValue", props.modelValue === star ? null : star);
}
</script>

<template>
  <div class="flex items-center gap-0.5" @mouseleave="hovered = null">
    <button
      v-for="star in 5"
      :key="star"
      type="button"
      class="p-0.5 leading-none focus:outline-none"
      :class="readonly ? 'cursor-default' : 'cursor-pointer transition-transform hover:scale-110'"
      :tabindex="readonly ? -1 : undefined"
      :aria-label="`${star} star${star > 1 ? 's' : ''}`"
      @mouseenter="!readonly && (hovered = star)"
      @click.stop="select(star)"
    >
      <svg
        viewBox="0 0 24 24"
        class="h-3.5 w-3.5 transition-colors"
        :class="
          (hovered !== null ? star <= hovered : star <= (modelValue ?? 0))
            ? partial
              ? 'fill-transparent text-amber-400'
              : 'fill-amber-400 text-amber-400'
            : 'fill-transparent text-on-surface-variant/40'
        "
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
