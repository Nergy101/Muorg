import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import EqualizerBars from "./EqualizerBars.vue";

/**
 * The now-playing indicator, used by both apps in track lists and queues.
 *
 * Its one non-obvious property is that pausing *freezes* the bars rather than
 * hiding them — a paused track still has to read as the current one.
 */
describe("EqualizerBars", () => {
  /** The animated bars, picked out by the inline animation the component sets. */
  const bars = (wrapper: ReturnType<typeof mount>) =>
    wrapper.findAll('[style*="animation"]');

  it("renders three bars", () => {
    expect(bars(mount(EqualizerBars))).toHaveLength(3);
  });

  it("staggers the bars so they do not move as one block", () => {
    const delays = bars(mount(EqualizerBars)).map((b) =>
      /(\d+)ms infinite/.exec(b.attributes("style") ?? "")?.[1],
    );
    expect(new Set(delays).size).toBeGreaterThan(1);
  });

  it("animates by default", () => {
    for (const bar of bars(mount(EqualizerBars))) {
      expect(bar.attributes("style")).toContain("animation-play-state: running");
    }
  });

  it("freezes rather than hides when paused", () => {
    const wrapper = mount(EqualizerBars, { props: { paused: true } });
    expect(bars(wrapper)).toHaveLength(3);
    for (const bar of bars(wrapper)) {
      expect(bar.attributes("style")).toContain("animation-play-state: paused");
    }
  });

  it("resumes when the prop flips back", async () => {
    const wrapper = mount(EqualizerBars, { props: { paused: true } });
    await wrapper.setProps({ paused: false });
    expect(bars(wrapper)[0].attributes("style")).toContain(
      "animation-play-state: running",
    );
  });

  it("passes a caller's class down to each bar for colouring", () => {
    // Both apps tint the bars with the accent colour this way.
    const wrapper = mount(EqualizerBars, { props: { class: "text-primary" } });
    for (const bar of bars(wrapper)) {
      expect(bar.classes()).toContain("text-primary");
    }
  });

  it("is hidden from assistive technology", () => {
    // Decorative: the row it sits in already announces the track.
    expect(mount(EqualizerBars).attributes("aria-hidden")).toBe("true");
  });
});
