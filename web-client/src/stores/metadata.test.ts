import { describe, expect, it } from "vitest";
import {
  applyExtractedToForm,
  buildFieldUpdate,
  EMPTY_METADATA_FORM,
  sharedFormValue,
} from "./metadata";
import type { MetadataFieldKey, MetadataForm } from "./metadata";

function form(over: Partial<MetadataForm> = {}): MetadataForm {
  return { ...EMPTY_METADATA_FORM, ...over };
}

/**
 * Building the `MetadataUpdate` body for a save.
 *
 * The rule that matters: single-track saves always send every field (the form
 * already holds the track's current values), while bulk saves send ONLY the
 * fields the user touched or explicitly cleared — an untouched field in bulk
 * mode must never overwrite other tracks' values.
 */
describe("buildFieldUpdate", () => {
  it("single track: writes every field, converting blanks to null", () => {
    const f = form({ title: "A song", artist: "An artist", year: "2001" });
    const update = buildFieldUpdate(f, new Set(), new Set(), false);
    expect(update).toEqual({
      title: "A song",
      artist: "An artist",
      album: null,
      album_artist: null,
      featuring: null,
      year: 2001,
      genre: null,
      track_number: null,
      disc_number: null,
    });
  });

  it("bulk: only includes fields that were touched", () => {
    const f = form({ title: "Ignored", artist: "New artist" });
    const update = buildFieldUpdate(f, new Set<MetadataFieldKey>(["artist"]), new Set(), true);
    expect(update).toEqual({ artist: "New artist" });
  });

  it("bulk: an explicitly cleared field is sent as null even though it was never 'touched' otherwise", () => {
    const f = form();
    const update = buildFieldUpdate(f, new Set<MetadataFieldKey>(["genre"]), new Set<MetadataFieldKey>(["genre"]), true);
    expect(update).toEqual({ genre: null });
  });

  it("parses numeric fields, mapping blank or non-numeric to null", () => {
    const f = form({ year: "", track_number: "7", disc_number: "not a number" });
    const update = buildFieldUpdate(
      f,
      new Set<MetadataFieldKey>(["year", "track_number", "disc_number"]),
      new Set(),
      true,
    );
    expect(update.year).toBeNull();
    expect(update.track_number).toBe(7);
    expect(update.disc_number).toBeNull();
  });

  it("trims text fields and maps an empty result to null", () => {
    const f = form({ title: "  Padded  ", artist: "   " });
    const update = buildFieldUpdate(f, new Set<MetadataFieldKey>(["title", "artist"]), new Set(), true);
    expect(update.title).toBe("Padded");
    expect(update.artist).toBeNull();
  });

  it("never includes an untouched, uncleared field in bulk mode", () => {
    const f = form({ album: "Some album" });
    const update = buildFieldUpdate(f, new Set(), new Set(), true);
    expect(update).toEqual({});
  });
});

describe("applyExtractedToForm", () => {
  it("fills mapped fields and reports which keys it touched", () => {
    const { form: next, touched } = applyExtractedToForm(form(), {
      artist: "Extracted Artist",
      tracktitle: "Extracted Title",
      tracknumber: "3",
    });
    expect(next.artist).toBe("Extracted Artist");
    expect(next.title).toBe("Extracted Title");
    expect(next.track_number).toBe("3");
    expect(touched.sort()).toEqual(["artist", "title", "track_number"].sort());
  });

  it("ignores keys with no path-format mapping", () => {
    const { form: next, touched } = applyExtractedToForm(form(), { unknownKey: "x" });
    expect(next).toEqual(form());
    expect(touched).toEqual([]);
  });

  it("drops a non-numeric value for a numeric field instead of writing garbage", () => {
    const { form: next, touched } = applyExtractedToForm(form(), { year: "not-a-year" });
    expect(next.year).toBe("");
    expect(touched).toEqual([]);
  });

  it("does not mutate the form it was given", () => {
    const original = form({ artist: "Old" });
    const snapshot = { ...original };
    applyExtractedToForm(original, { artist: "New" });
    expect(original).toEqual(snapshot);
  });
});

describe("sharedFormValue", () => {
  it("returns the value every track shares", () => {
    expect(sharedFormValue(["Rock", "Rock", "Rock"])).toBe("Rock");
  });

  it("returns empty when the values differ", () => {
    expect(sharedFormValue(["Rock", "Jazz"])).toBe("");
  });

  it("treats null and undefined as equivalent to an empty string", () => {
    expect(sharedFormValue([null, undefined, ""])).toBe("");
    expect(sharedFormValue(["Rock", null])).toBe("");
  });

  it("returns empty for an empty list", () => {
    expect(sharedFormValue([])).toBe("");
  });
});
