<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div class="content-col flex h-14 shrink-0 items-center gap-1 px-2">
      <button
        type="button"
        class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full text-on-surface transition-colors lg:w-auto lg:gap-1 lg:px-3 lg:text-label-lg lg:hover:bg-surface-container"
        aria-label="Back"
        @click="router.back()"
      >
        <MageIcon name="chevron-left" class="h-5 w-5" />
        <span class="hidden lg:inline">Back</span>
      </button>

      <div class="min-w-0 flex-1">
        <h1 class="truncate text-title-md text-on-surface">Smart Transform</h1>
        <p class="text-body-sm text-on-surface-variant">Bulk-rename files by rewriting a path pattern</p>
      </div>
    </div>

    <!-- Apply progress -->
    <div
      v-if="applyProgress"
      class="content-col flex shrink-0 items-center gap-3 px-4 py-2 text-body-sm text-on-surface-variant"
    >
      <MageIcon name="refresh" class="h-4 w-4 shrink-0 animate-spin" />
      <span class="min-w-0 flex-1 truncate">
        Moving {{ applyProgress.current }}/{{ applyProgress.total }} file{{ applyProgress.total === 1 ? "" : "s" }}…
      </span>
      <button type="button" class="shrink-0 text-label-lg text-error" @click="cancelApply">Cancel</button>
    </div>

    <div class="content-col min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
      <div class="px-4 py-3">
        <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
          <div>
            <label class="mb-1 block text-label-sm text-on-surface-variant">Origin pattern</label>
            <input
              v-model="origin"
              type="text"
              class="w-full rounded-xl bg-surface-variant px-3 py-2 font-mono text-body-sm text-on-surface outline-none placeholder:text-on-surface-variant/60"
              placeholder="<Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>"
            />
          </div>
          <div>
            <label class="mb-1 block text-label-sm text-on-surface-variant">Destination pattern</label>
            <input
              v-model="dest"
              type="text"
              class="w-full rounded-xl bg-surface-variant px-3 py-2 font-mono text-body-sm text-on-surface outline-none placeholder:text-on-surface-variant/60"
              placeholder="<Artist>/<Album> (Remastered)/<TrackNumber> - <TrackTitle>.<Format>"
            />
          </div>
        </div>
        <p class="mt-2 text-body-sm text-on-surface-variant">
          Tracks whose path matches the Origin pattern are listed below. The path prefix before the matched
          segments is preserved; only the matched segments are rewritten using the Destination pattern.
        </p>
      </div>

      <div v-if="!origin.trim() || !dest.trim()" class="flex flex-col items-center gap-2 px-6 py-12 text-center">
        <MageIcon name="exchange" class="h-12 w-12 text-on-surface-variant/40" />
        <span class="text-body-md text-on-surface-variant">
          Enter an Origin and Destination pattern to see matching tracks.
        </span>
      </div>
      <div v-else-if="matches.length === 0" class="flex flex-col items-center gap-2 px-6 py-12 text-center">
        <MageIcon name="check" class="h-12 w-12 text-on-surface-variant/40" />
        <span class="text-body-md text-on-surface-variant">No tracks match the Origin pattern.</span>
      </div>

      <template v-else>
        <div class="flex items-center gap-3 border-b border-surface-variant/60 px-4 py-2">
          <input
            type="checkbox"
            :checked="allSelected"
            :indeterminate="selectedIds.size > 0 && !allSelected"
            class="h-4 w-4 shrink-0"
            @change="toggleSelectAll"
          />
          <span class="text-body-sm text-on-surface-variant">
            {{ selectedIds.size }} of {{ matches.length }} selected
          </span>
        </div>

        <button
          v-for="m in matches"
          :key="m.track.id"
          type="button"
          class="flex w-full items-start gap-3 px-4 py-2 text-left transition-colors hover:bg-surface-container/50"
          :class="selectedIds.has(m.track.id) ? 'bg-surface-container/30' : ''"
          @click="toggleTrack(m.track.id)"
        >
          <input
            type="checkbox"
            :checked="selectedIds.has(m.track.id)"
            class="pointer-events-none mt-1 h-4 w-4 shrink-0"
          />
          <div class="min-w-0 flex-1">
            <p class="truncate font-mono text-body-sm text-on-surface-variant">{{ m.track.path }}</p>
            <div class="mt-0.5 flex items-center gap-1">
              <MageIcon name="chevron-right" class="h-3.5 w-3.5 shrink-0 text-on-surface-variant/60" />
              <p class="truncate font-mono text-body-sm text-on-surface">{{ m.newPath }}</p>
            </div>
          </div>
        </button>

        <!-- Sticks to the bottom of the scroll viewport rather than a fixed
             footer, so it never ends up hidden behind the floating mobile
             mini-player/nav island — the scroller's own bottom-inset padding
             keeps it clear of that overlay. -->
        <div class="sticky bottom-0 z-10 flex items-center justify-between gap-3 border-t border-surface-variant/60 bg-background/95 px-4 py-3 backdrop-blur">
          <p v-if="applyError" class="truncate text-body-sm text-error">{{ applyError }}</p>
          <span v-else class="text-body-sm text-on-surface-variant">
            {{ selectedIds.size }} of {{ matches.length }} selected
          </span>
          <button
            type="button"
            class="shrink-0 rounded-full bg-primary px-4 py-2 text-label-lg text-on-primary disabled:opacity-40"
            :disabled="selectedIds.size === 0 || !!applyProgress"
            @click="startApply"
          >
            Transform
          </button>
        </div>
      </template>
    </div>

    <ConfirmDialog
      :open="confirmOpen"
      title="Move files on disk?"
      :message="confirmMessage"
      confirm-label="Move"
      danger
      @confirm="confirmApply"
      @cancel="confirmOpen = false"
    />

    <ConfirmDialog
      :open="collisionOpen"
      title="Destinations already taken"
      :message="collisionMessage"
      confirm-label="Override"
      danger
      @confirm="confirmOverride"
      @cancel="collisionOpen = false"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import { useLibraryStore } from "../stores/library";
