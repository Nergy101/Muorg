<script setup lang="ts">
import SharedMageIcon from "@shared/components/MageIcon.vue";

// Thin re-export so the web client's existing imports keep working; the
// component and the vendored SVG set live in the shared tree (@shared) and
// are also used by the desktop client.
const props = defineProps<{
  name: string;
  class?: string;
}>();
</script>

<template>
  <SharedMageIcon :name="props.name" :class="props.class" />
</template>
