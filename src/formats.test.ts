import { describe, expect, it } from "vitest";
import { isLossless, needsTranscode } from "./formats";

describe("needsTranscode", () => {
  it("streams MP3 as-is and transcodes FLAC and WAV", () => {
    expect(needsTranscode("mp3")).toBe(false);
    expect(needsTranscode("MP3")).toBe(false);
    expect(needsTranscode("flac")).toBe(true);
    expect(needsTranscode("wav")).toBe(true);
  });
});

describe("isLossless", () => {
  it("flags FLAC and WAV only", () => {
    expect(isLossless("flac")).toBe(true);
    expect(isLossless("wav")).toBe(true);
    expect(isLossless("mp3")).toBe(false);
    expect(isLossless(undefined)).toBe(false);
  });
});
