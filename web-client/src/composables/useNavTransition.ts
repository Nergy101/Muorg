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
 * Compares stack depth to classify the move. Modal wins over depth: entering or
 * leaving the full-screen player always reads as a sheet, whatever it came from.
 */
export function resolveNavTransition(
  to: RouteLocationNormalized,
  from: RouteLocationNormalized,
): NavTransition {
  // Cold boot / deep link: there is no outgoing view to animate against.
  if (from.matched.length === 0) return "";

  const toDepth = depthOf(to);
  const fromDepth = depthOf(from);

  if (toDepth === "modal") return "modal-in";
  if (fromDepth === "modal") return "modal-out";

  if (toDepth > fromDepth) return "nav-push";
  if (toDepth < fromDepth) return "nav-pop";
  // Same level: tabs cross-fade, but a sideways drill (album -> album) is still
  // forward motion.
  return toDepth === 0 ? "nav-fade" : "nav-push";
}
