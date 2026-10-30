<template>
  <BottomSheet :open="keyMapOpen" @close="closeKeyMap">
    <div class="px-6 pb-2 text-title-md text-on-surface">Keyboard shortcuts</div>
    <div class="px-6 pb-6">
      <section v-for="group in WEB_KEY_MAP" :key="group.title" class="pt-3">
        <h3 class="pb-1 text-label-lg uppercase tracking-wide text-on-surface-variant">{{ group.title }}</h3>
        <dl>
          <div
            v-for="entry in group.entries"
            :key="entry.keys + entry.description"
            class="flex items-start justify-between gap-4 py-1.5"
          >
            <dt class="max-w-[55%] shrink-0 font-mono text-body-sm text-on-surface">{{ entry.keys }}</dt>
            <dd class="text-right text-body-sm text-on-surface-variant">{{ entry.description }}</dd>
          </div>
        </dl>
      </section>
    </div>
  </BottomSheet>
</template>

<script setup lang="ts">
import BottomSheet from "./BottomSheet.vue";
import { closeKeyMap, keyMapOpen } from "../composables/useKeyMapModal";
import { WEB_KEY_MAP } from "../keyMap";
</script>
