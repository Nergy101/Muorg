<template>
  <Teleport to="body">
    <Transition
      enter-active-class="sheet-motion"
      enter-from-class="opacity-0"
      leave-active-class="sheet-motion"
      leave-to-class="opacity-0"
    >
      <div
        v-if="pending"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-6"
        @click.self="playlistStore.cancelPendingAdd()"
      >
        <div
          class="glass-strong w-full max-w-sm rounded-[28px] p-6 shadow-2xl"
          role="dialog"
          aria-modal="true"
        >
          <h2 class="text-title-lg text-on-surface">
            {{ isSingleAdd ? "Already in playlist" : "Duplicate tracks" }}
          </h2>

          <p class="mt-3 text-body-md text-on-surface-variant">
            <template v-if="isSingleAdd">
              This track is already in
              <span class="font-medium text-on-surface">"{{ pending.playlistName }}"</span>.
              Add it again?
            </template>
            <template v-else-if="allAreDupes">
              All {{ dupeCount }} tracks are already in
              <span class="font-medium text-on-surface">"{{ pending.playlistName }}"</span>.
              Add them again anyway?
            </template>
            <template v-else>
              <span class="font-medium text-on-surface">{{ dupeCount }} {{ dupeCount === 1 ? "track" : "tracks" }}</span>
              {{ dupeCount === 1 ? "is" : "are" }} already in
              <span class="font-medium text-on-surface">"{{ pending.playlistName }}"</span>.
              {{ newCount }} new {{ newCount === 1 ? "track" : "tracks" }} would be added.
            </template>
          </p>

          <div class="mt-6 flex flex-wrap justify-end gap-2">
            <button
              type="button"
              class="rounded-full px-4 py-2 text-label-lg text-on-surface-variant"
              @click="playlistStore.cancelPendingAdd()"
            >Cancel</button>

            <!-- "Add new only" — shown when some are new and some are dupes (multi-add). -->
            <button
              v-if="!isSingleAdd && !allAreDupes"
              type="button"
              class="glass-field rounded-full px-4 py-2 text-label-lg text-on-surface"
              @click="playlistStore.confirmAddDeduped()"
            >Add {{ newCount }} new only</button>

            <button
              type="button"
              class="rounded-full px-4 py-2 text-label-lg text-primary"
              @click="playlistStore.confirmAddAll()"
            >{{ isSingleAdd ? "Add again" : allAreDupes ? "Add all anyway" : "Add all" }}</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { usePlaylistStore } from "../stores/playlists";

/**
 * Mounted once, globally (App.vue) — driven entirely by the playlists
 * store's `pendingAdd` so every "add to playlist" surface (PlaylistPickerSheet,
 * PlaylistCard/SidebarNav drop targets) shares one dialog instance. Mirrors
 * the desktop client's PlaylistDuplicateDialog / usePlaylistAdd.
 */
const playlistStore = usePlaylistStore();

const pending = computed(() => playlistStore.pendingAdd);
const isSingleAdd = computed(() => pending.value?.allIds.length === 1);
const dupeCount = computed(() => pending.value?.dupeIds.length ?? 0);
const newCount = computed(() => pending.value?.newIds.length ?? 0);
const allAreDupes = computed(() => newCount.value === 0);
</script>
