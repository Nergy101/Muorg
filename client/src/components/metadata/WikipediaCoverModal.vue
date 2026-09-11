<script setup lang="ts">
/**
 * The confirm step for a cover found on Wikipedia: show it full size and ask,
 * rather than writing it straight into the tag. The heuristic that picked it
 * (`utils/wikipediaCover.ts`) is good but not reliable enough to skip a look.
 */
defineProps<{
  open: boolean;
  imageUrl: string | null;
  loading: boolean;
  error: string | null;
  applying: boolean;
}>();

const emit = defineEmits<{ (e: "close"): void; (e: "apply"): void }>();
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[100] flex items-center justify-center bg-stone-950/80 p-4"
      role="dialog"
      aria-modal="true"
      aria-label="Image from Wikipedia"
      @click.self="emit('close')"
    >
      <div
        class="flex max-h-[90vh] max-w-lg flex-col gap-4 rounded-lg border border-stone-600 bg-stone-800 p-4 shadow-xl"
        @click.stop
      >
        <p v-if="imageUrl" class="text-sm font-medium text-stone-300">Use this image?</p>
        <p v-if="loading" class="text-xs text-stone-500">Searching Wikipedia…</p>
        <p v-else-if="error" class="text-xs text-amber-400">{{ error }}</p>
        <template v-else-if="imageUrl">
          <img
            :src="imageUrl"
            alt="Wikipedia result"
            class="max-h-[60vh] w-full rounded object-contain border border-stone-600"
          />
          <div class="flex justify-end gap-2">
            <button
              type="button"
              class="rounded border border-stone-600 px-3 py-1.5 text-xs text-stone-400 hover:bg-stone-600 hover:text-stone-200"
              @click="emit('close')"
            >
              Cancel
            </button>
            <button
              type="button"
              class="accent-btn rounded px-3 py-1.5 text-xs text-white disabled:opacity-50"
              style="background-color: #5b7c32"
              :disabled="applying"
              @click="emit('apply')"
            >
              {{ applying ? "Applying…" : "Yes, use this image" }}
            </button>
          </div>
        </template>
        <template v-else>
          <p class="text-sm text-stone-400">No image was found on Wikipedia.</p>
          <div class="flex justify-end">
            <button
              type="button"
              class="rounded border border-stone-600 px-3 py-1.5 text-xs text-stone-400 hover:bg-stone-600 hover:text-stone-200"
              @click="emit('close')"
            >
              Close
            </button>
          </div>
        </template>
      </div>
    </div>
  </Teleport>
</template>
