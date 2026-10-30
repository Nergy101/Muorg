import { ref } from "vue";

/**
 * App-wide hover/focus descriptions for controls.
 *
 * One delegated listener (installed once from App.vue) instead of a directive
 * on every button: any control that carries an accessible name — `data-tip`,
 * `aria-label` or `title` — gets a styled popover, so a new icon button is
 * described the moment it is given an `aria-label`, which it needs for screen
 * readers anyway.
 *
 * Rules:
 *  - `data-tip` / `title` are explicit descriptions and always show.
 *  - `aria-label` only shows when the control has no visible text of its own
 *    (an icon button): a labelled button whose label merely repeats its text
 *    needs no popover.
 *  - `title` is moved to `data-tip` on first hover so the browser's own
 *    delayed, unstyled tooltip does not appear on top of this one.
 *  - Mouse/pen hover and keyboard focus only; touch has no hover.
 */
export interface TooltipState {
  text: string;
  /** Bounding box of the control the tip describes (viewport coordinates). */
  rect: { left: number; top: number; width: number; height: number };
}

export const tooltip = ref<TooltipState | null>(null);

const SELECTOR = "button, a[href], [role='button'], [role='switch'], [role='slider'], [role='menuitem'], input[type='range'], summary";
const SHOW_DELAY_MS = 400;
/** Moving between controls within this window shows the next tip at once. */
const WARM_MS = 600;

let installed = false;
let showTimer: ReturnType<typeof setTimeout> | undefined;
let lastHiddenAt = 0;
let current: Element | null = null;

/**
 * Fuller descriptions (with the keyboard shortcut where there is one) for
 * icon buttons whose `aria-label` is deliberately terse for screen readers.
 * Keyed by the exact label; anything not listed shows its label as is.
 */
const TIP_DETAILS: Readonly<Record<string, string>> = {
  Back: "Go back",
  "Track actions": "Track actions — add to a playlist, edit metadata, info and more",
  "Change layout": "Change layout — cycle through album grid, album list and track list",
  "Shuffle all": "Shuffle and play the whole library",
  "Jump to album": "Open this track's album",
  Reorder: "Drag to reorder",
  "Repeat mode": "Repeat — cycle off → all → one (R)",
  Shuffle: "Toggle shuffle (S)",
  Queue: "Open the play queue",
  "Browse artist": "Show everything by this artist",
  "Sleep timer": "Sleep timer — stop playback automatically after a set time",
  "Report actions": "Report actions — play all, save as playlist and bulk fixes",
  "Save order": "Save the new track order",
  "Add cover": "Add cover art from a file",
  "Delete from server": "Delete this track and its file from the server (cannot be undone)",
  "Next track": "Next track (Ctrl+→)",
  "Previous track": "Previous track (Ctrl+←)",
  Pause: "Pause (Space)",
  Mute: "Mute (M)",
  Unmute: "Unmute (M)",
  "Clear selection": "Clear the selection (Esc)",
};
/** The description for a control, or null when it needs none. */
export function tipFor(el: Element): string | null {
  const html = el as HTMLElement;
  const title = el.getAttribute("title");
  if (title) {
    // Take the text over from the native tooltip.
    html.dataset.tip = title;
    el.removeAttribute("title");
  }
  const explicit = html.dataset.tip?.trim();
  if (explicit) return explicit;

  const label = el.getAttribute("aria-label")?.trim();
  if (!label) return null;
  const visible = (html.innerText ?? el.textContent ?? "").trim();
  return visible.length === 0 ? (TIP_DETAILS[label] ?? label) : null;
}

function hide(): void {
  clearTimeout(showTimer);
  showTimer = undefined;
  if (tooltip.value) lastHiddenAt = Date.now();
  tooltip.value = null;
  current = null;
}

function show(el: Element): void {
  const text = tipFor(el);
  if (!text) return;
  clearTimeout(showTimer);
  const present = (): void => {
    if (!el.isConnected || (el as HTMLButtonElement).disabled) return;
    const r = el.getBoundingClientRect();
    current = el;
    tooltip.value = { text, rect: { left: r.left, top: r.top, width: r.width, height: r.height } };
  };
  const warm = tooltip.value !== null || Date.now() - lastHiddenAt < WARM_MS;
  if (warm) present();
  else showTimer = setTimeout(present, SHOW_DELAY_MS);
}

function controlAt(target: EventTarget | null): Element | null {
  return target instanceof Element ? target.closest(SELECTOR) : null;
}

export function installTooltips(): void {
  if (installed) return;
  installed = true;

  document.addEventListener(
    "pointerover",
    (e) => {
      if (e.pointerType === "touch") return;
      const el = controlAt(e.target);
      if (!el) return;
      if (el === current) return;
      hide();
      show(el);
    },
    true,
  );

  document.addEventListener(
    "pointerout",
    (e) => {
      const el = controlAt(e.target);
      if (!el) return;
      const to = e.relatedTarget;
      if (to instanceof Node && el.contains(to)) return;
      hide();
    },
    true,
  );

  // Keyboard users get the same description when focus lands on a control.
  document.addEventListener(
    "focusin",
    (e) => {
      const el = controlAt(e.target);
      if (!el || !(el as HTMLElement).matches(":focus-visible")) return;
      hide();
      show(el);
    },
    true,
  );
  document.addEventListener("focusout", hide, true);

  // Anything that moves or consumes the pointer ends the tip.
  document.addEventListener("pointerdown", hide, true);
  document.addEventListener("scroll", hide, true);
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") hide();
  });
  window.addEventListener("hashchange", hide);
  window.addEventListener("blur", hide);
}
