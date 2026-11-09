import { describe, expect, it } from "vitest";
import { mount } from "@vue/test-utils";
import FeatherIcon from "./FeatherIcon.vue";

/**
 * Every icon in the desktop app goes through this. It renders raw SVG with
 * `v-html`, so the two things worth checking are that a bad name degrades
 * quietly instead of throwing, and that the icon inherits colour rather than
 * being painted a fixed one. Feather names are mapped onto the vendored Mage
 * set (see the MAGE_NAMES table in the component).
 */
describe("FeatherIcon", () => {
  it("renders the named icon as inline SVG via its Mage mapping", () => {
    const wrapper = mount(FeatherIcon, { props: { name: "play" } });
    expect(wrapper.html()).toContain("<svg");
  });

  it("maps a Feather name to a Mage icon", () => {
    // "cast" has no Mage equivalent under the same name; it must resolve to
    // the mapped screencast glyph instead of rendering nothing.
    const wrapper = mount(FeatherIcon, { props: { name: "cast" } });
    expect(wrapper.html()).toContain("<svg");
  });

  it("renders nothing for a name neither Feather nor Mage has", () => {
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
    expect(svgClass).toContain("h-4");
    expect(svgClass).toContain("w-4");
  });

  it("swaps the icon when the name changes", async () => {
    const wrapper = mount(FeatherIcon, { props: { name: "play" } });
    const before = wrapper.find("svg").html();
    await wrapper.setProps({ name: "pause" });
    expect(wrapper.find("svg").html()).not.toBe(before);
  });

  it("is hidden from assistive technology", () => {
    // Icons here are always paired with a label or an aria-label on the button.
    const wrapper = mount(FeatherIcon, { props: { name: "play" } });
    expect(wrapper.find("span").attributes("aria-hidden")).toBe("true");
    expect(wrapper.find("span").attributes("role")).toBe("img");
  });
});
