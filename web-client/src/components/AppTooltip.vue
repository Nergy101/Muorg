<template>
  <Teleport to="body">
    <div
      v-if="tooltip"
      ref="el"
      role="tooltip"
      class="glass-strong pointer-events-none fixed z-[70] max-w-[16rem] rounded-lg px-2.5 py-1.5 text-label-sm text-on-surface shadow-xl"
      :style="style"
    >{{ tooltip.text }}</div>
  </Teleport>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import { tooltip } from "../composables/useTooltips";

const GAP = 8;
const EDGE = 8;

const el = ref<HTMLElement | null>(null);
const style = ref<Record<string, string>>({ left: "0px", top: "0px", visibility: "hidden" });

/** Centre the tip above its control, flipping below when there is no room and
 *  clamping to the viewport. Measured after render so the real size is used. */
watch(tooltip, async (t) => {
  if (!t) return;
  style.value = { left: "0px", top: "0px", visibility: "hidden" };
  await nextTick();
  const node = el.value;
  if (!node) return;
  const { width, height } = node.getBoundingClientRect();
  const centre = t.rect.left + t.rect.width / 2;
  const left = Math.min(Math.max(centre - width / 2, EDGE), window.innerWidth - width - EDGE);
  const above = t.rect.top - height - GAP;
  const top = above >= EDGE ? above : t.rect.top + t.rect.height + GAP;
  style.value = { left: `${Math.round(left)}px`, top: `${Math.round(top)}px` };
});
</script>
