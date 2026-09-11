import { ref } from "vue";

export type TooltipPosition = "left" | "below" | "above";

export interface TooltipState {
  text: string;
  x: number;
  y: number;
  position: TooltipPosition;
}

/**
 * A hover tooltip rendered in a `<Teleport to="body">` rather than as a CSS
 * `::after`, because the sidebar and the table both clip overflow — an absolute
 * tooltip inside either gets cut off at the pane edge.
 *
 * The hide is delayed by 100ms and cancelled when the pointer enters the
 * tooltip itself, so a tooltip carrying text worth reading can be hovered
 * without it vanishing on the way there.
 *
 * Three components had their own copy of this (the metadata editor, the reports
 * sidebar and the reports modal), each with the same timeout dance.
 */
export function useTooltipPopover(hideDelayMs = 100) {
  const tooltip = ref<TooltipState | null>(null);
  let hideTimeout: ReturnType<typeof setTimeout> | null = null;

  function show(text: string, e: MouseEvent, position: TooltipPosition = "below") {
    if (hideTimeout) clearTimeout(hideTimeout);
    hideTimeout = null;
    const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
    if (position === "left") {
      tooltip.value = { text, x: rect.left - 8, y: rect.top + rect.height / 2, position };
    } else if (position === "above") {
      tooltip.value = { text, x: rect.left + rect.width / 2, y: rect.top - 6, position };
    } else {
      tooltip.value = { text, x: rect.left + rect.width / 2, y: rect.bottom + 6, position };
    }
  }

  /** Hide shortly, so the pointer can travel into the tooltip and cancel it. */
  function scheduleHide() {
    hideTimeout = setTimeout(() => {
      tooltip.value = null;
      hideTimeout = null;
    }, hideDelayMs);
  }

  function cancelHide() {
    if (hideTimeout) clearTimeout(hideTimeout);
    hideTimeout = null;
  }

  function hide() {
    tooltip.value = null;
    cancelHide();
  }

  /** The inline style that places the tooltip for its anchor side. */
  function styleFor(state: TooltipState): Record<string, string> {
    const base = { left: `${state.x}px`, top: `${state.y}px` };
    if (state.position === "left") return { ...base, transform: "translate(-100%, -50%)" };
    if (state.position === "above") return { ...base, transform: "translate(-50%, -100%)" };
    return { ...base, transform: "translateX(-50%)" };
  }

  return { tooltip, show, scheduleHide, cancelHide, hide, styleFor };
}
