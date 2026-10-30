<template>
  <button type="button" class="flex min-h-14 w-full items-center justify-between gap-4 px-4 py-2 text-left" @click="openKeyMap">
    <div class="min-w-0 flex-1">
      <p class="text-body-lg text-on-surface">Keyboard shortcuts</p>
      <p class="text-body-sm text-on-surface-variant">Press ? anywhere to see them</p>
    </div>
    <MageIcon name="chevron-right" class="h-5 w-5 shrink-0 text-on-surface-variant" />
  </button>
</template>

<script setup lang="ts">
import MageIcon from "../MageIcon.vue";
import { openKeyMap } from "../../composables/useKeyMapModal";
</script>
