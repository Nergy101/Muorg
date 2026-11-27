<template>
  <!-- NER-326: listening profiles management. The section always offers "Add
       profile" — creating a second profile is what switches the whole feature
       on everywhere else. Until then this is the only place profiles are
       mentioned at all. -->
  <div>
    <p :class="SUBHEAD">Profiles</p>
    <p class="px-4 pb-2 text-body-sm text-on-surface-variant">
      Listening profiles give play history and listening stats their own scope.
      Nothing changes anywhere until you add a second profile; from then on the
      profile you are listening as is always shown, and stats can compare or
      combine profiles.
    </p>

    <!-- The profile list only exists once there is something to choose
         between. The default profile ("You", id 1) can be renamed but not
         deleted, and the last remaining profile can't be removed either. -->
    <template v-if="profilesStore.profiles.length > 1">
      <div v-for="p in profilesStore.profiles" :key="p.id" :class="ROW">
        <div class="flex min-w-0 flex-1 items-center gap-3">
          <MageIcon
            :name="p.id === profilesStore.activeProfileId ? 'sound-waves' : 'user'"
            class="h-5 w-5 shrink-0"
            :class="p.id === profilesStore.activeProfileId ? 'text-primary' : 'text-on-surface-variant'"
          />
          <div class="min-w-0 flex-1">
            <p class="truncate text-body-lg text-on-surface">{{ p.name }}</p>
            <p v-if="p.id === profilesStore.activeProfileId" class="text-body-sm text-primary">
              Listening as this profile
            </p>
          </div>
        </div>

        <!-- Rename: inline text field replacing the row's controls. -->
        <template v-if="renamingId === p.id">
          <input
            v-model="renameText"
            :aria-label="`New name for ${p.name}`"
            class="w-32 rounded-lg bg-surface-variant/60 px-2 py-1 text-body-md text-on-surface outline-none"
            maxlength="40"
            @keydown.enter.prevent="commitRename(p)"
            @keydown.escape.prevent="cancelRename"
          />
          <button
            type="button"
            class="rounded-full p-2 text-primary transition-colors hover:bg-surface-variant/60"
            aria-label="Save name"
            @click="commitRename(p)"
          >
            <MageIcon name="check" class="h-4 w-4" />
          </button>
          <button
            type="button"
            class="rounded-full p-2 text-on-surface-variant transition-colors hover:bg-surface-variant/60"
            aria-label="Cancel rename"
            @click="cancelRename"
          >
            <MageIcon name="multiply" class="h-4 w-4" />
          </button>
        </template>
        <template v-else>
          <button
            v-if="p.id !== profilesStore.activeProfileId"
            type="button"
            class="rounded-full px-3 py-1 text-label-lg text-primary transition-colors hover:bg-primary/10"
            @click="profilesStore.setActive(p.id)"
          >
            Listen as
          </button>
          <button
            type="button"
            class="rounded-full p-2 text-on-surface-variant transition-colors hover:bg-surface-variant/60"
            :aria-label="`Rename ${p.name}`"
            @click="startRename(p)"
          >
            <MageIcon name="edit" class="h-4 w-4" />
          </button>
          <button
            v-if="p.id !== 1 && profilesStore.profiles.length > 1"
            type="button"
            class="rounded-full p-2 text-error transition-colors hover:bg-error/10"
            :aria-label="`Delete ${p.name}`"
            @click="deleteTarget = p"
          >
            <MageIcon name="trash" class="h-4 w-4" />
          </button>
        </template>
      </div>
    </template>

    <!-- Add: a single affordance, always present. The server creates the
         profile and the store refresh makes the feature come alive. -->
    <div :class="ROW">
      <div class="min-w-0 flex-1">
        <p class="text-body-lg text-on-surface">Add profile</p>
        <p class="text-body-sm text-on-surface-variant">
          {{ profilesStore.profiles.length > 1 ? "Another listening profile" : "A second listening profile" }}
        </p>
      </div>
      <template v-if="adding">
        <input
          ref="newNameEl"
          v-model="newName"
          aria-label="New profile name"
          class="w-32 rounded-lg bg-surface-variant/60 px-2 py-1 text-body-md text-on-surface outline-none"
          maxlength="40"
          placeholder="Name"
          @keydown.enter.prevent="commitAdd"
          @keydown.escape.prevent="adding = false"
        />
        <button
          type="button"
          class="rounded-full p-2 text-primary transition-colors hover:bg-surface-variant/60"
          aria-label="Create profile"
          @click="commitAdd"
        >
          <MageIcon name="check" class="h-4 w-4" />
        </button>
        <button
          type="button"
          class="rounded-full p-2 text-on-surface-variant transition-colors hover:bg-surface-variant/60"
          aria-label="Cancel"
          @click="adding = false"
        >
          <MageIcon name="multiply" class="h-4 w-4" />
        </button>
      </template>
      <button
        v-else
        type="button"
        class="flex items-center gap-1 rounded-full px-3 py-1 text-label-lg text-primary transition-colors hover:bg-primary/10"
        @click="startAdd"
      >
        <MageIcon name="plus" class="h-4 w-4" />
        <span>New</span>
      </button>
    </div>

    <ConfirmDialog
      :open="deleteTarget !== null"
      title="Delete profile?"
      :message="
        deleteTarget
          ? `Plays recorded under “${deleteTarget.name}” stay in the database but stop showing in per-profile stats. This cannot be undone.`
          : ''
      "
      confirm-label="Delete"
      danger
      @confirm="confirmDelete"
      @cancel="deleteTarget = null"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * NER-326: the profiles section of Settings. Creation is the only always-on
 * affordance — the rest of the list (switch, rename, delete) appears with
 * the second profile and then the feature is on everywhere.
 */
import { nextTick, ref } from "vue";
import MageIcon from "../MageIcon.vue";
import ConfirmDialog from "../ConfirmDialog.vue";
import { useProfilesStore } from "../../stores/profiles";
import { showToast } from "../../composables/useToast";
import type { Profile } from "../../types";

const ROW = "flex min-h-14 items-center justify-between gap-2 px-4 py-2";
const SUBHEAD = "px-4 pb-1 pt-4 text-label-lg text-on-surface-variant";

const profilesStore = useProfilesStore();

const adding = ref(false);
const newName = ref("");
const newNameEl = ref<HTMLInputElement | null>(null);
const renamingId = ref<number | null>(null);
const renameText = ref("");
const deleteTarget = ref<Profile | null>(null);

function startAdd(): void {
  newName.value = "";
  adding.value = true;
  void nextTick(() => newNameEl.value?.focus());
}

async function commitAdd(): Promise<void> {
  const name = newName.value.trim();
  if (!name) return;
  try {
    await profilesStore.create(name);
    adding.value = false;
    showToast(`Profile “${name}” created — you are now listening as it`);
  } catch {
    showToast("Could not create the profile — check the server connection");
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
    showToast("Could not rename the profile");
  }
}

async function confirmDelete(): Promise<void> {
  const p = deleteTarget.value;
  deleteTarget.value = null;
  if (!p) return;
  try {
    await profilesStore.remove(p.id);
    showToast(`Profile “${p.name}” deleted`);
  } catch {
    showToast("Could not delete the profile");
  }
}
</script>
