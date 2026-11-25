<script setup lang="ts">
import { computed, ref } from "vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

/** Pretty custom selection box: pill button + popover list anchored to it. */
const props = defineProps<{
  modelValue: string;
  options: { value: string; label: string }[];
  label?: string;
}>();
const emit = defineEmits<{ (e: "update:modelValue", value: string): void }>();

const open = ref(false);
const btnRef = ref<HTMLElement | null>(null);
const pos = ref({ x: 0, y: 0 });

function toggle() {
  if (!open.value && btnRef.value) {
    const rect = btnRef.value.getBoundingClientRect();
    pos.value = { x: rect.right, y: rect.bottom + 4 };
  }
  open.value = !open.value;
}
function pick(value: string) {
  emit("update:modelValue", value);
  open.value = false;
}
const currentLabel = computed(
  () => props.options.find((o) => o.value === props.modelValue)?.label ?? props.modelValue,
);
</script>

<template>
  <label class="relative flex items-center gap-1.5 text-xs text-on-surface-variant">
    <span v-if="label">{{ label }}</span>
    <button
      ref="btnRef"
      type="button"
      class="flex items-center gap-1.5 rounded-full border border-outline bg-surface py-1 pl-3 pr-2 text-xs font-medium text-on-surface transition-colors hover:bg-surface-variant"
      :aria-expanded="open"
      @click="toggle"
    >
      {{ currentLabel }}
      <FeatherIcon name="chevron-down" class="h-3.5 w-3.5 text-on-surface-variant" />
    </button>
    <Teleport to="body">
      <div
        v-if="open"
        class="fixed z-[400] min-w-[140px] rounded-xl bg-surface py-1 shadow-xl ring-1 ring-outline/30"
        :style="{ left: pos.x + 'px', top: pos.y + 'px', transform: 'translateX(-100%)' }"
      >
        <button
          v-for="o in options"
          :key="o.value"
          type="button"
          class="flex w-full items-center justify-between gap-3 px-3 py-1.5 text-left text-xs hover:bg-surface-container"
          :class="o.value === modelValue ? 'font-semibold text-on-primary-container' : 'text-on-surface'"
          @click="pick(o.value)"
        >
          {{ o.label }}
          <FeatherIcon v-if="o.value === modelValue" name="check" class="h-3.5 w-3.5" />
        </button>
      </div>
      <div v-if="open" class="fixed inset-0 z-[399]" @click="open = false" />
    </Teleport>
  </label>
</template>
