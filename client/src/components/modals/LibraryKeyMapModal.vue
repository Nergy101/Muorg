<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { KEY_MAP_ENTRIES } from "@shared/utils/keyMap";

const props = defineProps<{
  open: boolean;
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
}>();

const keyMapModalRef = ref<HTMLDivElement | null>(null);

watch(
  () => props.open,
  async (open) => {
    if (!open) return;
    await nextTick();
    keyMapModalRef.value?.focus();
  },
);

function close() {
  emit("update:open", false);
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") close();
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="props.open"
      ref="keyMapModalRef"
      class="fixed inset-0 z-[300] flex items-center justify-center bg-stone-950/70 p-4 outline-none"
      role="dialog"
      aria-modal="true"
      aria-labelledby="keymap-modal-title"
      tabindex="-1"
      @keydown="onKeydown"
      @click.self="close"
    >
      <div class="w-full max-w-md rounded-lg border border-stone-600 bg-stone-800 shadow-xl" @click.stop>
        <div class="flex items-center justify-between border-b border-stone-700 px-4 py-3">
          <h2 id="keymap-modal-title" class="text-sm font-semibold text-stone-200">Key map</h2>
          <button
            type="button"
            class="rounded p-1.5 text-stone-500 hover:bg-stone-600 hover:text-stone-200"
            aria-label="Close"
            @click="close"
          >
            <FeatherIcon name="x" class="h-4 w-4" />
          </button>
        </div>
        <div class="max-h-[70vh] overflow-y-auto p-4">
          <dl class="space-y-3">
            <div v-for="entry in KEY_MAP_ENTRIES" :key="entry.keys" class="flex gap-3 text-sm">
              <dt class="w-36 shrink-0 font-mono text-stone-400">{{ entry.keys }}</dt>
              <dd class="min-w-0 text-stone-300">{{ entry.description }}</dd>
            </div>
          </dl>
        </div>
      </div>
    </div>
  </Teleport>
</template>

