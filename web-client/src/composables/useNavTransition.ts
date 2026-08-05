import { ref } from "vue";
import type { RouteLocationNormalized } from "vue-router";

/**
 * Which transition the next route change plays.
 *
 * A single `<Transition>` names one animation for both the entering and the
 * leaving view, so the *pair* has to be chosen up front from the direction of
 * travel. Without this, push and pop are indistinguishable and back-navigation
 * slides in from the same side as forward — the thing that makes a web app feel
 * unlike a native one.
 */
export type NavTransition =
  | "" // first paint: no animation to fade in from nothing
  | "modal-in"
  | "modal-out"
  | "nav-push"
  | "nav-pop"
  | "nav-fade";

export const navTransition = ref<NavTransition>("");

type Depth = number | "modal";

function depthOf(r: RouteLocationNormalized): Depth {
  const d = r.meta.depth;
  if (d === "modal") return "modal";
  return typeof d === "number" ? d : 0;
}

/**
 * Browser-history position of the entry the last committed navigation landed
 * on. vue-router assigns positions at entry creation (they grow with the
 * history length and are never reused), so a pop lands on an entry with a
 * strictly smaller position than the one it left.
 */
let lastPosition: number | null = null;

function currentPosition(): number | null {
  const s = window.history.state as { position?: unknown } | null;
  return typeof s?.position === "number" ? s.position : null;
}

function isQueue(r: RouteLocationNormalized): boolean {
  return r.name === "queue";
}

/**
 * Compares stack depth to classify the move. Modal wins over depth: entering or
 * leaving the full-screen player always reads as a sheet, whatever it came from.
 */
export function resolveNavTransition(
  to: RouteLocationNormalized,
  from: RouteLocationNormalized,
): NavTransition {
  // Depth alone cannot order the player sheet against the queue page ("modal"
  // vs a plain number), so that pair reads direction from the history position
  // to tell a push from a pop.
  const position = currentPosition();
  const isBack = lastPosition !== null && position !== null && position < lastPosition;
  lastPosition = position;

  // Cold boot / deep link: there is no outgoing view to animate against.
  if (from.matched.length === 0) return "";

  const toDepth = depthOf(to);
  const fromDepth = depthOf(from);

  // The queue is a pushed page that may sit on top of the player sheet, so the
  // pair animates as a normal stack push/pop instead of a sheet open/close:
  // pushing the queue slides it in from the right, popping back reveals the
  // player from the left rather than re-rising it from the bottom. Only a
  // forward queue -> player (tapping the mini player) still opens the sheet.
  if (toDepth === "modal" && isQueue(from)) return isBack ? "nav-pop" : "modal-in";
  if (fromDepth === "modal" && isQueue(to)) return isBack ? "nav-pop" : "nav-push";

  if (toDepth === "modal") return "modal-in";
  if (fromDepth === "modal") return "modal-out";

  if (toDepth > fromDepth) return "nav-push";
  if (toDepth < fromDepth) return "nav-pop";
  // Same level: tabs cross-fade, but a sideways drill (album -> album) is still
  // forward motion.
  return toDepth === 0 ? "nav-fade" : "nav-push";
}
