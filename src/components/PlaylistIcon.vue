<script setup lang="ts">
import { computed, ref } from "vue";
import { loadLucideIcons, type LucideIcons } from "../lucideIcons";
import { lucideComponentName, resolvePlaylistIcon } from "../playlistIcons";

const props = defineProps<{
  /** Stored playlist icon (`name:#RRGGBB`); legacy/empty values render the default. */
  icon?: string | null;
  class?: string;
}>();

const iconsMap = ref<LucideIcons | null>(null);
void loadLucideIcons().then((m) => (iconsMap.value = m));

const resolved = computed(() => resolvePlaylistIcon(props.icon));
const component = computed(() => {
  const map = iconsMap.value;
  if (!map) return null;
  return (
    map[lucideComponentName(resolved.value.name) as keyof LucideIcons] ?? map.EighthNotes
  );
});
</script>

<template>
  <component
    :is="component"
    v-if="component"
    :color="resolved.color"
    :stroke-width="2"
    :class="props.class"
    role="img"
  />
</template>
