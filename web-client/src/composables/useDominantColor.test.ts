import { describe, expect, it } from "vitest";
import { captionToneFor, relativeLuminance } from "./useDominantColor";

/**
 * Art-dependent caption tone: the caption over a card's frosted scrim must
 * flip with the artwork underneath, not with the theme. White text beats
 * black once the background's WCAG relative luminance drops below ~0.179,
 * so that crossover is the threshold between a light and a dark caption.
 */
describe("captionToneFor", () => {
  it("picks a dark caption (black text) over light artwork", () => {
    expect(captionToneFor("255,255,255")).toBe("dark");
    expect(captionToneFor("239,237,233")).toBe("dark"); // light theme surface
    expect(captionToneFor("200,200,200")).toBe("dark");
  });

  it("picks a light caption (white text) over dark artwork", () => {
    expect(captionToneFor("0,0,0")).toBe("light");
    expect(captionToneFor("28,25,23")).toBe("light"); // dark theme background
    expect(captionToneFor("41,37,36")).toBe("light"); // dark theme surface
    expect(captionToneFor("90,90,90")).toBe("light");
  });

  it("splits at the WCAG contrast crossover (~0.179 relative luminance)", () => {
    // #767676 is the classic mid-gray where black and white tie (lum ~0.184
    // is just above the crossover -> dark caption; one step darker -> light).
    expect(captionToneFor("118,118,118")).toBe("dark");
    expect(captionToneFor("117,117,117")).toBe("light");
  });

  it("treats malformed input as dark artwork (light caption)", () => {
    expect(captionToneFor("not,a,color")).toBe("light");
  });
});

describe("relativeLuminance", () => {
  it("maps black to 0 and white to 1", () => {
    expect(relativeLuminance("0,0,0")).toBe(0);
    expect(relativeLuminance("255,255,255")).toBeCloseTo(1, 5);
  });
});
