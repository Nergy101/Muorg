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

  </div>
</template>

<script setup lang="ts">
/**
 * Reports settings block, mounted inside SettingsView's Library section:
 * which fields count as "missing metadata" everywhere in the app (Reports,
 * Stats, the Settings badge, the sidebar badge — all read this one list),
 */
import { useSettingsStore } from "../../stores/settings";
import { MISSING_METADATA_FIELDS, type MissingMetadataField } from "@shared/reports";

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
