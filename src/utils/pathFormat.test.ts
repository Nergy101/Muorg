import { describe, it, expect } from "vitest";
import {
  PATH_FIELD_MAP,
  buildTransformPath,
  buildUpdateFromExtracted,
  extractBestFromPath,
  extractMetadataFromPath,
} from "./pathFormat";

describe("extractMetadataFromPath", () => {
  it("extracts fields from a multi-segment template with an extension", () => {
    const out = extractMetadataFromPath(
      "<Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>",
      "/music/Pink Floyd/The Wall/01 - In the Flesh.mp3",
    );
    expect(out).toEqual({
      Artist: "Pink Floyd",
      Album: "The Wall",
      TrackNumber: "01",
      TrackTitle: "In the Flesh",
      Format: "mp3",
    });
  });

  it("returns null for an empty template", () => {
    expect(extractMetadataFromPath("   ", "/a/b.mp3")).toBeNull();
  });

  it("returns null when the path has fewer segments than the template", () => {
    expect(extractMetadataFromPath("<Artist>/<Album>/<Title>", "/only/two")).toBeNull();
  });

  it("returns null when a literal segment does not match", () => {
    expect(extractMetadataFromPath("Music/<Album>", "/Other/Album")).toBeNull();
  });

  it("matches literal segments exactly", () => {
    expect(extractMetadataFromPath("Music/<Album>", "/root/Music/Album")).toEqual({ Album: "Album" });
  });

  it("returns null when a placeholder segment does not fit the pattern", () => {
    expect(extractMetadataFromPath("<TrackNumber> - <Title>.<Format>", "/x/nodash.mp3")).toBeNull();
  });

  it("handles a whole-segment extension placeholder with no name", () => {
    expect(extractMetadataFromPath("<Title>.<ext>", "/x/Song.flac")).toEqual({
      Title: "Song",
      Format: "flac",
    });
  });

  it("handles a segment that is only an extension placeholder", () => {
    expect(extractMetadataFromPath("<Artist>/.<ext>", "/x/Band/.ogg")).toEqual({
      Artist: "Band",
      Format: "ogg",
    });
  });

  it("supports a backslash-separated Windows path", () => {
    expect(extractMetadataFromPath("<Artist>/<Album>", "C:\\Music\\Band\\Record")).toEqual({
      Artist: "Band",
      Album: "Record",
    });
  });

  it("returns null when the path segment is missing for a placeholder", () => {
    expect(extractMetadataFromPath("<A>/<B>", "/x/")).toBeNull();
  });
});

describe("extractBestFromPath", () => {
  it("returns null when there are no templates or none match", () => {
    expect(extractBestFromPath([], "/a/b.mp3")).toBeNull();
    expect(extractBestFromPath(["   ", "Nope/<X>"], "/a/b.mp3")).toBeNull();
  });

  it("picks the template that maps the most metadata fields", () => {
    const templates = ["<Album>", "<Artist>/<Album>/<TrackNumber> - <TrackTitle>"];
    const out = extractBestFromPath(templates, "/m/Band/Record/02 - Song");
    expect(out).toEqual({ Artist: "Band", Album: "Record", TrackNumber: "02", TrackTitle: "Song" });
  });
});

describe("buildUpdateFromExtracted", () => {
  it("maps keys case- and underscore-insensitively and parses numbers", () => {
    const update = buildUpdateFromExtracted({
      Track_Number: "7",
      disc_number: "2",
      Year: "1999",
      ARTIST: "A",
      album_artist: "AA",
      Genre: "Rock",
      featuring: "X",
      TrackTitle: "T",
      Album: "Al",
    });
    expect(update).toEqual({
      track_number: 7,
      disc_number: 2,
      year: 1999,
      artist: "A",
      album_artist: "AA",
      genre: "Rock",
      featuring: "X",
      title: "T",
      album: "Al",
    });
  });

  it("skips unknown keys and the picture field", () => {
    expect(buildUpdateFromExtracted({ Bogus: "x", picture: "abc" })).toEqual({});
  });

  it("drops non-numeric and blank numeric values", () => {
    expect(
      buildUpdateFromExtracted({ TrackNumber: "abc", Year: "   ", DiscNumber: "" }),
    ).toEqual({});
  });

  it("turns empty string text values into null", () => {
    expect(buildUpdateFromExtracted({ Title: "", Artist: "", Album: "", Genre: "" })).toEqual({
      title: null,
      artist: null,
      album: null,
      genre: null,
    });
  });

  it("exposes a field map that covers the documented aliases", () => {
    expect(PATH_FIELD_MAP.tracktitle).toBe("title");
    expect(PATH_FIELD_MAP.album_artist).toBe("albumArtist");
  });
});

describe("buildTransformPath", () => {
  it("rewrites a path using the origin template's variables and keeps the prefix", () => {
    expect(
      buildTransformPath(
        "<Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>",
        "<Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>",
        "/music/Band/Record/01 - Song.mp3",
      ),
    ).toBe("/music/Band/Record/01 - Song.mp3");
  });

  it("moves a file into a new layout", () => {
    expect(
      buildTransformPath(
        "<Artist>/<Album>",
        "<Artist>-<Album>",
        "/lib/Band/Record",
      ),
    ).toBe("/lib/Band-Record");
  });

  it("returns null when the origin template does not match", () => {
    expect(buildTransformPath("<A>/<B>/<C>", "<A>", "/x")).toBeNull();
  });

  it("returns null when the destination references a missing variable", () => {
    expect(buildTransformPath("<Artist>", "<Artist>/<Missing>", "/lib/Band")).toBeNull();
  });

  it("works for a relative path and produces an absolute destination", () => {
    expect(buildTransformPath("<Artist>", "<Artist>", "Band")).toBe("/Band");
  });

  it("normalises backslashes in the input path", () => {
    expect(buildTransformPath("<Artist>", "<Artist>", "C:\\lib\\Band")).toBe("C:/lib/Band");
  });
});
