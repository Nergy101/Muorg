<script setup lang="ts">
import { computed } from "vue";
import type { CatalogTrack } from "../../types";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import TooltipPopover from "../shared/TooltipPopover.vue";
import { useTooltipPopover } from "../../composables/useTooltipPopover";

const props = defineProps<{
  open: boolean;
  title: string;
  tracks: CatalogTrack[];
  duplicateCount: number | null;
  canSavePlaylist?: boolean;
  canApplyFromPath?: boolean;
  applyFromPathTooltip?: string;
  canAutoTag?: boolean;
}>();

const emit = defineEmits<{
  (e: "close"): void;
  (e: "selectTrack", track: CatalogTrack): void;
  (e: "saveAsPlaylist"): void;
  (e: "applyAllFromPath"): void;
  (e: "autoTagAll"): void;
}>();

const hasTracks = computed(() => props.tracks.length > 0);

const {
  tooltip: tooltipPopover,
  show: showTooltip,
  scheduleHide: scheduleHideTooltip,
  cancelHide: cancelHideTooltip,
  hide: hideTooltip,
  styleFor: tooltipStyle,
} = useTooltipPopover();

function close() {
  hideTooltip();
  emit("close");
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === "Escape") close();
}
</script>

<template>
  <Teleport to="body">
    <div
      v-if="open"
      class="fixed inset-0 z-[250] flex items-center justify-center bg-background/70 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="reports-modal-title"
      @keydown="onKeydown"
      @click.self="close"
    >
      <div class="max-h-[80vh] w-full max-w-3xl overflow-hidden glass-strong rounded-2xl shadow-xl" @click.stop>
        <div class="flex items-center justify-between border-b border-outline px-4 py-3">
          <div class="flex items-center gap-2">
            <h2 id="reports-modal-title" class="text-sm font-semibold text-on-surface">
              {{ title }}
            </h2>
            <span v-if="duplicateCount != null" class="rounded-full bg-red-500/20 px-2 py-0.5 text-[0.7rem] font-medium text-red-300">
              {{ duplicateCount }} extra duplicate{{ duplicateCount === 1 ? "" : "s" }}
            </span>
          </div>
          <div class="flex items-center gap-2">
            <button
              v-if="canSavePlaylist"
              type="button"
              class="inline-flex items-center gap-1.5 rounded border border-outline px-2 py-1 text-xs text-on-surface-variant hover:bg-surface-variant hover:text-on-surface"
              @click="emit('saveAsPlaylist')"
            >
              <FeatherIcon name="list" class="h-3.5 w-3.5 shrink-0" />
              Save as playlist for audit
            </button>
            <button
              v-if="canApplyFromPath"
              type="button"
              class="inline-flex items-center gap-1.5 rounded border border-outline px-2 py-1 text-xs text-on-surface-variant hover:bg-surface-variant hover:text-on-surface"
              @click="emit('applyAllFromPath')"
              @mouseenter="applyFromPathTooltip && showTooltip(applyFromPathTooltip, $event)"
              @mouseleave="scheduleHideTooltip"
            >
              <FeatherIcon name="zap" class="h-3.5 w-3.5 shrink-0" />
              Apply from path
            </button>
            <button
              v-if="canAutoTag"
              type="button"
              class="inline-flex items-center gap-1.5 rounded border border-outline px-2 py-1 text-xs text-on-surface-variant hover:bg-surface-variant hover:text-on-surface"
              @click="emit('autoTagAll')"
            >
              <FeatherIcon name="search" class="h-3.5 w-3.5 shrink-0" />
              Auto-tag all
            </button>
            <button
              type="button"
              class="rounded p-1.5 text-on-surface-variant hover:bg-surface-variant hover:text-on-surface"
              aria-label="Close"
              @click="close"
            >
              <FeatherIcon name="x" class="h-4 w-4" />
            </button>
          </div>
        </div>

        <div class="max-h-[calc(80vh-3rem)] overflow-y-auto">
          <table class="w-full border-collapse text-left text-sm">
            <thead class="bg-surface">
              <tr class="border-b border-outline text-xs font-semibold uppercase tracking-wide text-on-surface-variant">
                <th class="px-3 py-2">Title</th>
                <th class="px-3 py-2">Artist</th>
                <th class="px-3 py-2">Album</th>
                <th class="px-3 py-2">Year</th>
                <th class="px-3 py-2">Path</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="!hasTracks">
                <td colspan="5" class="px-3 py-4 text-center text-xs text-on-surface-variant">
                  No tracks match this report.
                </td>
              </tr>
              <tr
                v-for="track in tracks"
                v-else
                :key="track.id"
                class="border-b border-outline/60 hover:bg-surface-container/70 cursor-pointer"
                @click="emit('selectTrack', track)"
              >
                <td class="px-3 py-1.5 text-on-surface">{{ track.title ?? "—" }}</td>
                <td class="px-3 py-1.5 text-on-surface">{{ track.artist ?? "—" }}</td>
                <td class="px-3 py-1.5 text-on-surface">{{ track.album ?? "—" }}</td>
                <td class="px-3 py-1.5 text-on-surface">{{ track.year ?? "—" }}</td>
                <td class="px-3 py-1.5 text-[11px] text-on-surface-variant">
                  <span class="line-clamp-2 break-all">{{ track.path }}</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </Teleport>

  <TooltipPopover
    :state="tooltipPopover"
    :style-for="tooltipStyle"
    :z-index="300"
    @enter="cancelHideTooltip"
    @leave="hideTooltip"
  />
</template>
