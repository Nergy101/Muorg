import { describe, expect, it } from "vitest";
import { relativeLuminance, scrimToneFor } from "./useDominantColor";

/**
 * Art-dependent scrim tone: the bottom fade over an album card must flip
 * with the artwork underneath, not with the theme. White caption text beats
 * black once the background's WCAG relative luminance drops below ~0.179,
 * so that crossover is the threshold between a dark and a light scrim.
 */
describe("scrimToneFor", () => {
  it("picks a dark scrim (white text) over light artwork", () => {
    expect(scrimToneFor("255,255,255")).toBe("dark");
    expect(scrimToneFor("239,237,233")).toBe("dark"); // light theme surface
    expect(scrimToneFor("200,200,200")).toBe("dark");
  });

  it("picks a light scrim (black text) over dark artwork", () => {
    expect(scrimToneFor("0,0,0")).toBe("light");
    expect(scrimToneFor("28,25,23")).toBe("light"); // dark theme background
    expect(scrimToneFor("41,37,36")).toBe("light"); // dark theme surface
    expect(scrimToneFor("90,90,90")).toBe("light");
  });

  it("splits at the WCAG contrast crossover (~0.179 relative luminance)", () => {
    // #767676 is the classic mid-gray where black and white tie (lum ~0.184
    // is just above the crossover -> dark scrim; one step darker -> light).
    expect(scrimToneFor("118,118,118")).toBe("dark");
    expect(scrimToneFor("117,117,117")).toBe("light");
  });

  it("treats malformed input as dark artwork (light scrim)", () => {
    expect(scrimToneFor("not,a,color")).toBe("light");
  });
});

describe("relativeLuminance", () => {
  it("maps black to 0 and white to 1", () => {
    expect(relativeLuminance("0,0,0")).toBe(0);
    expect(relativeLuminance("255,255,255")).toBeCloseTo(1, 5);
  });
});
