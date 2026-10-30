/**
 * HTML5 drag payload for "drag tracks onto a playlist". One shared format so
 * every draggable track surface (library table, track rows, album headers)
 * and every drop target (playlist cards, the desktop rail) agree.
 *
 * The ids travel in a custom MIME type, so dropping them on anything that does
 * not understand it (a text field, another app) does nothing.
 */
export const TRACK_DRAG_MIME = "application/x-muorg-tracks";

/** Call from a `dragstart` handler. */
export function startTrackDrag(e: DragEvent, ids: readonly number[]): void {
  if (!e.dataTransfer || ids.length === 0) return;
  e.dataTransfer.setData(TRACK_DRAG_MIME, JSON.stringify(ids));
  // Some browsers refuse to start a drag with only a custom type.
  e.dataTransfer.setData("text/plain", `${ids.length} track${ids.length === 1 ? "" : "s"}`);
  e.dataTransfer.effectAllowed = "copy";
}

/** True while the dragged payload is a track drag (readable in `dragover`, where data is not). */
export function isTrackDrag(e: DragEvent): boolean {
  return e.dataTransfer?.types.includes(TRACK_DRAG_MIME) ?? false;
}

/** Call from a `drop` handler. Returns the dragged track ids, or null if the drop is not a track drag. */
export function readTrackDrag(e: DragEvent): number[] | null {
  const raw = e.dataTransfer?.getData(TRACK_DRAG_MIME);
  if (!raw) return null;
  try {
    const parsed: unknown = JSON.parse(raw);
    if (Array.isArray(parsed) && parsed.every((n) => typeof n === "number")) return parsed;
  } catch {
    /* not ours */
  }
  return null;
}
