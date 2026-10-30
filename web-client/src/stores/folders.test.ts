import { describe, expect, it } from "vitest";
import { countTracksByRoot } from "./folders";

describe("countTracksByRoot", () => {
  it("counts tracks whose path is under a root", () => {
    const counts = countTracksByRoot(
      [{ path: "/music/rock/a.flac" }, { path: "/music/rock/b.flac" }, { path: "/music/jazz/c.flac" }],
      ["/music/rock", "/music/jazz"],
    );
    expect(counts.get("/music/rock")).toBe(2);
    expect(counts.get("/music/jazz")).toBe(1);
  });

  it("counts a track whose path equals the root itself", () => {
    const counts = countTracksByRoot([{ path: "/music/solo.flac" }], ["/music/solo.flac"]);
    expect(counts.get("/music/solo.flac")).toBe(1);
  });

  it("does not match a sibling directory with a similar name prefix", () => {
    const counts = countTracksByRoot([{ path: "/music2/a.flac" }], ["/music"]);
    expect(counts.get("/music")).toBe(0);
  });

  it("attributes a track to the longest (most specific) matching root, not both", () => {
    const counts = countTracksByRoot(
      [{ path: "/music/rock/metal/a.flac" }],
      ["/music/rock", "/music/rock/metal"],
    );
    expect(counts.get("/music/rock/metal")).toBe(1);
    expect(counts.get("/music/rock")).toBe(0);
  });

  it("returns a zero entry for every root even when nothing matches", () => {
    const counts = countTracksByRoot([], ["/a", "/b"]);
    expect(counts.get("/a")).toBe(0);
    expect(counts.get("/b")).toBe(0);
  });

  it("ignores a track that matches no configured root", () => {
    const counts = countTracksByRoot([{ path: "/elsewhere/a.flac" }], ["/music"]);
    expect(counts.get("/music")).toBe(0);
    expect(counts.size).toBe(1);
  });
});
