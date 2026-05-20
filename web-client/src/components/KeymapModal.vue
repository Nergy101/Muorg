<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-150"
      enter-from-class="opacity-0"
      leave-active-class="transition-opacity duration-150"
      leave-to-class="opacity-0"
    >
      <div
        v-if="modelValue"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4"
        @click.self="emit('update:modelValue', false)"
      >
        <div
          class="w-full max-w-sm rounded-xl border border-stone-700 bg-stone-900 shadow-2xl"
          @click.stop
        >
          <!-- Header -->
          <div class="flex items-center justify-between border-b border-stone-700 px-4 py-3">
            <h2 class="text-sm font-semibold text-stone-200">Mouse controls</h2>
            <button
              type="button"
              class="inline-flex h-6 w-6 items-center justify-center rounded text-stone-400 hover:bg-stone-700 hover:text-stone-200"
              @click="emit('update:modelValue', false)"
            >
              <FeatherIcon name="x" class="h-3.5 w-3.5" />
            </button>
          </div>

          <!-- Controls table -->
          <div class="p-4">
            <table class="w-full text-sm">
              <tbody>
                <template v-for="(section, si) in sections" :key="si">
                  <tr>
                    <td colspan="2" class="pb-1 pt-3 text-xs font-semibold uppercase tracking-wide text-stone-500 first:pt-0">
                      {{ section.title }}
                    </td>
                  </tr>
                  <tr
                    v-for="(row, ri) in section.rows"
                    :key="ri"
                    class="border-t border-stone-800"
                  >
                    <td class="py-2 pr-4 text-stone-400">{{ row.action }}</td>
                    <td class="py-2 text-right">
                      <kbd class="rounded bg-stone-800 px-2 py-0.5 text-xs font-mono text-stone-300 border border-stone-700">{{ row.binding }}</kbd>
                    </td>
                  </tr>
                </template>
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import FeatherIcon from "./FeatherIcon.vue";

defineProps<{ modelValue: boolean }>();
const emit = defineEmits<{ "update:modelValue": [value: boolean] }>();

const sections = [
  {
    title: "Tracks",
    rows: [
      { action: "Play track", binding: "Left click" },
      { action: "Play track", binding: "Double click" },
      { action: "Toggle selection", binding: "Left click" },
      { action: "Extend selection", binding: "Shift + click" },
      { action: "Context menu", binding: "Right click" },
    ],
  },
  {
    title: "Album groups",
    rows: [
      { action: "Collapse / expand", binding: "Left click" },
    ],
  },
  {
    title: "Album grid",
    rows: [
      { action: "Open album", binding: "Left click" },
      { action: "Context menu", binding: "Right click" },
    ],
  },
  {
    title: "Now playing",
    rows: [
      { action: "Expand player", binding: "Left click" },
      { action: "Context menu", binding: "Right click" },
    ],
  },
];
</script>
