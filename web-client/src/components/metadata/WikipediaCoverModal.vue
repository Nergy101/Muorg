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
        class="fixed inset-0 z-[70] flex items-center justify-center bg-black/60 p-4"
        role="dialog"
        aria-modal="true"
        aria-label="Image from Wikipedia"
        @click.self="emit('close')"
      >
        <div class="glass-strong flex max-h-[90vh] w-full max-w-lg flex-col gap-4 rounded-[28px] p-5 shadow-2xl">
          <p v-if="imageUrl" class="text-body-lg text-on-surface">Use this image?</p>
          <p v-if="loading" class="py-6 text-center text-body-md text-on-surface-variant">Searching Wikipedia…</p>
          <p v-else-if="error" class="py-6 text-center text-body-md text-error">{{ error }}</p>
          <template v-else-if="imageUrl">
            <img
              :src="imageUrl"
              alt="Wikipedia result"
              class="max-h-[60vh] w-full rounded-xl border border-outline/20 object-contain"
            />
            <div class="flex justify-end gap-2">
              <button
                type="button"
                class="rounded-full px-4 py-2 text-label-lg text-on-surface-variant"
                @click="emit('close')"
              >Cancel</button>
              <button
                type="button"
                class="rounded-full bg-primary px-4 py-2 text-label-lg text-on-primary disabled:opacity-50"
                :disabled="applying"
                @click="emit('apply')"
              >{{ applying ? "Applying…" : "Yes, use this image" }}</button>
            </div>
          </template>
          <template v-else>
            <p class="py-2 text-body-md text-on-surface-variant">No image was found on Wikipedia.</p>
            <div class="flex justify-end">
              <button
                type="button"
                class="rounded-full px-4 py-2 text-label-lg text-on-surface-variant"
                @click="emit('close')"
              >Close</button>
            </div>
          </template>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
/**
 * The confirm step for a cover found on Wikipedia: show it full size and ask,
 * rather than writing it straight into the tag. The heuristic that picked it
 * (`@shared/utils/wikipediaCover`) is good but not reliable enough to skip a
 * look.
 */
defineProps<{
  open: boolean;
  imageUrl: string | null;
  loading: boolean;
  error: string | null;
  applying: boolean;
}>();

const emit = defineEmits<{ close: []; apply: [] }>();
</script>
