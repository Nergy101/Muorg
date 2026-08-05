<template>
  <BottomSheet :open="open" @close="emit('close')">
    <div class="px-6 pb-2 text-title-md text-on-surface">Add to playlist</div>

    <p
      v-if="playlists.length === 0"
      class="py-6 text-center text-body-md text-on-surface-variant"
    >
      No playlists yet
    </p>

    <button
      v-for="p in playlists"
      :key="p.id"
      type="button"
      class="flex h-14 w-full items-center gap-3 px-6 text-left"
      @click="toggle(p)"
    >
      <span class="text-title-lg leading-none">{{ p.icon ?? "🎵" }}</span>
      <span class="min-w-0 flex-1 truncate text-body-lg text-on-surface">{{ p.name }}</span>
      <MageIcon
        v-if="stateFor(p.id) === 'full'"
        name="check-circle"
        class="h-5 w-5 shrink-0 text-primary"
      />
      <MageIcon
        v-else-if="stateFor(p.id) === 'partial'"
        name="check-circle"
        class="h-5 w-5 shrink-0 text-secondary opacity-70"
      />
      <span v-else class="h-5 w-5 shrink-0 rounded-full border-2 border-on-surface-variant/40" />
    </button>

    <div class="my-1 border-t border-outline/30" />

    <button
      v-if="!creating"
      type="button"
      class="flex h-14 w-full items-center gap-3 px-6 text-left text-primary"
      @click="startCreating"
    >
      <MageIcon name="plus" class="h-5 w-5" />
      <span class="text-body-lg">New playlist</span>
    </button>

    <div v-else class="px-6 pb-4 pt-2">
      <input
        ref="nameInput"
        v-model="newName"
        type="text"
        placeholder="Playlist name"
        class="w-full rounded-xl bg-surface-variant px-3 py-2.5 text-body-lg text-on-surface outline-none placeholder:text-on-surface-variant"
        @keyup.enter="submitCreate"
      />
      <div class="mt-2 flex justify-end gap-2">
        <button
          type="button"
          class="rounded-full px-4 py-2 text-label-lg text-on-surface-variant"
          @click="creating = false"
        >Cancel</button>
        <button
          type="button"
          class="rounded-full px-4 py-2 text-label-lg text-primary disabled:opacity-50"
          :disabled="newName.trim().length === 0"
          @click="submitCreate"
        >Create</button>
      </div>
    </div>

    <div class="h-2" />
  </BottomSheet>
</template>

<script setup lang="ts">
import { nextTick, ref, watch } from "vue";
import MageIcon from "./MageIcon.vue";
import BottomSheet from "./BottomSheet.vue";
import type { Playlist } from "../types";

type MemberState = "full" | "partial" | "none";

const props = defineProps<{
  open: boolean;
  playlists: Playlist[];
  membershipIds: Set<number>;
  partialMembershipIds?: Set<number>;
}>();

const emit = defineEmits<{
  add: [Playlist];
  remove: [Playlist];
  create: [name: string];
  close: [];
}>();

// Optimistic overlay so a tap responds before the network round-trip lands.
const overrides = ref<Map<number, MemberState>>(new Map());
const creating = ref(false);
const newName = ref("");
const nameInput = ref<HTMLInputElement | null>(null);

watch(
  () => props.open,
  (isOpen) => {
    if (!isOpen) return;
    overrides.value = new Map();
    creating.value = false;
    newName.value = "";
  },
);

function stateFor(id: number): MemberState {
  const override = overrides.value.get(id);
  if (override) return override;
  if (props.membershipIds.has(id)) return "full";
  if (props.partialMembershipIds?.has(id)) return "partial";
  return "none";
}

function toggle(p: Playlist): void {
  const current = stateFor(p.id);
  // Partial means "some tracks are in" — completing the add is the useful move.
  const next: MemberState = current === "full" ? "none" : "full";
  overrides.value = new Map(overrides.value).set(p.id, next);
  if (current === "full") emit("remove", p);
  else emit("add", p);
}

async function startCreating(): Promise<void> {
  creating.value = true;
  await nextTick();
  nameInput.value?.focus();
}

function submitCreate(): void {
  const name = newName.value.trim();
  if (!name) return;
  emit("create", name);
  creating.value = false;
  newName.value = "";
}
</script>
