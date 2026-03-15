<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import FeatherIcon from "../shared/FeatherIcon.vue";

const props = defineProps<{
  open: boolean;
}>();

const emit = defineEmits<{
  (e: "update:open", value: boolean): void;
}>();

const keyMapModalRef = ref<HTMLDivElement | null>(null);

const keyMapEntries: { keys: string; description: string }[] = [
  { keys: "Ctrl+F / ⌘F", description: "Focus search bar" },
  { keys: "Ctrl+R / ⌘R", description: "Refresh whole library (all folders, all reports)" },
  { keys: "Ctrl+M / ⌘M", description: "Toggle metadata editor panel for current selection" },
  { keys: "Ctrl+L / ⌘L", description: "Show library panel" },
  { keys: "Ctrl+P / ⌘P", description: "Toggle full player panel" },
  { keys: "Ctrl+S / ⌘S", description: "Open maximized player (requires at least one track selected)" },
  { keys: "Ctrl+Q / ⌘Q", description: "Toggle queue panel" },
  { keys: "Ctrl+A / ⌘A", description: "Select all tracks in current view and enable multi-select" },
  { keys: "Ctrl+K / ⌘K", description: "Open key map" },
  { keys: "Escape", description: "Close metadata panel (discard changes)" },
  { keys: "↓ Arrow Down", description: "Move focus down in track list" },
  { keys: "↑ Arrow Up", description: "Move focus up in track list" },
  { keys: "Space", description: "On group row: expand or collapse. On track row: select (add to selection in multi-select)" },
  { keys: "Enter", description: "With one track selected: start playback or pause if already playing" },
];

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
            <div v-for="entry in keyMapEntries" :key="entry.keys" class="flex gap-3 text-sm">
              <dt class="w-36 shrink-0 font-mono text-stone-400">{{ entry.keys }}</dt>
              <dd class="min-w-0 text-stone-300">{{ entry.description }}</dd>
            </div>
          </dl>
        </div>
      </div>
    </div>
  </Teleport>
</template>

