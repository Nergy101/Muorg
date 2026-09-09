<template>
  <div class="flex h-[14px] items-end gap-[2px]" aria-hidden="true">
    <div
      v-for="(delay, i) in DELAYS"
      :key="i"
      class="w-[3px] rounded-sm bg-current"
      :class="props.class"
      :style="{
        animation: `eq 380ms cubic-bezier(0.4,0,0.2,1) ${delay}ms infinite alternate`,
        animationPlayState: props.paused ? 'paused' : 'running',
      }"
    />
  </div>
</template>

<script setup lang="ts">
const DELAYS = [0, 160, 80];

/** `paused` freezes the bars where they stand rather than hiding them, so a
    paused track still reads as the current one. */
const props = defineProps<{ class?: string; paused?: boolean }>();
</script>

<style>
/* Deliberately not `scoped`: the animation is applied through an inline
   `:style` binding, which the SFC compiler cannot rewrite, so a scoped
   `@keyframes eq` would be renamed out from under it. Living with the
   component rather than in each app's global stylesheet keeps the two in
   step. */
@keyframes eq {
  from { height: 25%; }
  to { height: 100%; }
}
</style>
