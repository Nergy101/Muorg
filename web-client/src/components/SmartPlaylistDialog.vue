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
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
        @click.self="emit('cancel')"
      >
        <div
          class="glass-strong flex max-h-[90vh] w-full max-w-lg flex-col rounded-[28px] p-6 shadow-2xl"
          role="dialog"
          aria-modal="true"
        >
          <h2 class="text-title-lg text-on-surface">
            {{ isEditing ? "Edit smart playlist" : "New smart playlist" }}
          </h2>

          <input
            v-model="name"
            type="text"
            placeholder="Playlist name"
            class="mt-4 w-full rounded-xl bg-surface-variant px-3 py-2.5 text-body-lg text-on-surface outline-none placeholder:text-on-surface-variant"
            @keyup.enter="confirm"
          />

          <div class="mt-4 max-h-[24vh] grid grid-cols-8 gap-1 overflow-y-auto">
            <button
              v-for="emoji in PLAYLIST_EMOJIS"
              :key="emoji"
              type="button"
              class="flex h-9 w-9 items-center justify-center rounded-lg text-title-md leading-none"
              :class="emoji === icon ? 'bg-primary-container' : ''"
              @click="icon = emoji"
            >{{ emoji }}</button>
          </div>

          <div class="mt-4 max-h-[38vh] min-h-0 flex-1 space-y-2 overflow-y-auto pr-1">
            <div v-if="!rules.length" class="py-4 text-center text-body-sm text-on-surface-variant">
              Add a rule to define which tracks match.
            </div>
            <div
              v-for="(rule, i) in rules"
              :key="i"
              class="flex items-center gap-2"
            >
              <select
                v-model="rule.field"
                class="min-w-0 flex-[1.3] rounded-lg bg-surface-variant px-2 py-2 text-label-md text-on-surface outline-none"
                @change="onFieldChange(rule)"
              >
                <option v-for="f in FIELDS" :key="f.value" :value="f.value">{{ f.label }}</option>
              </select>

              <select
                v-model="rule.op"
                class="shrink-0 rounded-lg bg-surface-variant px-2 py-2 text-label-md text-on-surface outline-none"
              >
                <option v-for="op in opsForField(rule.field)" :key="op.value" :value="op.value">
                  {{ op.label }}
                </option>
              </select>

              <template v-if="!valueHidden(rule.op)">
                <select
                  v-if="fieldType(rule.field) === 'bool'"
                  v-model="rule.value"
                  class="w-20 shrink-0 rounded-lg bg-surface-variant px-2 py-2 text-label-md text-on-surface outline-none"
                >
                  <option value="1">True</option>
                  <option value="0">False</option>
                </select>
                <input
                  v-else-if="fieldType(rule.field) === 'number'"
                  v-model="rule.value"
                  type="number"
                  step="1"
                  class="w-20 shrink-0 rounded-lg bg-surface-variant px-2 py-2 text-label-md text-on-surface outline-none"
                />
                <select
                  v-else-if="rule.field === 'genre' && genres?.length"
                  v-model="rule.value"
                  class="min-w-0 flex-1 rounded-lg bg-surface-variant px-2 py-2 text-label-md text-on-surface outline-none"
                >
                  <option value="" disabled>Select genre…</option>
                  <option v-for="g in genres" :key="g" :value="g">{{ g }}</option>
                </select>
                <input
                  v-else
                  v-model="rule.value"
                  type="text"
                  placeholder="Value"
                  class="min-w-0 flex-1 rounded-lg bg-surface-variant px-2 py-2 text-label-md text-on-surface outline-none placeholder:text-on-surface-variant"
                />
              </template>
              <span v-else class="min-w-0 flex-1" />

              <button
                type="button"
                class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-on-surface-variant hover:text-error"
                :aria-label="`Remove rule ${i + 1}`"
                @click="removeRule(i)"
              >
                <MageIcon name="multiply" class="h-4 w-4" />
              </button>
            </div>
          </div>

          <button
            type="button"
            class="mt-3 flex items-center gap-1.5 self-start text-label-lg text-primary"
            @click="addRule"
          >
            <MageIcon name="plus" class="h-4 w-4" />
            Add rule
          </button>

          <div class="mt-4 flex justify-end gap-2">
            <button
              type="button"
              class="rounded-full px-4 py-2 text-label-lg text-on-surface-variant"
              @click="emit('cancel')"
            >Cancel</button>
            <button
              type="button"
              class="rounded-full px-4 py-2 text-label-lg text-primary disabled:opacity-50"
              :disabled="!canConfirm"
              @click="confirm"
            >{{ isEditing ? "Save" : "Create" }}</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import MageIcon from "./MageIcon.vue";
import { PLAYLIST_EMOJIS } from "../playlistEmojis";
import type { SmartRule } from "../types";

const props = defineProps<{
  open: boolean;
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

const BOOL_OPS = [{ value: "eq", label: "=" }];

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
  rule.op = opsForField(rule.field)[0].value;
  rule.value = fieldType(rule.field) === "bool" ? "1" : "";
}

const name = ref(props.initialName ?? "");
const icon = ref(props.initialIcon ?? "⚡");
const rules = reactive<SmartRule[]>([]);

watch(
  () => props.open,
  (isOpen) => {
    if (!isOpen) return;
    name.value = props.initialName ?? "";
    icon.value = props.initialIcon ?? "⚡";
    rules.splice(0, rules.length, ...(props.initialRules?.map((r) => ({ ...r })) ?? []));
  },
);

function addRule(): void {
  rules.push({ field: "rating", op: "gte", value: "4" });
}

function removeRule(i: number): void {
  rules.splice(i, 1);
}

const canConfirm = computed(() => name.value.trim() !== "" && rules.length > 0);

function confirm(): void {
  if (!canConfirm.value) return;
  emit("confirm", name.value.trim(), icon.value, rules.map((r) => ({ ...r })));
}
</script>
