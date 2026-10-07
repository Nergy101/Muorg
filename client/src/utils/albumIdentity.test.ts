import { describe, expect, it } from "vitest";
import { albumIdentityKey } from "./albumIdentity";

describe("albumIdentityKey", () => {
  it("keeps same-titled albums by different artists separate", () => {
    expect(albumIdentityKey("Greatest Hits", "The Example Band"))
      .not.toBe(albumIdentityKey("Greatest Hits", "Another Artist"));
  });

  it("normalizes album and album artist consistently", () => {
    expect(albumIdentityKey("  Greatest Hits ", " THE EXAMPLE BAND "))
      .toBe(albumIdentityKey("greatest hits", "the example band"));
  });

  it("uses the same fallback for missing album names", () => {
    expect(albumIdentityKey(null, null)).toBe(albumIdentityKey(" Unknown Album ", ""));
  });
});
