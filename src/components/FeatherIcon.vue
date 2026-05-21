<script setup lang="ts">
import { computed } from "vue";
import feather from "feather-icons";

const props = defineProps<{
  name: string;
  class?: string;
}>();

const svgContent = computed(() => {
  const icon = (feather.icons as Record<string, (typeof feather.icons)[keyof typeof feather.icons] | undefined>)[props.name];
  if (!icon) return "";
  return icon.toSvg({
    class: `feather feather-${props.name} ${props.class ?? ""}`.trim(),
    stroke: "currentColor",
  });
});
</script>

<template>
  <span
    class="inline-flex shrink-0 items-center justify-center [&>svg]:block"
    role="img"
    aria-hidden="true"
    v-html="svgContent"
  />
</template>
