<script setup lang="ts">
import { ref } from "vue";

const props = defineProps<{
  modelValue: number | null;
  /** If true, the component is display-only (no hover/click). */
  readonly?: boolean;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: number | null];
}>();

const hovered = ref<number | null>(null);

function select(star: number) {
  if (props.readonly) return;
  // Clicking the same star clears the rating.
  emit("update:modelValue", props.modelValue === star ? null : star);
}
</script>

<template>
  <div
    class="flex items-center gap-0.5"
    :class="{ 'cursor-pointer': !readonly }"
    @mouseleave="hovered = null"
  >
    <button
      v-for="star in 5"
      :key="star"
      type="button"
      class="p-0.5 leading-none focus:outline-none"
      :class="{ 'cursor-default': readonly, 'hover:scale-110 transition-transform': !readonly }"
      :aria-label="`${star} star${star > 1 ? 's' : ''}`"
      @mouseenter="!readonly && (hovered = star)"
      @click="select(star)"
    >
      <svg
        xmlns="http://www.w3.org/2000/svg"
        viewBox="0 0 24 24"
        class="h-4 w-4 transition-colors"
        :class="
          (hovered !== null ? star <= hovered : star <= (modelValue ?? 0))
            ? 'fill-amber-400 text-amber-400'
            : 'fill-transparent text-stone-500'
        "
        stroke="currentColor"
        stroke-width="1.5"
        stroke-linecap="round"
        stroke-linejoin="round"
      >
        <polygon
          points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"
        />
      </svg>
    </button>
  </div>
</template>
