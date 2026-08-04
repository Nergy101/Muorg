<template>
  <div ref="box" class="w-full overflow-hidden whitespace-nowrap">
    <span
      ref="inner"
      class="inline-block max-w-full align-bottom"
      :class="[props.class, overflowing ? '' : 'truncate']"
    >{{ text }}</span>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";

/** Android basicMarquee(initialDelayMillis = 1200, repeatDelayMillis = 1200, velocity = 40.dp). */
const HOLD_MS = 1200;
const VELOCITY_PX_PER_S = 40;
const GAP_PX = 24;

const props = defineProps<{ text: string; class?: string }>();

const box = ref<HTMLElement | null>(null);
const inner = ref<HTMLElement | null>(null);
const overflowing = ref(false);

let anim: Animation | null = null;
let ro: ResizeObserver | null = null;

function apply(): void {
  const b = box.value;
  const s = inner.value;
  anim?.cancel();
  anim = null;
  if (!b || !s) return;

  // Measure untruncated: the class is only added once we know it fits.
  const over = s.scrollWidth - b.clientWidth;
  overflowing.value = over > 1;
  if (over <= 1) return;

  const shift = over + GAP_PX;
  const scrollMs = (shift / VELOCITY_PX_PER_S) * 1000;
  const total = HOLD_MS + scrollMs + HOLD_MS;
  anim = s.animate(
    [
      { transform: "translateX(0)", offset: 0 },
      { transform: "translateX(0)", offset: HOLD_MS / total },
      { transform: `translateX(${-shift}px)`, offset: (HOLD_MS + scrollMs) / total },
      { transform: `translateX(${-shift}px)`, offset: 1 },
    ],
    { duration: total, iterations: Infinity, easing: "linear" },
  );
}

onMounted(() => {
  apply();
  ro = new ResizeObserver(() => apply());
  if (box.value) ro.observe(box.value);
});

onBeforeUnmount(() => {
  ro?.disconnect();
  anim?.cancel();
});

watch(
  () => props.text,
  () => requestAnimationFrame(apply),
);
</script>
