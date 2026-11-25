<script setup lang="ts">
import { computed, reactive, ref, watch } from "vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import PlaylistIconPicker from "./PlaylistIconPicker.vue";
import PlaylistIcon from "@shared/components/PlaylistIcon.vue";

export interface SmartRule {
  field: string;
  op: string;
  value: string;
}

const props = defineProps<{
  open: boolean;
  isEditing?: boolean;
  initialName?: string;
  initialIcon?: string;
  initialRules?: SmartRule[];
  genres?: string[];
}>();

const emit = defineEmits<{
  (e: "confirm", name: string, icon: string, rules: SmartRule[]): void;
  (e: "cancel"): void;
}>();

const name = ref(props.initialName ?? "");
const icon = ref(props.initialIcon ?? "");
const showIconPicker = ref(false);
const rules = reactive<SmartRule[]>(
  props.initialRules ? [...props.initialRules.map((r) => ({ ...r }))] : [],
);

// Reset state whenever the modal opens with new initial values
watch(() => props.open, (isOpen) => {
  if (isOpen) {
    name.value = props.initialName ?? "";
    icon.value = props.initialIcon ?? "";
    showIconPicker.value = false;
    rules.splice(0, rules.length, ...(props.initialRules?.map((r) => ({ ...r })) ?? []));
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

function addRule() {
  rules.push({ field: "rating", op: "gte", value: "4" });
}

function removeRule(i: number) {
  rules.splice(i, 1);
}

const canConfirm = computed(() => name.value.trim() && rules.length > 0);

function confirm() {
  if (!canConfirm.value) return;
  emit("confirm", name.value.trim(), icon.value.trim(), rules.map((r) => ({ ...r })));
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[400] flex items-center justify-center bg-black/60"
      @click.self="emit('cancel')"
    >
      <div class="w-full max-w-lg glass-strong rounded-2xl p-5 shadow-2xl">
        <h2 class="mb-4 text-sm font-semibold text-on-surface">
          {{ isEditing ? "Edit smart playlist" : "New smart playlist" }}
        </h2>

        <!-- Name + icon row -->
        <div class="relative mb-4 flex items-center gap-2">
          <button
            type="button"
            class="icon-btn h-8 w-8 shrink-0 rounded border border-outline bg-surface-variant text-base leading-none hover:bg-surface-variant"
            :class="showIconPicker ? 'ring-1 ring-outline' : ''"
            title="Pick icon"
            @mousedown.prevent
            @click.stop="showIconPicker = !showIconPicker"
          >
            <PlaylistIcon v-if="icon" :icon="icon" class="h-4 w-4" />
            <FeatherIcon v-else name="smile" class="h-3.5 w-3.5 text-on-surface-variant" />
          </button>
          <input
            v-model="name"
            type="text"
            placeholder="Playlist name…"
            maxlength="128"
            class="min-w-0 flex-1 rounded border border-outline bg-surface-variant px-3 py-1.5 text-sm text-on-surface outline-none focus:border-outline"
            @keydown.enter="confirm"
            @keydown.escape="emit('cancel')"
          />
          <PlaylistIconPicker
            :open="showIconPicker"
            :value="icon"
            class="absolute left-0 top-full z-50 mt-1"
            @pick="icon = $event; showIconPicker = false"
          />
        </div>

        <!-- Rules -->
        <div class="mb-3 space-y-2">
          <div v-if="!rules.length" class="py-2 text-center text-xs text-on-surface-variant">
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
              class="rounded border border-outline bg-surface-variant px-2 py-1 text-xs text-on-surface outline-none focus:border-outline"
              @change="rule.op = opsForField(rule.field)[0].value; rule.value = fieldType(rule.field) === 'bool' ? '1' : ''"
            >
              <option v-for="f in FIELDS" :key="f.value" :value="f.value">{{ f.label }}</option>
            </select>

            <!-- Operator -->
            <select
              v-model="rule.op"
              class="rounded border border-outline bg-surface-variant px-2 py-1 text-xs text-on-surface outline-none focus:border-outline"
            >
              <option v-for="op in opsForField(rule.field)" :key="op.value" :value="op.value">{{ op.label }}</option>
            </select>

            <!-- Value -->
            <template v-if="!valueHidden(rule.op)">
              <select
                v-if="fieldType(rule.field) === 'bool'"
                v-model="rule.value"
                class="w-24 rounded border border-outline bg-surface-variant px-2 py-1 text-xs text-on-surface outline-none focus:border-outline"
              >
                <option value="1">True</option>
                <option value="0">False</option>
              </select>
              <input
                v-else-if="fieldType(rule.field) === 'number'"
                v-model="rule.value"
                type="number"
                step="1"
                class="w-20 rounded border border-outline bg-surface-variant px-2 py-1 text-xs text-on-surface outline-none focus:border-outline"
              />
              <select
                v-else-if="rule.field === 'genre' && props.genres?.length"
                v-model="rule.value"
                class="min-w-0 flex-1 rounded border border-outline bg-surface-variant px-2 py-1 text-xs text-on-surface outline-none focus:border-outline"
              >
                <option value="" disabled>Select genre…</option>
                <option v-for="g in props.genres" :key="g" :value="g">{{ g }}</option>
              </select>
              <input
                v-else
                v-model="rule.value"
                type="text"
                class="min-w-0 flex-1 rounded border border-outline bg-surface-variant px-2 py-1 text-xs text-on-surface outline-none focus:border-outline"
              />
            </template>
            <span v-else class="min-w-0 flex-1" />

            <button
              type="button"
              class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded text-on-surface-variant hover:bg-surface-variant hover:text-red-400"
              @click="removeRule(i)"
            >
              <FeatherIcon name="x" class="h-3.5 w-3.5" />
            </button>
          </div>
        </div>

        <!-- Add rule -->
        <button
          type="button"
          class="mb-5 flex items-center gap-1.5 text-xs text-on-surface-variant hover:text-on-surface"
          @click="addRule"
        >
          <FeatherIcon name="plus" class="h-3.5 w-3.5" />
          Add rule
        </button>

        <!-- Actions -->
        <div class="flex justify-end gap-2">
          <button
            type="button"
            class="rounded px-3 py-1.5 text-xs text-on-surface-variant hover:bg-surface-variant hover:text-on-surface"
            @click="emit('cancel')"
          >
            Cancel
          </button>
          <button
            type="button"
            class="rounded bg-primary px-3 py-1.5 text-xs text-white hover:bg-[#6a9038] disabled:opacity-50"
            :disabled="!canConfirm"
            @click="confirm"
          >
            {{ isEditing ? "Save" : "Create" }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
