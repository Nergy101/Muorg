<script setup lang="ts">
import { computed } from "vue";
import type { PendingPlaylistAdd } from "../../composables/usePlaylistAdd";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const props = defineProps<{ pending: PendingPlaylistAdd }>();
const emit = defineEmits<{
  (e: "confirm-all"): void;
  (e: "confirm-deduped"): void;
  (e: "cancel"): void;
}>();

/** True when the user is adding exactly one track (so "de-duplicate" = "cancel"). */
const isSingleAdd = computed(() => props.pending.allIds.length === 1);
const dupeCount = computed(() => props.pending.dupeIds.length);
const newCount = computed(() => props.pending.newIds.length);
const allAreDupes = computed(() => newCount.value === 0);
</script>

<template>
  <Teleport to="body">
    <div
      class="fixed inset-0 z-[500] flex items-center justify-center bg-black/60"
      @mousedown.self="emit('cancel')"
    >
      <div
        class="w-[400px] rounded-xl border border-stone-600 bg-stone-800 p-5 shadow-2xl"
        @click.stop
      >
        <!-- Header -->
        <div class="mb-3 flex items-center gap-2">
          <FeatherIcon name="alert-circle" class="h-4 w-4 shrink-0 text-amber-400" />
          <h3 class="text-sm font-semibold text-stone-100">
            {{ isSingleAdd ? "Already in playlist" : "Duplicate tracks" }}
          </h3>
        </div>

        <!-- Body -->
        <p class="mb-5 text-sm leading-relaxed text-stone-300">
          <template v-if="isSingleAdd">
            This track is already in
            <span class="font-medium text-stone-100">"{{ pending.playlistName }}"</span>.
            Add it again?
          </template>
          <template v-else-if="allAreDupes">
            All {{ dupeCount }} tracks are already in
            <span class="font-medium text-stone-100">"{{ pending.playlistName }}"</span>.
            Add them again anyway?
          </template>
          <template v-else>
            <span class="font-medium text-stone-100">{{ dupeCount }} {{ dupeCount === 1 ? "track" : "tracks" }}</span>
            {{ dupeCount === 1 ? "is" : "are" }} already in
            <span class="font-medium text-stone-100">"{{ pending.playlistName }}"</span>.
            {{ newCount }} new {{ newCount === 1 ? "track" : "tracks" }} would be added.
          </template>
        </p>

        <!-- Actions -->
        <div class="flex items-center justify-end gap-2">
          <button
            type="button"
            class="rounded px-3 py-1.5 text-sm text-stone-400 hover:bg-stone-700 hover:text-stone-200"
            @click="emit('cancel')"
          >
            Cancel
          </button>

          <!-- "Add new only" — shown when some are new and some are dupes (multi-add) -->
          <button
            v-if="!isSingleAdd && !allAreDupes"
            type="button"
            class="rounded border border-stone-600 bg-stone-700/60 px-3 py-1.5 text-sm text-stone-200 hover:bg-stone-600"
            @click="emit('confirm-deduped')"
          >
            Add {{ newCount }} new only
          </button>

          <!-- "Add all" / "Add again" -->
          <button
            type="button"
            class="rounded bg-stone-600 px-3 py-1.5 text-sm text-stone-100 hover:bg-stone-500"
            @click="emit('confirm-all')"
          >
            {{ isSingleAdd ? "Add again" : allAreDupes ? "Add all anyway" : "Add all" }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
