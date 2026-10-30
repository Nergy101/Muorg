import { describe, it, expect } from "vitest";
import {
  findAlbumCoverUrl,
  normalizeFileTitle,
  pickBestAlbumImage,
  scoreImageAsAlbumArt,
} from "./wikipediaCover";

describe("normalizeFileTitle", () => {
  it("drops the File: prefix and reduces the name to letters and digits", () => {
    expect(normalizeFileTitle("File:OK Computer cover.jpg")).toEqual({
      name: "okcomputercover",
      ext: "jpg",
    });
  });

  it("reports no extension when there is none", () => {
    expect(normalizeFileTitle("File:Something")).toEqual({ name: "something", ext: "" });
  });
});

describe("scoreImageAsAlbumArt", () => {
  it("prefers a file naming the album over one that does not", () => {
    const named = scoreImageAsAlbumArt("File:OK Computer.jpg", "OK Computer");
    const other = scoreImageAsAlbumArt("File:Band photo.jpg", "OK Computer");
    expect(named).toBeGreaterThan(other);
  });

  it("matches the album name through punctuation and spacing", () => {
    // "Sgt. Pepper's" on Wikipedia is "Sgt.PepperLonelyHearts...", so the
    // comparison has to ignore everything but letters and digits.
    const score = scoreImageAsAlbumArt(
      "File:Sgt. Pepper's Lonely Hearts Club Band.jpg",
      "Sgt Peppers Lonely Hearts Club Band",
    );
    expect(score).toBeGreaterThan(0);
  });

  it("pushes page furniture below zero", () => {
    // These are what an article's image list is mostly made of, and picking one
    // put a rating star into someone's album tag.
    for (const junk of [
      "File:Star full.svg",
      "File:Edit-clear.svg",
      "File:Button hide.png",
      "File:Arrow right.svg",
      "File:Loudspeaker icon.svg",
    ]) {
      expect(scoreImageAsAlbumArt(junk, "OK Computer")).toBeLessThan(0);
    }
  });

  it("rewards a real raster extension", () => {
    const jpg = scoreImageAsAlbumArt("File:Something.jpg", "");
    const tiff = scoreImageAsAlbumArt("File:Something.tiff", "");
    expect(jpg).toBeGreaterThan(tiff);
  });
});

describe("pickBestAlbumImage", () => {
  it("picks the cover out of a realistic image list", () => {
    const images = [
      { title: "File:Star empty.svg" },
      { title: "File:OK Computer cover.png" },
      { title: "File:Edit-clear.svg" },
      { title: "File:Loudspeaker.svg" },
    ];
    expect(pickBestAlbumImage(images, "OK Computer")).toBe("File:OK Computer cover.png");
  });

  it("ignores entries that are not files", () => {
    const images = [{ title: "Template:Infobox album" }, { title: "File:Cover.jpg" }];
    expect(pickBestAlbumImage(images, "Cover")).toBe("File:Cover.jpg");
  });

  it("returns null for an empty list", () => {
    expect(pickBestAlbumImage([], "Anything")).toBeNull();
  });

  it("still returns the least-bad option when everything looks like furniture", () => {
    // Better to show the user something and let them refuse it than to report
    // "no image" when the page does have one.
    const images = [{ title: "File:Star.svg" }];
    expect(pickBestAlbumImage(images, "Anything")).toBe("File:Star.svg");
  });
});

describe("findAlbumCoverUrl", () => {
  /** Query strings encode spaces as `+`, which decodeURIComponent leaves alone. */
  function readable(url: string): string {
    return decodeURIComponent(url).split("+").join(" ");
  }

  /** A fetch that answers the three calls in order from canned bodies. */
  function stubFetch(bodies: unknown[]): { impl: typeof fetch; urls: string[] } {
    const urls: string[] = [];
    let i = 0;
    const impl = (async (input: RequestInfo | URL) => {
      urls.push(String(input));
      const body = bodies[Math.min(i++, bodies.length - 1)];
      return { json: async () => body } as Response;
    }) as typeof fetch;
    return { impl, urls };
  }

  const searchHit = {
    query: { pages: { "1": { pageid: 42, title: "OK Computer", index: 1 } } },
  };

  it("walks search → images → imageinfo and returns the URL", async () => {
    const { impl, urls } = stubFetch([
      searchHit,
      { query: { pages: { "42": { images: [{ title: "File:OK Computer cover.jpg" }] } } } },
      { query: { pages: { "9": { imageinfo: [{ url: "https://upload.example/cover.jpg" }] } } } },
    ]);

    const result = await findAlbumCoverUrl("OK Computer", "Radiohead", impl);
    expect(result).toEqual({ ok: true, url: "https://upload.example/cover.jpg" });
    expect(urls).toHaveLength(3);
    // Every call needs origin=* or MediaWiki sends no CORS headers and the
    // browser rejects the response before the code sees it.
    expect(urls.every((u) => u.includes("origin=*"))).toBe(true);
    // "(album)" steers the search off the song and the film of the same name.
    expect(readable(urls[0])).toContain("OK Computer (album)");
  });

  it("takes the top-ranked search result, not the first key", async () => {
    // `generator=search` returns a keyed object; only `index` carries the rank,
    // so reading the object's first entry picks an arbitrary article.
    const { impl, urls } = stubFetch([
      {
        query: {
          pages: {
            "100": { pageid: 100, title: "Wrong article", index: 3 },
            "7": { pageid: 7, title: "Right article", index: 1 },
          },
        },
      },
      { query: { pages: { "7": { images: [{ title: "File:Cover.jpg" }] } } } },
      { query: { pages: { "1": { imageinfo: [{ url: "https://upload.example/c.jpg" }] } } } },
    ]);

    const result = await findAlbumCoverUrl("Album", "Artist", impl);
    expect(result.ok).toBe(true);
    expect(urls[1]).toContain("pageids=7");
  });

  it("says so when there is nothing to search for", async () => {
    const { impl, urls } = stubFetch([]);
    const result = await findAlbumCoverUrl("  ", "", impl);
    expect(result).toEqual({ ok: false, reason: "Enter an album (or artist) name first." });
    expect(urls).toHaveLength(0);
  });

  it("falls back to the artist when there is no album name", async () => {
    const { impl, urls } = stubFetch([
      searchHit,
      { query: { pages: { "42": { images: [{ title: "File:Cover.jpg" }] } } } },
      { query: { pages: { "1": { imageinfo: [{ url: "https://upload.example/c.jpg" }] } } } },
    ]);
    await findAlbumCoverUrl("", "Radiohead", impl);
    expect(readable(urls[0])).toContain("Radiohead");
    expect(readable(urls[0])).not.toContain("(album)");
  });

  it("reports each stage that can come back empty", async () => {
    const noPage = await findAlbumCoverUrl("A", "B", stubFetch([{ query: {} }]).impl);
    expect(noPage).toEqual({ ok: false, reason: "No Wikipedia page found for this album." });

    const noImages = await findAlbumCoverUrl(
      "A",
      "B",
      stubFetch([searchHit, { query: { pages: { "42": { images: [] } } } }]).impl,
    );
    expect(noImages).toEqual({ ok: false, reason: "No image found on this Wikipedia page." });

    const noUrl = await findAlbumCoverUrl(
      "A",
      "B",
      stubFetch([
        searchHit,
        { query: { pages: { "42": { images: [{ title: "File:Cover.jpg" }] } } } },
        { query: { pages: { "1": {} } } },
      ]).impl,
    );
    expect(noUrl).toEqual({ ok: false, reason: "Could not get image URL." });
  });
});
