<template>
  <div class="rounded-xl bg-surface-container/70 ring-1 ring-outline/10">
    <button
      type="button"
      class="flex w-full items-center gap-3 rounded-xl px-4 py-3 text-left transition-colors hover:bg-surface-container"
      :aria-expanded="open"
      @click="open = !open"
    >
      <MageIcon v-if="icon" :name="icon" class="h-4 w-4 shrink-0 text-primary" />
      <span class="min-w-0 flex-1">
        <span class="block text-body-lg font-semibold text-on-surface">{{ title }}</span>
        <span v-if="subtitle" class="block truncate text-body-sm text-on-surface-variant">{{ subtitle }}</span>
      </span>
      <MageIcon
        name="chevron-down"
        class="h-4 w-4 shrink-0 text-on-surface-variant transition-transform"
        :class="open ? 'rotate-180' : ''"
      />
    </button>
    <div v-show="open" class="border-t border-outline/15">
      <slot />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from "vue";
import MageIcon from "../MageIcon.vue";

const props = withDefaults(
  defineProps<{
    title: string;
    subtitle?: string;
    icon?: string;
    /** Sections start collapsed unless this is set. */
    defaultOpen?: boolean;
  }>(),
  { defaultOpen: false },
);

const open = ref(props.defaultOpen);
</script>
