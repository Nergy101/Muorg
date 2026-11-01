<template>
  <div>
    <button
      type="button"
      :class="[SECTION, 'flex w-full items-center gap-1.5 text-left']"
      :aria-expanded="open"
      @click="open = !open"
    >
      <MageIcon v-if="icon" :name="icon" class="h-3.5 w-3.5 shrink-0" />
      <span class="flex-1">{{ title }}</span>
      <MageIcon
        name="chevron-down"
        class="h-4 w-4 shrink-0 text-on-surface-variant transition-transform"
        :class="open ? 'rotate-180' : ''"
      />
    </button>
    <div v-show="open">
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
    icon?: string;
    /** Sections start collapsed unless this is set (the version section). */
    defaultOpen?: boolean;
  }>(),
  { defaultOpen: false },
);

const open = ref(props.defaultOpen);

const SECTION = "px-4 pt-5 pb-1 text-label-sm uppercase tracking-[0.8px] text-primary";
</script>
