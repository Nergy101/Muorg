<template>
  <div class="flex flex-col" style="height: 280px;">
    <!-- Search -->
    <div class="shrink-0 border-b border-stone-700 p-2">
      <input
        v-model="search"
        type="text"
        placeholder="Search emoji…"
        class="w-full rounded border border-stone-600 bg-stone-800 px-2 py-1 text-xs text-stone-200 placeholder-stone-500 focus:border-accent focus:outline-none"
      />
    </div>

    <!-- Grid -->
    <div class="min-h-0 flex-1 overflow-y-auto p-1">
      <template v-if="search.length >= 1">
        <div class="flex flex-wrap gap-0.5">
          <button
            v-for="e in searchResults"
            :key="e.char"
            type="button"
            :title="e.name"
            :class="[
              'flex h-8 w-8 items-center justify-center rounded text-lg transition-colors hover:bg-stone-700',
              modelValue === e.char ? 'bg-stone-600 ring-1 ring-accent' : '',
            ]"
            @click="toggle(e.char)"
          >{{ e.char }}</button>
          <p v-if="searchResults.length === 0" class="w-full py-4 text-center text-xs text-stone-500">No results</p>
        </div>
      </template>
      <template v-else>
        <div v-for="group in groups" :key="group.name" class="mb-2">
          <p class="mb-0.5 px-1 text-[10px] font-semibold uppercase tracking-wide text-stone-500">{{ group.name }}</p>
          <div class="flex flex-wrap gap-0.5">
            <button
              v-for="e in group.items"
              :key="e.char"
              type="button"
              :title="e.name"
              :class="[
                'flex h-8 w-8 items-center justify-center rounded text-lg transition-colors hover:bg-stone-700',
                modelValue === e.char ? 'bg-stone-600 ring-1 ring-accent' : '',
              ]"
              @click="toggle(e.char)"
            >{{ e.char }}</button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from "vue";
import rawData from "unicode-emoji-json/data-by-emoji.json";

interface EmojiEntry { char: string; name: string; group: string }

const allEmoji: EmojiEntry[] = Object.entries(rawData as Record<string, { name: string; group: string }>).map(
  ([char, meta]) => ({ char, name: meta.name, group: meta.group }),
);

const groups = computed(() => {
  const map = new Map<string, EmojiEntry[]>();
  for (const e of allEmoji) {
    let arr = map.get(e.group);
    if (!arr) { arr = []; map.set(e.group, arr); }
    arr.push(e);
  }
  return Array.from(map.entries()).map(([name, items]) => ({ name, items }));
});

const search = ref("");

const searchResults = computed(() => {
  const q = search.value.toLowerCase();
  if (!q) return allEmoji;
  return allEmoji.filter((e) => e.name.includes(q) || e.char === q);
});

const props = defineProps<{ modelValue: string | null }>();
const emit = defineEmits<{ "update:modelValue": [value: string | null] }>();

function toggle(char: string): void {
  emit("update:modelValue", props.modelValue === char ? null : char);
}
</script>
