<template>
  <Teleport to="body">
    <Transition
      enter-active-class="sheet-motion"
      enter-from-class="opacity-0"
      leave-active-class="sheet-motion"
      leave-to-class="opacity-0"
    >
      <div
        v-if="open"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-6"
        @click.self="emit('cancel')"
      >
        <div
          class="glass-strong w-full max-w-sm rounded-[28px] p-6 shadow-2xl"
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

          <div class="mt-4">
            <PlaylistIconPicker v-model="icon" />
          </div>

          <div class="mt-6 flex justify-end gap-2">
            <button
              type="button"
              class="rounded-full px-4 py-2 text-label-lg text-on-surface-variant"
              @click="emit('cancel')"
            >Cancel</button>
            <button
              type="button"
              class="flex items-center gap-1.5 rounded-full px-4 py-2 text-label-lg text-primary disabled:opacity-50"
              :disabled="pending || name.trim().length === 0"
              @click="submit"
            >
              <MageIcon v-if="pending" name="refresh" class="h-4 w-4 animate-spin" />
              <span>{{ confirmLabel }}</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch } from "vue";
import MageIcon from "./MageIcon.vue";
import PlaylistIconPicker from "./PlaylistIconPicker.vue";
import { DEFAULT_PLAYLIST_ICON } from "@shared/playlistIcons";

const props = defineProps<{
  open: boolean;
  title: string;
  confirmLabel: string;
  initialName?: string;
  initialIcon?: string;
}>();

const emit = defineEmits<{ confirm: [name: string, icon: string]; cancel: [] }>();

const name = ref(props.initialName ?? "");
const icon = ref(props.initialIcon ?? DEFAULT_PLAYLIST_ICON);

// True from submit until the parent closes the dialog after the async save —
// drives the confirm button's spinner so it's clear the app is working.
const pending = ref(false);

watch(
  () => props.open,
  (isOpen) => {
    if (!isOpen) {
      pending.value = false;
      return;
    }
    name.value = props.initialName ?? "";
    icon.value = props.initialIcon ?? DEFAULT_PLAYLIST_ICON;
  },
);

function submit(): void {
  const trimmed = name.value.trim();
  if (!trimmed || pending.value) return;
  pending.value = true;
  emit("confirm", trimmed, icon.value);
}
</script>
