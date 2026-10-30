import { computed, ref } from "vue";

/**
 * Which tracks the metadata editor is open on. This is the whole contract
 * between "something that wants to edit" (track sheet, selection bar, report
 * rows, context menu) and the editor itself, which is mounted once in App.vue.
 *
 * One id edits that track; several ids open the bulk editor, where only the
 * fields the user touches are written.
 */
const editorTrackIds = ref<number[]>([]);

export const isEditorOpen = computed(() => editorTrackIds.value.length > 0);
export const editorIds = computed(() => editorTrackIds.value);

export function openMetadataEditor(trackIds: readonly number[]): void {
  editorTrackIds.value = [...new Set(trackIds)];
}

export function closeMetadataEditor(): void {
  editorTrackIds.value = [];
}
