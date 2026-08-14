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
          <p class="mt-3 text-body-md text-on-surface-variant">{{ message }}</p>
          <div class="mt-6 flex justify-end gap-2">
            <button
              type="button"
              class="rounded-full px-4 py-2 text-label-lg text-on-surface-variant"
              @click="emit('cancel')"
            >{{ cancelLabel ?? "Cancel" }}</button>
            <button
              type="button"
              class="rounded-full px-4 py-2 text-label-lg"
              :class="danger ? 'text-error' : 'text-primary'"
              @click="emit('confirm')"
            >{{ confirmLabel }}</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
defineProps<{
  open: boolean;
  title: string;
  message: string;
  confirmLabel: string;
  cancelLabel?: string;
  danger?: boolean;
}>();
const emit = defineEmits<{ confirm: []; cancel: [] }>();
</script>
