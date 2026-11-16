import { describe, expect, it } from "vitest";
import {
  DEFAULT_PLAYLIST_ICON,
  DEFAULT_PLAYLIST_ICON_COLOR,
  DEFAULT_PLAYLIST_ICON_NAME,
  formatPlaylistIcon,
  lucideComponentName,
  resolvePlaylistIcon,
} from "./playlistIcons";

describe("resolvePlaylistIcon", () => {
  it("parses a well-formed name:#RRGGBB value", () => {
    expect(resolvePlaylistIcon("guitar:#F87171")).toEqual({
      name: "guitar",
      color: "#F87171",
    });
  });

  it("falls back to eighth-notes in white for legacy emoji", () => {
    expect(resolvePlaylistIcon("🎵")).toEqual({
      name: DEFAULT_PLAYLIST_ICON_NAME,
      color: DEFAULT_PLAYLIST_ICON_COLOR,
    });
  });

  it("falls back for null, empty and malformed values", () => {
    for (const bad of [null, undefined, "", "guitar:F87171", "guitar:#FFF", ":#FFFFFF", "Guitar:#FFFFFF"]) {
      expect(resolvePlaylistIcon(bad)).toEqual({
        name: "eighth-notes",
        color: "#FFFFFF",
      });
    }
  });

  it("default constant matches the fallback", () => {
    expect(DEFAULT_PLAYLIST_ICON).toBe("eighth-notes:#FFFFFF");
    expect(resolvePlaylistIcon(null)).toEqual(resolvePlaylistIcon(DEFAULT_PLAYLIST_ICON));
  });
});

describe("formatPlaylistIcon", () => {
  it("round-trips through resolvePlaylistIcon", () => {
    const value = formatPlaylistIcon("drum", "#22D3EE");
    expect(resolvePlaylistIcon(value)).toEqual({ name: "drum", color: "#22D3EE" });
  });
});

describe("lucideComponentName", () => {
  it("converts kebab-case to the PascalCase keys of the @lucide/vue icons map", () => {
    expect(lucideComponentName("eighth-notes")).toBe("EighthNotes");
    expect(lucideComponentName("music")).toBe("Music");
    expect(lucideComponentName("music-2")).toBe("Music2");
    expect(lucideComponentName("a-arrow-down")).toBe("AArrowDown");
  });
});
