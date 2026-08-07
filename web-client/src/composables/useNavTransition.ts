import { ref } from "vue";
import type { RouteLocationNormalized } from "vue-router";

/**
 * Which transition the next route change plays.
 *
 * A single `<Transition>` names one animation for both the entering and the
 * leaving view, so the *pair* has to be chosen up front from the direction of
 * travel. Only forward motion is animated here — see `resolveNavTransition`.
 */
export type NavTransition =
  | "" // no animation: first paint, and every backward navigation
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

/**
 * Compares stack depth to classify a forward move. Modal wins over depth:
 * entering or leaving the full-screen player always reads as a sheet, whatever
 * it came from. Backward moves are never animated.
 */
export function resolveNavTransition(
  to: RouteLocationNormalized,
  from: RouteLocationNormalized,
): NavTransition {
  const position = currentPosition();
  const isBack = lastPosition !== null && position !== null && position < lastPosition;
  lastPosition = position;

  // Cold boot / deep link: there is no outgoing view to animate against.
  if (from.matched.length === 0) return "";

  // A pop animates nothing at all. The platform already runs its own page
  // transition for a back navigation — conspicuously so in an installed iOS
  // PWA — so anything of ours plays *on top of* that one and reads as a double
  // animation. And there is nothing to animate in regardless: the view being
  // returned to was already there and belongs exactly where it is.
  if (isBack) return "";

  const toDepth = depthOf(to);
  const fromDepth = depthOf(from);

  // Depth cannot order the player sheet against the queue pages ("modal" vs a
  // plain number), so that one pair is named explicitly: the queue is a page
  // pushed on top of the sheet, not another sheet.
  if (fromDepth === "modal" && (to.name === "queue" || to.name === "player-queue")) {
    return "nav-push";
  }

  if (toDepth === "modal") return "modal-in";
  if (fromDepth === "modal") return "modal-out";

  if (toDepth > fromDepth) return "nav-push";
  if (toDepth < fromDepth) return "nav-pop";
  // Same level: tabs cross-fade, but a sideways drill (album -> album) is still
  // forward motion.
  return toDepth === 0 ? "nav-fade" : "nav-push";
}