import { loadPref, savePref } from "../stores/prefs";
import { api } from "../api/client";
import { showToast, showWarning } from "../composables/useToast";
import { computeTransformMatches, planSelectedRenames } from "../smartTransform";
import type { RenameCollision } from "@shared/api";

const router = useRouter();
const lib = useLibraryStore();

// Last-used templates persist across visits — typing them out again every
// time would make this screen tedious to use more than once.
const origin = ref(loadPref("muorg-web-transform-origin", ""));
const dest = ref(loadPref("muorg-web-transform-dest", ""));
watch(origin, (v) => savePref("muorg-web-transform-origin", v));
watch(dest, (v) => savePref("muorg-web-transform-dest", v));

const matches = computed(() => computeTransformMatches(lib.tracks, origin.value, dest.value));

const selectedIds = ref<Set<number>>(new Set());
// Selection resets whenever the match set changes — a stale id surviving a
// pattern edit could silently select a track the user never saw listed.
watch(matches, () => {
  selectedIds.value = new Set();
});

const allSelected = computed(
  () => matches.value.length > 0 && matches.value.every((m) => selectedIds.value.has(m.track.id)),
);

function toggleSelectAll(): void {
  selectedIds.value = allSelected.value
    ? new Set()
    : new Set(matches.value.map((m) => m.track.id));
}

function toggleTrack(id: number): void {
  const next = new Set(selectedIds.value);
  if (next.has(id)) next.delete(id);
  else next.add(id);
  selectedIds.value = next;
}

// ── Apply ────────────────────────────────────────────────────────────────

const applyProgress = ref<{ current: number; total: number } | null>(null);
const applyCancelRequested = ref(false);
const applyError = ref<string | null>(null);
const confirmOpen = ref(false);
const collisionOpen = ref(false);
const pendingCollisions = ref<RenameCollision[]>([]);

const confirmMessage = computed(() => {
  const n = selectedIds.value.size;
  return `Move ${n} file${n === 1 ? "" : "s"} on disk to the new paths? This cannot be undone.`;
});

const collisionMessage = computed(() => {
  const list = pendingCollisions.value;
  const n = list.length;
  const names = list.slice(0, 3).map((c) => c.new_path);
  const more = n > names.length ? ` and ${n - names.length} more` : "";
  const reasons = names.join(", ") + more;
  return `Moving these files would overwrite ${n} existing destination${n === 1 ? "" : "s"}: ${reasons}. This cannot be undone.`;
});

function cancelApply(): void {
  applyCancelRequested.value = true;
}

/** Preflight: check for collisions before showing any confirmation. */
async function startApply(): Promise<void> {
  if (selectedIds.value.size === 0 || applyProgress.value) return;
  applyError.value = null;
  const plan = planSelectedRenames(matches.value, selectedIds.value);
  try {
    const collisions = await api.checkRenameCollisions(plan);
    if (collisions.length > 0) {
      pendingCollisions.value = collisions;
      collisionOpen.value = true;
      return;
    }
  } catch (e) {
    applyError.value = `Collision check failed: ${(e as Error).message}`;
    return;
  }
  confirmOpen.value = true;
}

function confirmApply(): void {
  confirmOpen.value = false;
  void runApply(false);
}

/** User explicitly accepted overwriting the colliding destinations. */
function confirmOverride(): void {
  collisionOpen.value = false;
  void runApply(true);
}

async function runApply(overwrite: boolean): Promise<void> {
  const plan = planSelectedRenames(matches.value, selectedIds.value);
  if (plan.length === 0) return;
  applyError.value = null;
  applyCancelRequested.value = false;
  const total = plan.length;
  applyProgress.value = { current: 0, total };

  let moved = 0;
  let failed = 0;
  let firstError: string | null = null;
  for (let i = 0; i < plan.length; i++) {
    if (applyCancelRequested.value) break;
    const { id, newPath } = plan[i];
    try {
      await api.renameTrackFile(id, newPath, overwrite);
      moved++;
    } catch (e) {
      failed++;
      firstError ??= (e as Error).message;
    }
    applyProgress.value = { current: i + 1, total };
  }
  applyProgress.value = null;
  await lib.loadLibrary();
  selectedIds.value = new Set();

  if (applyCancelRequested.value) {
    showToast(`Transform cancelled after moving ${moved} of ${total} file${total === 1 ? "" : "s"}`);
  } else if (failed > 0) {
    showWarning(`Moved ${moved} of ${total} file${total === 1 ? "" : "s"}; ${failed} failed: ${firstError}`);
  } else {
    showToast(`Moved ${moved} file${moved === 1 ? "" : "s"}`);
  }
}
</script>
