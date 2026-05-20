<template>
  <Teleport to="body">
    <div
      v-if="modelValue"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      @click.self="emit('update:modelValue', false)"
    >
      <div class="w-full max-w-xs rounded-xl border border-stone-700 bg-stone-900 p-5 shadow-2xl">
        <h3 class="mb-3 text-sm font-semibold text-stone-100">{{ title }}</h3>

        <!-- Emoji picker -->
        <div class="mb-3 overflow-hidden rounded border border-stone-700 bg-stone-800/60">
          <EmojiPicker v-model="localIcon" />
        </div>

        <!-- Selected emoji preview -->
        <div class="mb-3 flex items-center gap-2 text-xs text-stone-400">
          <span class="text-base">{{ localIcon || '🎵' }}</span>
          <span class="text-stone-500">{{ localIcon ? 'Click to deselect' : 'Default emoji will be used' }}</span>
        </div>

        <!-- Name input -->
        <input
          ref="inputEl"
          v-model="localName"
          type="text"
          placeholder="Playlist name"
          class="w-full rounded border border-stone-600 bg-stone-800 px-3 py-2 text-sm text-stone-200 placeholder-stone-500 focus:border-accent focus:outline-none"
          maxlength="80"
          @keydown.enter="confirm"
          @keydown.esc="emit('update:modelValue', false)"
        />

        <div class="mt-4 flex justify-end gap-2">
          <button
            type="button"
            class="rounded px-3 py-1.5 text-sm text-stone-400 hover:bg-stone-800"
            @click="emit('update:modelValue', false)"
          >Cancel</button>
          <button
            type="button"
            class="rounded bg-accent px-3 py-1.5 text-sm text-white hover:bg-[var(--accent-hover)]"
            @click="confirm"
          >{{ confirmLabel }}</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from "vue";
import EmojiPicker from "./EmojiPicker.vue";

const props = defineProps<{
  modelValue: boolean;
  title: string;
  confirmLabel?: string;
  initialName?: string;
  initialIcon?: string | null;
}>();

const emit = defineEmits<{
  "update:modelValue": [value: boolean];
  confirm: [name: string, icon: string | null];
}>();

const localName = ref(props.initialName ?? "");
const localIcon = ref<string | null>(props.initialIcon ?? null);
const inputEl = ref<HTMLInputElement | null>(null);

watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      localName.value = props.initialName ?? "";
      localIcon.value = props.initialIcon ?? null;
      nextTick(() => inputEl.value?.focus());
    }
  },
);

function confirm(): void {
  const name = localName.value.trim();
  if (!name) return;
  emit("confirm", name, localIcon.value);
  emit("update:modelValue", false);
}
</script>
