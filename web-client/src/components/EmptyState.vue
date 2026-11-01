<template>
  <div
    class="flex items-center text-center"
    :class="compact ? 'flex-row gap-4 py-4 text-left' : 'flex-col gap-2 px-6 py-12'"
  >
    <div :class="compact ? 'w-20 shrink-0' : 'w-40 sm:w-48'">
      <OrcIllustration variant="empty" />
    </div>
    <div class="flex min-w-0 flex-col" :class="compact ? 'items-start' : 'items-center'">
      <p :class="compact ? 'text-body-md font-medium' : 'text-body-lg'" class="text-on-surface">
        {{ title }}
      </p>
      <p v-if="body" class="mt-1 text-body-md text-on-surface-variant">{{ body }}</p>
      <div v-if="$slots.default" class="mt-4"><slot /></div>
    </div>
  </div>
</template>

<script setup lang="ts">
import OrcIllustration from "./OrcIllustration.vue";

/** The generic "it's empty here" state: the Orc with an empty crate. */
withDefaults(
  defineProps<{
    title?: string;
    body?: string;
    /** Inline, side-by-side layout for shelves and table rows. */
    compact?: boolean;
  }>(),
  { title: "It seems to be empty here", body: undefined, compact: false },
);
</script>
