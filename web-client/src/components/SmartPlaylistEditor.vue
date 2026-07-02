<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="fixed inset-0 z-[400] flex items-center justify-center bg-black/60 p-4 backdrop-blur-sm"
      @click.self="emit('cancel')"
    >
      <div class="w-full max-w-lg rounded-xl border border-stone-600 bg-stone-800 p-5 shadow-2xl">
        <h2 class="mb-4 text-sm font-semibold text-stone-100">
          {{ isEditing ? 'Edit smart playlist' : 'New smart playlist' }}
        </h2>

        <!-- Name + emoji row -->
        <div class="relative mb-4 flex items-center gap-2">
          <button
            type="button"
            class="flex h-8 w-8 shrink-0 items-center justify-center rounded border border-stone-600 bg-stone-700 text-base leading-none hover:bg-stone-600"
            :class="showEmojiPicker ? 'ring-1 ring-stone-400' : ''"
            title="Pick emoji"
            @mousedown.prevent
            @click.stop="showEmojiPicker = !showEmojiPicker"
          >
            <span v-if="localIcon">{{ localIcon }}</span>
            <svg v-else width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" class="text-stone-400">
              <circle cx="12" cy="12" r="10" /><path d="M8 14s1.5 2 4 2 4-2 4-2" /><line x1="9" y1="9" x2="9.01" y2="9" /><line x1="15" y1="9" x2="15.01" y2="9" />
            </svg>
          </button>
          <input
            ref="nameInput"
            v-model="localName"
            type="text"
            placeholder="Playlist name…"
            maxlength="128"
            class="min-w-0 flex-1 rounded border border-stone-600 bg-stone-700 px-3 py-1.5 text-sm text-stone-200 outline-none focus:border-stone-400"
            @keydown.enter="confirm"
            @keydown.escape="emit('cancel')"
          />
          <EmojiPicker
            v-if="showEmojiPicker"
            :model-value="localIcon"
            class="absolute left-0 top-full z-50 mt-1"
            @click.stop
            @update:modelValue="(v) => { localIcon = v ?? ''; showEmojiPicker = false }"
          />
        </div>

        <!-- Rules -->
        <div class="mb-3 space-y-2">
          <div v-if="!rules.length" class="py-2 text-center text-xs text-stone-500">
            Add a rule to define which tracks match.
          </div>
          <div
            v-for="(rule, i) in rules"
            :key="i"
            class="flex items-center gap-2"
          >
            <!-- Field -->
            <select
              v-model="rule.field"
              class="rounded border border-stone-600 bg-stone-700 px-2 py-1 text-xs text-stone-200 outline-none focus:border-stone-400"
              @change="onFieldChange(rule)"
            >
              <option v-for="f in FIELDS" :key="f.value" :value="f.value">{{ f.label }}</option>
            </select>

            <!-- Operator -->
            <select
              v-model="rule.op"
              class="rounded border border-stone-600 bg-stone-700 px-2 py-1 text-xs text-stone-200 outline-none focus:border-stone-400"
            >
              <option v-for="op in opsForField(rule.field)" :key="op.value" :value="op.value">{{ op.label }}</option>
            </select>

            <!-- Value -->
            <template v-if="!valueHidden(rule.op)">
              <select
                v-if="fieldType(rule.field) === 'bool'"
                v-model="rule.value"
                class="w-24 rounded border border-stone-600 bg-stone-700 px-2 py-1 text-xs text-stone-200 outline-none focus:border-stone-400"
              >
                <option value="1">True</option>
                <option value="0">False</option>
              </select>
              <input
                v-else-if="fieldType(rule.field) === 'number'"
                v-model="rule.value"
                type="number"
                step="1"
                class="w-20 rounded border border-stone-600 bg-stone-700 px-2 py-1 text-xs text-stone-200 outline-none focus:border-stone-400"
              />
              <select
                v-else-if="rule.field === 'genre' && genres.length"
                v-model="rule.value"
                class="min-w-0 flex-1 rounded border border-stone-600 bg-stone-700 px-2 py-1 text-xs text-stone-200 outline-none focus:border-stone-400"
              >
                <option value="" disabled>Select genre…</option>
                <option v-for="g in genres" :key="g" :value="g">{{ g }}</option>
              </select>
              <input
                v-else
                v-model="rule.value"
                type="text"
                class="min-w-0 flex-1 rounded border border-stone-600 bg-stone-700 px-2 py-1 text-xs text-stone-200 outline-none focus:border-stone-400"
              />
            </template>
            <span v-else class="min-w-0 flex-1" />

            <button
              type="button"
              class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-stone-500 hover:bg-stone-700 hover:text-red-400"
              @click="removeRule(i)"
            >
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>
        </div>

        <!-- Add rule -->
        <button
          type="button"
          class="mb-5 flex items-center gap-1.5 text-xs text-stone-400 hover:text-stone-200"
          @click="addRule"
        >
          <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="16" /><line x1="8" y1="12" x2="16" y2="12" />
          </svg>
          Add rule
        </button>

        <!-- Actions -->
        <div class="flex justify-end gap-2">
          <button
            type="button"
            class="rounded px-3 py-1.5 text-xs text-stone-400 hover:bg-stone-700 hover:text-stone-200"
            @click="emit('cancel')"
          >
            Cancel
          </button>
          <button
            type="button"
            class="rounded bg-[#5b7c32] px-3 py-1.5 text-xs text-white hover:bg-[#6a9038] disabled:opacity-50"
            :disabled="!canConfirm"
            @click="confirm"
          >
            {{ isEditing ? 'Save' : 'Create' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch, nextTick } from "vue";
import EmojiPicker from "./EmojiPicker.vue";
import type { SmartRule } from "../types";

const props = defineProps<{
  visible: boolean;
  isEditing?: boolean;
  initialName?: string;
  initialIcon?: string;
  initialRules?: SmartRule[];
  genres?: string[];
}>();

const emit = defineEmits<{
  confirm: [name: string, icon: string, rules: SmartRule[]];
  cancel: [];
}>();

const localName = ref(props.initialName ?? "");
const localIcon = ref(props.initialIcon ?? "");
const showEmojiPicker = ref(false);
const rules = reactive<SmartRule[]>(
  props.initialRules ? [...props.initialRules.map((r) => ({ ...r }))] : [],
);
const nameInput = ref<HTMLInputElement | null>(null);

// Reset state whenever the modal opens with new initial values
watch(() => props.visible, (isOpen) => {
  if (isOpen) {
    localName.value = props.initialName ?? "";
    localIcon.value = props.initialIcon ?? "";
    showEmojiPicker.value = false;
    rules.splice(0, rules.length, ...(props.initialRules?.map((r) => ({ ...r })) ?? []));
    nextTick(() => nameInput.value?.focus());
  }
});

const FIELDS = [
  { value: "rating", label: "Rating", type: "number" },
  { value: "play_count", label: "Play count", type: "number" },
  { value: "genre", label: "Genre", type: "text" },
  { value: "year", label: "Year", type: "number" },
  { value: "artist", label: "Artist", type: "text" },
  { value: "album", label: "Album", type: "text" },
  { value: "title", label: "Title", type: "text" },
  { value: "last_played_at", label: "Last played (unix)", type: "number" },
  { value: "has_cover", label: "Has cover art", type: "bool" },
];

const NUMBER_OPS = [
  { value: "eq", label: "=" },
  { value: "neq", label: "≠" },
  { value: "gt", label: ">" },
  { value: "gte", label: "≥" },
  { value: "lt", label: "<" },
  { value: "lte", label: "≤" },
  { value: "is_null", label: "is not set" },
  { value: "is_not_null", label: "is set" },
];

const TEXT_OPS = [
  { value: "eq", label: "is" },
  { value: "neq", label: "is not" },
  { value: "contains", label: "contains" },
  { value: "is_null", label: "is empty" },
  { value: "is_not_null", label: "is not empty" },
];

const BOOL_OPS = [
  { value: "eq", label: "=" },
];

function opsForField(field: string) {
  const f = FIELDS.find((x) => x.value === field);
  if (!f) return TEXT_OPS;
  if (f.type === "number") return NUMBER_OPS;
  if (f.type === "bool") return BOOL_OPS;
  return TEXT_OPS;
}

function fieldType(field: string) {
  return FIELDS.find((x) => x.value === field)?.type ?? "text";
}

const valueHidden = (op: string) => op === "is_null" || op === "is_not_null";

function onFieldChange(rule: SmartRule): void {
  const ops = opsForField(rule.field);
  if (ops.length > 0) {
    // Only reset op if current op is not valid for new field
    const isValid = ops.some((o) => o.value === rule.op);
    if (!isValid) rule.op = ops[0].value;
  }
  rule.value = fieldType(rule.field) === "bool" ? "1" : "";
}

function addRule(): void {
  rules.push({ field: "rating", op: "gte", value: "4" });
}

function removeRule(i: number): void {
  rules.splice(i, 1);
}

const canConfirm = computed(() => localName.value.trim().length > 0 && rules.length > 0);

function confirm(): void {
  if (!canConfirm.value) return;
  emit("confirm", localName.value.trim(), localIcon.value.trim(), rules.map((r) => ({ ...r })));
}

// Extract unique genres from library tracks if not provided
const genres = computed(() => {
  if (props.genres && props.genres.length > 0) return props.genres;
  // Fallback: use an empty list, user can type freely
  return [];
});
</script>
