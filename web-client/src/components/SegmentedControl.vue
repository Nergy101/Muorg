<template>
  <div class="flex gap-1 rounded-full bg-surface p-1">
    <button
      v-for="opt in options"
      :key="opt.value"
      type="button"
      class="flex-1 rounded-full px-3 py-1.5 text-label-lg transition-colors"
      :class="opt.value === modelValue
        ? 'bg-primary/[0.18] text-primary'
        : 'text-on-surface-variant'"
      @click="emit('update:modelValue', opt.value)"
    >{{ opt.label }}</button>
  </div>
</template>

<script setup lang="ts">
defineProps<{ modelValue: string; options: { value: string; label: string }[] }>();
const emit = defineEmits<{ "update:modelValue": [string] }>();
</script>
