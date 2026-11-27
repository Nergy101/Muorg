<template>
  <!-- NER-326: listening profiles management. Profiles belong to the current
       library context (local sidecar or the connected server). The section
       always offers "New" — creating a second profile is what switches the
       whole feature on everywhere else. -->
  <div>
    <p class="mb-2 text-sm text-on-surface-variant">
      Listening profiles give play history and listening stats their own scope.
      Nothing changes anywhere until a second profile exists; from then on the
      profile you are listening as is always shown (top bar, home, here).
    </p>

    <template v-if="profilesStore.profiles.length > 1">
      <div
        v-for="p in profilesStore.profiles"
        :key="p.id"
        class="flex min-h-12 items-center justify-between gap-2 rounded-lg px-2 py-1.5"
      >
        <div class="flex min-w-0 flex-1 items-center gap-2.5">
          <FeatherIcon
            :name="p.id === profilesStore.activeProfileId ? 'activity' : 'user'"
            class="h-4 w-4 shrink-0"
            :class="p.id === profilesStore.activeProfileId ? 'text-primary' : 'text-on-surface-variant'"
          />
          <div class="min-w-0 flex-1">
            <p class="truncate text-sm text-on-surface">{{ p.name }}</p>
            <p v-if="p.id === profilesStore.activeProfileId" class="text-xs text-primary">
              Listening as this profile
            </p>
          </div>
        </div>

        <template v-if="renamingId === p.id">
          <input
            v-model="renameText"
            :aria-label="`New name for ${p.name}`"
            class="w-32 rounded-lg bg-surface-variant/60 px-2 py-1 text-sm text-on-surface outline-none"
            maxlength="40"
            @keydown.enter.prevent="commitRename(p)"
            @keydown.escape.prevent="cancelRename"
          />
          <button type="button" class="rounded-full p-2 text-primary hover:bg-surface-variant/60" aria-label="Save name" @click="commitRename(p)">
            <FeatherIcon name="check" class="h-4 w-4" />
          </button>
          <button type="button" class="rounded-full p-2 text-on-surface-variant hover:bg-surface-variant/60" aria-label="Cancel rename" @click="cancelRename">
            <FeatherIcon name="x" class="h-4 w-4" />
          </button>
        </template>
        <template v-else>
          <button
            v-if="p.id !== profilesStore.activeProfileId"
            type="button"
            class="rounded-full px-3 py-1 text-sm font-medium text-primary hover:bg-primary/10"
            @click="profilesStore.setActive(p.id)"
          >
            Listen as
          </button>
          <button type="button" class="rounded-full p-2 text-on-surface-variant hover:bg-surface-variant/60" :aria-label="`Rename ${p.name}`" @click="startRename(p)">
            <FeatherIcon name="edit" class="h-4 w-4" />
          </button>
          <button
            v-if="p.id !== 1"
            type="button"
            class="rounded-full p-2 text-error hover:bg-error/10"
            :aria-label="`Delete ${p.name}`"
            @click="deleteTarget = p"
          >
            <FeatherIcon name="trash" class="h-4 w-4" />
          </button>
        </template>
      </div>
    </template>

    <div class="flex items-center justify-between gap-2 rounded-lg px-2 py-1.5">
      <div class="min-w-0 flex-1">
        <p class="text-sm text-on-surface">Add profile</p>
        <p class="text-xs text-on-surface-variant">
          {{ profilesStore.profiles.length > 1 ? "Another listening profile" : "A second listening profile" }}
        </p>
      </div>
      <template v-if="adding">
        <input
          ref="newNameEl"
          v-model="newName"
          aria-label="New profile name"
          class="w-32 rounded-lg bg-surface-variant/60 px-2 py-1 text-sm text-on-surface outline-none"
          maxlength="40"
          placeholder="Name"
          @keydown.enter.prevent="commitAdd"
          @keydown.escape.prevent="adding = false"
        />
        <button type="button" class="rounded-full p-2 text-primary hover:bg-surface-variant/60" aria-label="Create profile" @click="commitAdd">
          <FeatherIcon name="check" class="h-4 w-4" />
        </button>
        <button type="button" class="rounded-full p-2 text-on-surface-variant hover:bg-surface-variant/60" aria-label="Cancel" @click="adding = false">
          <FeatherIcon name="x" class="h-4 w-4" />
        </button>
      </template>
      <button
        v-else
        type="button"
        class="btn-outline flex !py-1.5 !text-xs items-center gap-1"
        @click="startAdd"
      >
        <FeatherIcon name="plus" class="h-3.5 w-3.5" />
        New
      </button>
    </div>

    <!-- Inline two-step confirm for delete (no modal in this shell). -->
    <div v-if="deleteTarget" class="mt-2 flex items-center justify-between gap-2 rounded-lg bg-error/10 px-3 py-2">
      <p class="text-xs text-on-surface">
        Delete “{{ deleteTarget.name }}”? Its plays stay in the database but leave the per-profile stats.
      </p>
      <div class="flex shrink-0 gap-1">
        <button type="button" class="rounded-full px-2.5 py-1 text-xs font-medium text-error hover:bg-error/10" @click="confirmDelete">
          Delete
        </button>
        <button type="button" class="rounded-full px-2.5 py-1 text-xs text-on-surface-variant hover:bg-surface-variant/60" @click="deleteTarget = null">
          Cancel
        </button>
      </div>
    </div>

    <p v-if="errorMsg" class="mt-2 text-xs text-error">{{ errorMsg }}</p>
  </div>
</template>

<script setup lang="ts">
/**
 * NER-326: the "Listening profiles" section of Settings. Creation is the only
 * always-on affordance — the rest of the list (switch, rename, delete) appears
 * with the second profile and then the feature is on everywhere.
 */
import { nextTick, ref } from "vue";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { useProfilesStore } from "../../../stores/profiles";
import type { Profile } from "@shared/api";

const profilesStore = useProfilesStore();

const adding = ref(false);
const newName = ref("");
const newNameEl = ref<HTMLInputElement | null>(null);
const renamingId = ref<number | null>(null);
const renameText = ref("");
const deleteTarget = ref<Profile | null>(null);
const errorMsg = ref("");

function startAdd(): void {
  newName.value = "";
  errorMsg.value = "";
  adding.value = true;
  void nextTick(() => newNameEl.value?.focus());
}

async function commitAdd(): Promise<void> {
  const name = newName.value.trim();
  if (!name) return;
  try {
    await profilesStore.create(name);
    adding.value = false;
  } catch {
    errorMsg.value = "Could not create the profile — check the server connection";
  }
}

function startRename(p: Profile): void {
  renamingId.value = p.id;
  renameText.value = p.name;
}

function cancelRename(): void {
  renamingId.value = null;
}

async function commitRename(p: Profile): Promise<void> {
  const name = renameText.value.trim();
  renamingId.value = null;
  if (!name || name === p.name) return;
  try {
    await profilesStore.rename(p.id, name);
  } catch {
    errorMsg.value = "Could not rename the profile";
  }
}

async function confirmDelete(): Promise<void> {
  const p = deleteTarget.value;
  deleteTarget.value = null;
  if (!p) return;
  try {
    await profilesStore.remove(p.id);
  } catch {
    errorMsg.value = "Could not delete the profile";
  }
}
</script>
