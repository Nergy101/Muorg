<template>
  <div>
    <div class="px-4 py-2">
      <p class="pb-2 text-body-lg text-on-surface">Counts as "missing metadata" when empty</p>
      <div class="flex flex-wrap gap-2">
        <button
          v-for="field in MISSING_METADATA_FIELDS"
          :key="field"
          type="button"
          class="rounded-full px-3 py-1.5 text-label-lg transition-colors"
          :class="isChecked(field) ? 'bg-primary/[0.15] text-primary' : 'bg-surface-variant text-on-surface-variant'"
          :aria-pressed="isChecked(field)"
          @click="toggleField(field)"
        >{{ FIELD_LABELS[field] }}</button>
      </div>
    </div>

    <button type="button" :class="[ROW, 'w-full text-left']" @click="router.push({ name: 'smart-transform' })">
      <div class="min-w-0 flex-1">
        <p class="text-body-lg text-on-surface">Smart Transform</p>
        <p class="text-body-sm text-on-surface-variant">Bulk-rename files by rewriting a path pattern</p>
      </div>
      <MageIcon name="chevron-right" class="h-5 w-5 shrink-0 text-on-surface-variant" />
    </button>

    <button type="button" :class="[ROW, 'w-full text-left']" @click="router.push({ name: 'stats' })">
      <div class="min-w-0 flex-1">
        <p class="text-body-lg text-on-surface">Library statistics</p>
        <p class="text-body-sm text-on-surface-variant">Genres, top artists, ratings and metadata health</p>
      </div>
      <MageIcon name="chevron-right" class="h-5 w-5 shrink-0 text-on-surface-variant" />
    </button>
  </div>
</template>

<script setup lang="ts">
/**
 * Reports settings block, mounted inside SettingsView's Library section:
 * which fields count as "missing metadata" everywhere in the app (Reports,
 * Stats, the Settings badge, the sidebar badge — all read this one list),
 * plus links to the two screens that are not top-level nav destinations.
 */
import { useRouter } from "vue-router";
import MageIcon from "../MageIcon.vue";
import { useSettingsStore } from "../../stores/settings";
import { MISSING_METADATA_FIELDS, type MissingMetadataField } from "@shared/reports";

const ROW = "flex min-h-14 items-center justify-between gap-4 px-4 py-2";

const FIELD_LABELS: Record<MissingMetadataField, string> = {
  title: "Title",
  artist: "Artist",
  album: "Album",
  album_artist: "Album artist",
  year: "Year",
  genre: "Genre",
  track_number: "Track #",
  disc_number: "Disc #",
  rating: "Rating",
  has_cover: "Album cover",
};

const router = useRouter();
const settings = useSettingsStore();

function isChecked(field: MissingMetadataField): boolean {
  return settings.missingMetadataFields.includes(field);
}

function toggleField(field: MissingMetadataField): void {
  const current = settings.missingMetadataFields;
  const next = isChecked(field)
    ? current.filter((f) => f !== field)
    : [...current, field];
  settings.setMissingMetadataFields(next);
}
</script>
