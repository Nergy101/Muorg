import type { Directive } from "vue";

/**
 * v-marquee: on the `.marq` container, measure whether the `.marq-in` content
 * overflows the one-line box; if so add `.go` (starts the CSS scroll animation)
 * with the duration scaled to the text length and the box width as the travel
 * distance. Re-measures on update and on resize.
 */
function measure(el: HTMLElement) {
  const inner = el.querySelector<HTMLElement>(".marq-in");
  if (!inner) return;
  const overflow = inner.scrollWidth > el.clientWidth + 4;
  el.classList.toggle("go", overflow);
  if (overflow) {
    el.style.setProperty("--marq-box", `${el.clientWidth}px`);
    el.style.setProperty("--marq-dur", `${Math.max(4, inner.scrollWidth / 16)}s`);
  }
}

export const vMarquee: Directive<HTMLElement> = {
  mounted(el) {
    measure(el);
    const ro = new ResizeObserver(() => measure(el));
    ro.observe(el);
    (el as unknown as { _marqueeRO?: ResizeObserver })._marqueeRO = ro;
  },
  updated(el) {
    measure(el);
  },
  unmounted(el) {
    (el as unknown as { _marqueeRO?: ResizeObserver })._marqueeRO?.disconnect();
  },
};
