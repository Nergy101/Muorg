<script setup lang="ts">
import { ref, watch, onMounted, onUnmounted, nextTick } from "vue";

const props = defineProps<{ text: string }>();

const containerRef = ref<HTMLDivElement | null>(null);
const shouldScroll = ref(false);
const distance = ref(0);

function measure() {
  const el = containerRef.value;
  if (!el) { shouldScroll.value = false; distance.value = 0; return; }
  const diff = el.scrollWidth - el.clientWidth;
  if (diff > 4) {
    shouldScroll.value = true;
    distance.value = diff;
  } else {
    shouldScroll.value = false;
    distance.value = 0;
  }
}

watch(() => props.text, () => {
  shouldScroll.value = false;
  distance.value = 0;
  nextTick(measure);
});

let ro: ResizeObserver | null = null;

onMounted(() => {
  nextTick(measure);
  ro = new ResizeObserver(measure);
  if (containerRef.value) ro.observe(containerRef.value);
});

onUnmounted(() => ro?.disconnect());
</script>

<template>
  <div ref="containerRef" class="marquee-cell">
    <template v-if="!shouldScroll">{{ text }}</template>
    <span v-else class="marquee-cell-inner" :style="{ '--d': distance + 'px' }">{{ text }}</span>
  </div>
</template>

<style scoped>
.marquee-cell {
  position: relative;
  overflow: hidden;
  white-space: nowrap;
}
.marquee-cell-inner {
  display: inline-block;
  will-change: transform;
  animation: marquee-cell-bounce 4s ease-in-out infinite alternate;
}
@keyframes marquee-cell-bounce {
  0%, 15% { transform: translateX(0); }
  85%, 100% { transform: translateX(calc(-1 * var(--d))); }
}
</style>
