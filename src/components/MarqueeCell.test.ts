import { beforeEach, describe, expect, it, vi } from "vitest";
import { mount } from "@vue/test-utils";
import { nextTick } from "vue";
import MarqueeCell from "./MarqueeCell.vue";

/**
 * A table cell that scrolls its text only when the text does not fit.
 *
 * happy-dom reports every element as zero-sized, so `scrollWidth` and
 * `clientWidth` are stubbed per test — that pair is the entire input to the
 * decision this component makes.
 */
function withOverflow(scrollWidth: number, clientWidth: number) {
  vi.spyOn(HTMLElement.prototype, "scrollWidth", "get").mockReturnValue(scrollWidth);
  vi.spyOn(HTMLElement.prototype, "clientWidth", "get").mockReturnValue(clientWidth);
}

beforeEach(() => {
  vi.restoreAllMocks();
  // happy-dom has no ResizeObserver; the component observes its container.
  vi.stubGlobal(
    "ResizeObserver",
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  );
});

/** Mount and let the `nextTick(measure)` in onMounted run. */
async function mountSettled(text: string) {
  const wrapper = mount(MarqueeCell, { props: { text } });
  await nextTick();
  await nextTick();
  return wrapper;
}

describe("MarqueeCell", () => {
  it("shows the text plainly when it fits", async () => {
    withOverflow(100, 200);
    const wrapper = await mountSettled("Short title");

    expect(wrapper.text()).toBe("Short title");
    expect(wrapper.find(".marquee-cell-inner").exists()).toBe(false);
  });

  it("scrolls when the text overflows", async () => {
    withOverflow(400, 200);
    const wrapper = await mountSettled("A very long track title indeed");

    const inner = wrapper.find(".marquee-cell-inner");
    expect(inner.exists()).toBe(true);
    expect(wrapper.text()).toBe("A very long track title indeed");
  });

  it("scrolls by exactly the hidden width", async () => {
    withOverflow(400, 200);
    const wrapper = await mountSettled("Long");
    // The keyframes translate by --d; anything else over- or under-scrolls.
    expect(wrapper.find(".marquee-cell-inner").attributes("style")).toContain("--d: 200px");
  });

  it("ignores a few pixels of overflow rather than twitching", async () => {
    // Sub-pixel rounding routinely produces 1–4px of phantom overflow.
    withOverflow(203, 200);
    const wrapper = await mountSettled("Just about fits");
    expect(wrapper.find(".marquee-cell-inner").exists()).toBe(false);
  });

  it("re-measures when the text changes", async () => {
    withOverflow(400, 200);
    const wrapper = await mountSettled("Long title");
    expect(wrapper.find(".marquee-cell-inner").exists()).toBe(true);

    vi.restoreAllMocks();
    withOverflow(100, 200);
    await wrapper.setProps({ text: "Short" });
    await nextTick();
    await nextTick();

    expect(wrapper.find(".marquee-cell-inner").exists()).toBe(false);
    expect(wrapper.text()).toBe("Short");
  });

  it("disconnects its observer on unmount", async () => {
    const disconnect = vi.fn();
    vi.stubGlobal(
      "ResizeObserver",
      class {
        observe() {}
        unobserve() {}
        disconnect = disconnect;
      },
    );
    withOverflow(100, 200);

    const wrapper = await mountSettled("Anything");
    wrapper.unmount();

    expect(disconnect).toHaveBeenCalled();
  });

  it("renders an empty string without breaking", async () => {
    withOverflow(0, 0);
    const wrapper = await mountSettled("");
    expect(wrapper.text()).toBe("");
  });
});
