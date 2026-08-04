<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      leave-active-class="transition-opacity duration-200"
      leave-to-class="opacity-0"
    >
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-6"
        @click.self="emit('cancel')"
      >
        <div
          class="w-full max-w-sm rounded-[28px] bg-surface p-6 shadow-2xl"
          role="dialog"
          aria-modal="true"
        >
          <h2 class="text-title-lg text-on-surface">{{ title }}</h2>

          <input
            v-model="name"
            type="text"
            placeholder="Playlist name"
            class="mt-4 w-full rounded-xl bg-surface-variant px-3 py-2.5 text-body-lg text-on-surface outline-none placeholder:text-on-surface-variant"
            @keyup.enter="submit"
          />

          <div class="mt-4 max-h-[40vh] grid grid-cols-8 gap-1 overflow-y-auto">
            <button
              v-for="emoji in PLAYLIST_EMOJIS"
              :key="emoji"
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-lg text-title-md leading-none"
              :class="emoji === icon ? 'bg-primary-container' : ''"
              @click="icon = emoji"
            >{{ emoji }}</button>
          </div>

          <div class="mt-6 flex justify-end gap-2">
            <button
              type="button"
              class="rounded-full px-4 py-2 text-label-lg text-on-surface-variant"
              @click="emit('cancel')"
            >Cancel</button>
            <button
              type="button"
              class="rounded-full px-4 py-2 text-label-lg text-primary disabled:opacity-50"
              :disabled="name.trim().length === 0"
              @click="submit"
            >{{ confirmLabel }}</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";

/** The exact palette and order used by the Android PlaylistsScreen. */
const PLAYLIST_EMOJIS = [
  "🎵", "🎶", "🎸", "🎹", "🎺", "🎻", "🥁", "🎷",
  "🎤", "🎧", "📻", "🎼", "🎙", "🎛", "🎚", "🔊",
  "❤️", "💜", "💚", "💙", "💛", "🧡", "🖤", "🤍",
  "🔥", "⭐", "🌟", "✨", "💫", "🌙", "☀️", "🌈",
  "🏃", "💪", "🧘", "🎉", "🎊", "🥳", "😴", "😌",
  "🌿", "🌺", "🍂", "🌊", "⛰️", "🌃", "🌆", "🏖️",
  "🎮", "📚", "🏀", "⚽", "🚀", "🌍", "🦋", "🐾",
];

const props = defineProps<{
  open: boolean;
  title: string;
  confirmLabel: string;
  initialName?: string;
  initialIcon?: string;
}>();

const emit = defineEmits<{ confirm: [name: string, icon: string]; cancel: [] }>();

const name = ref(props.initialName ?? "");
const icon = ref(props.initialIcon ?? "🎵");

watch(
  () => props.open,
  (isOpen) => {
    if (!isOpen) return;
    name.value = props.initialName ?? "";
    icon.value = props.initialIcon ?? "🎵";
  },
);

function submit(): void {
  const trimmed = name.value.trim();
  if (!trimmed) return;
  emit("confirm", trimmed, icon.value);
}
</script>
