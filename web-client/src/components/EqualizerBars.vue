<template>
  <div class="flex h-[14px] items-end gap-[2px]" aria-hidden="true">
    <div
      v-for="(delay, i) in DELAYS"
      :key="i"
      class="w-[3px] rounded-sm bg-current"
      :class="props.class"
      :style="{ animation: `eq 380ms cubic-bezier(0.4,0,0.2,1) ${delay}ms infinite alternate` }"
    />
  </div>
</template>

<script setup lang="ts">
const DELAYS = [0, 160, 80];

const props = defineProps<{ class?: string }>();
</script>
