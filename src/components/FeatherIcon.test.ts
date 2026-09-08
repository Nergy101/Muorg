import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import FeatherIcon from "./FeatherIcon.vue";

/**
 * Every icon in the desktop app goes through this. It renders raw SVG with
 * `v-html`, so the two things worth checking are that a bad name degrades
 * quietly instead of throwing, and that the icon inherits colour rather than
 * being painted a fixed one.
 */
describe("FeatherIcon", () => {
  it("renders the named icon as inline SVG", () => {
    const wrapper = mount(FeatherIcon, { props: { name: "play" } });
    expect(wrapper.html()).toContain("<svg");
    expect(wrapper.html()).toContain("feather-play");
  });

  it("renders nothing for a name feather does not have", () => {
    // A typo'd name must not throw and take the surrounding view down.
    const wrapper = mount(FeatherIcon, { props: { name: "not-a-real-icon" } });
    expect(wrapper.html()).not.toContain("<svg");
    expect(wrapper.text()).toBe("");
  });

  it("strokes with currentColor so the icon inherits its context", () => {
    const wrapper = mount(FeatherIcon, { props: { name: "play" } });
    expect(wrapper.html()).toContain('stroke="currentColor"');
  });

  it("merges a caller's class onto the svg", () => {
    const wrapper = mount(FeatherIcon, { props: { name: "play", class: "h-4 w-4" } });
    const svgClass = /class="([^"]*)"/.exec(wrapper.find("svg").html())?.[1] ?? "";
    expect(svgClass).toContain("feather-play");
    expect(svgClass).toContain("h-4");
    expect(svgClass).toContain("w-4");
  });

  it("leaves no trailing space in the class when none is given", () => {
    const wrapper = mount(FeatherIcon, { props: { name: "play" } });
    const svgClass = /class="([^"]*)"/.exec(wrapper.find("svg").html())?.[1] ?? "";
    expect(svgClass).toBe(svgClass.trim());
  });

  it("swaps the icon when the name changes", async () => {
    const wrapper = mount(FeatherIcon, { props: { name: "play" } });
    await wrapper.setProps({ name: "pause" });
    expect(wrapper.html()).toContain("feather-pause");
    expect(wrapper.html()).not.toContain("feather-play");
  });

  it("is hidden from assistive technology", () => {
    // Icons here are always paired with a label or an aria-label on the button.
    const wrapper = mount(FeatherIcon, { props: { name: "play" } });
    expect(wrapper.attributes("aria-hidden")).toBe("true");
    expect(wrapper.attributes("role")).toBe("img");
  });
});
