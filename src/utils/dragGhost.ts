/**
 * Creates and attaches a custom drag ghost to the DragEvent.
 * Shows a cover-art thumbnail, the item label (album/track name), and track count.
 * The ghost is appended off-screen so the browser can snapshot it, then removed on
 * the next animation frame.
 */
export function setTracksDragGhost(
  e: DragEvent,
  label: string,
  trackCount: number,
  coverDataUrl: string | null | undefined,
): void {
  if (!e.dataTransfer) return;

  const ghost = document.createElement("div");
  ghost.style.cssText =
    "position:fixed;top:-9999px;left:-9999px;" +
    "display:inline-flex;align-items:center;gap:9px;" +
    "padding:7px 14px 7px 7px;" +
    "background:rgba(28,25,23,0.97);" +
    "border:1px solid rgba(120,113,108,0.35);" +
    "border-radius:10px;" +
    "box-shadow:0 4px 20px rgba(0,0,0,0.55);" +
    "font-family:ui-sans-serif,system-ui,-apple-system,sans-serif;" +
    "white-space:nowrap;pointer-events:none;user-select:none;";

  // 34×34 cover thumbnail or ♪ placeholder
  const artEl = document.createElement("div");
  artEl.style.cssText =
    "width:34px;height:34px;border-radius:4px;overflow:hidden;flex-shrink:0;" +
    "background:rgba(68,64,60,.85);display:flex;align-items:center;justify-content:center;" +
    "font-size:17px;color:#a8a29e;";
  if (coverDataUrl) {
    const img = document.createElement("img");
    img.src = coverDataUrl;
    img.style.cssText = "width:100%;height:100%;object-fit:cover;display:block;";
    artEl.appendChild(img);
  } else {
    artEl.textContent = "♪";
  }

  // Text block: label + track count
  const textEl = document.createElement("div");
  textEl.style.cssText = "display:flex;flex-direction:column;gap:2px;min-width:0;";

  const labelEl = document.createElement("div");
  labelEl.style.cssText =
    "font-weight:600;font-size:13px;color:#e7e5e4;" +
    "max-width:220px;overflow:hidden;text-overflow:ellipsis;";
  labelEl.textContent = label;

  const countEl = document.createElement("div");
  countEl.style.cssText = "font-size:11px;color:#78716c;";
  countEl.textContent = `${trackCount} track${trackCount === 1 ? "" : "s"}`;

  textEl.appendChild(labelEl);
  textEl.appendChild(countEl);
  ghost.appendChild(artEl);
  ghost.appendChild(textEl);
  document.body.appendChild(ghost);

  // Force layout so offsetHeight is valid, then position cursor at left-center of ghost
  const h = ghost.offsetHeight || 48;
  e.dataTransfer.setDragImage(ghost, 8, Math.round(h / 2));

  // Remove on next frame after the browser has captured the snapshot
  requestAnimationFrame(() => { ghost.parentNode?.removeChild(ghost); });
}
