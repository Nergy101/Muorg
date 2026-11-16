import { describe, expect, it } from "vitest";
import {
  buildDiscography,
  classifyRelease,
  creditsArtist,
  isLinkableArtist,
  splitCredits,
} from "./discography";
import { groupAlbums, albumKeyFor } from "./stores/library";
import type { CatalogTrack } from "./types";

let nextId = 1;
function t(
  title: string,
  artist: string | null,
  album: string | null,
  opts: Partial<CatalogTrack> = {},
): CatalogTrack {
  return {
    id: nextId++,
    title,
    artist,
    album,
    album_artist: null,
    duration_secs: 240,
    year: null,
    has_cover: false,
    ...opts,
  } as CatalogTrack;
}

function discographyOf(name: string, tracks: CatalogTrack[]) {
  const albums = groupAlbums(tracks);
  const byTrack = new Map<number, string>();
  for (const [key, item] of albums) for (const id of item.trackIds) byTrack.set(id, key);
  const dur = new Map(tracks.map((x) => [x.id, x.duration_secs ?? 0]));
  return buildDiscography(
    name,
    tracks,
    (x) => byTrack.get(x.id) ?? albumKeyFor(x),
    (key) => albums.get(key) ?? null,
    (id) => dur.get(id) ?? 0,
  );
}

const titles = (items: { album: string }[]) => items.map((i) => i.album);

describe("classifyRelease", () => {
  it("follows the 7+ / 4–6 / 1–3 track rule", () => {
    expect(classifyRelease("A", Array(7).fill(200))).toBe("album");
    expect(classifyRelease("A", Array(4).fill(200))).toBe("ep");
    expect(classifyRelease("A", Array(6).fill(200))).toBe("ep");
    expect(classifyRelease("A", [200, 200, 200])).toBe("single");
    expect(classifyRelease("A", [200])).toBe("single");
  });

  it("calls anything over 30 minutes an album", () => {
    expect(classifyRelease("A", [1000, 1000])).toBe("album");
  });

  it("calls a short release with a 10-minute track an EP", () => {
    expect(classifyRelease("A", [600, 300])).toBe("ep");
  });

  it("lets an explicit EP / Single in the title win", () => {
    expect(classifyRelease("Night Drive EP", Array(8).fill(100))).toBe("ep");
    expect(classifyRelease("Remixes (EP)", [100])).toBe("ep");
    expect(classifyRelease("Vol. EP2", [100])).toBe("ep");
    expect(classifyRelease("Blue Monday - Single", Array(5).fill(100))).toBe("single");
    expect(classifyRelease("Blue Monday (Single)", Array(5).fill(100))).toBe("single");
    // Neither word as part of a longer title.
    expect(classifyRelease("Epic Tales", Array(8).fill(200))).toBe("album");
    expect(classifyRelease("Single Ladies Anthology", Array(8).fill(200))).toBe("album");
  });
});

describe("credits", () => {
  it("splits featuring tags on the usual separators", () => {
    expect(splitCredits("A, B & C")).toEqual(["a", "b", "c"]);
    expect(splitCredits("A feat. B")).toEqual(["a", "b"]);
    expect(splitCredits("A / B; C and D")).toEqual(["a", "b", "c", "d"]);
    expect(splitCredits(null)).toEqual([]);
  });

  it("matches on artist, album artist or a featuring credit, case-insensitively", () => {
    expect(creditsArtist(t("x", "Miles Davis", null), "miles davis")).toBe(true);
    expect(creditsArtist(t("x", "Other", null, { album_artist: "Miles Davis" }), "miles davis")).toBe(true);
    expect(creditsArtist(t("x", "Other", null, { featuring: "Coltrane, Miles Davis" }), "miles davis")).toBe(true);
    expect(creditsArtist(t("x", "Miles Davis Quintet", null), "miles davis")).toBe(false);
  });

  it("never links placeholder artists", () => {
    expect(isLinkableArtist("Miles Davis")).toBe(true);
    expect(isLinkableArtist("Unknown Artist")).toBe(false);
    expect(isLinkableArtist("Various Artists")).toBe(false);
    expect(isLinkableArtist("  ")).toBe(false);
    expect(isLinkableArtist(null)).toBe(false);
  });
});

describe("buildDiscography", () => {
  it("sorts the artist's releases into albums, EPs, singles, appears-on and loose tracks", () => {
    const lp = Array.from({ length: 8 }, (_, i) =>
      t(`LP ${i}`, "Miles Davis", "Kind of Blue", { album_artist: "Miles Davis", year: 1959 }),
    );
    const lp2 = Array.from({ length: 7 }, (_, i) =>
      t(`LP2 ${i}`, "Miles Davis", "Bitches Brew", { year: 1970 }),
    );
    const ep = Array.from({ length: 4 }, (_, i) => t(`EP ${i}`, "Miles Davis", "Four"));
    const single = [t("So What", "Miles Davis", "So What - Single")];
    const comp = [
      t("Comp A", "Miles Davis", "Jazz Hits"),
      t("Comp B", "Herbie Hancock", "Jazz Hits"),
      t("Comp C", "Thelonious Monk", "Jazz Hits"),
    ];
    const feat = [t("Guest", "Herbie Hancock", "Empyrean", { featuring: "Miles Davis" }), t("Host", "Herbie Hancock", "Empyrean")];
    const loose = [t("Loose", "Miles Davis", null), t("Blank", "Miles Davis", "  ")];
    const other = [t("Nope", "Herbie Hancock", "Head Hunters")];

    const d = discographyOf("miles davis", [...lp, ...lp2, ...ep, ...single, ...comp, ...feat, ...loose, ...other]);

    // Newest first.
    expect(titles(d.albums)).toEqual(["Bitches Brew", "Kind of Blue"]);
    expect(titles(d.eps)).toEqual(["Four"]);
    expect(titles(d.singles)).toEqual(["So What - Single"]);
    expect(titles(d.appearsOn)).toEqual(["Empyrean", "Jazz Hits"]);
    expect(d.looseTracks.map((x) => x.title)).toEqual(["Loose", "Blank"]);
    // Every credited track, none of the others.
    expect(d.tracks).toHaveLength(8 + 7 + 4 + 1 + 1 + 1 + 2);
    // A release is the whole album, not just the artist's tracks on it.
    expect(d.appearsOn.find((a) => a.album === "Jazz Hits")!.trackCount).toBe(3);
  });

  it("is empty for an artist the catalog does not know", () => {
    const d = discographyOf("Nobody", [t("x", "Somebody", "A")]);
    expect(d.tracks).toEqual([]);
    expect(d.albums.length + d.eps.length + d.singles.length + d.appearsOn.length).toBe(0);
  });
});
