import { describe, expect, it } from "vitest";
import { applyCandidate, confidenceClass, rankCandidates } from "./useAutoTag";
import type { EditForm } from "./useAutoTag";
import type { MatchCandidate } from "../types";

/**
 * Filling the metadata form from a MusicBrainz match.
 *
 * The rule that matters: a candidate that says nothing about a field must leave
 * what is already there. Only `title` and `artist` are guaranteed on the wire —
 * everything else is nullable — so a naive assignment blanks the genre you just
 * typed, and does it silently.
 */
describe("applyCandidate", () => {
  const form: EditForm = {
    title: "Old title",
    artist: "Old artist",
    album: "Old album",
    album_artist: "Old album artist",
    genre: "Doom",
    year: "1999",
  };

  const candidate = (over: Partial<MatchCandidate> = {}): MatchCandidate => ({
    mbid: "mb-1",
    title: "New title",
    artist: "New artist",
    album: "New album",
    album_artist: "New album artist",
    year: 2004,
    track_number: 3,
    confidence: 0.9,
    ...over,
  }) as MatchCandidate;

  it("takes what the match provides", () => {
    const next = applyCandidate(form, candidate());
    expect(next.title).toBe("New title");
    expect(next.artist).toBe("New artist");
    expect(next.album).toBe("New album");
    expect(next.album_artist).toBe("New album artist");
    expect(next.year).toBe("2004");
  });

  it("keeps a field the match has nothing to say about", () => {
    const next = applyCandidate(form, candidate({ album: null, album_artist: null, year: null }));
    expect(next.album).toBe("Old album");
    expect(next.album_artist).toBe("Old album artist");
    expect(next.year).toBe("1999");
  });

  it("keeps a field the match sends as an empty string", () => {
    // MusicBrainz has both shapes; an empty album is absence, not a value.
    const next = applyCandidate(form, candidate({ album: "", album_artist: "" }));
    expect(next.album).toBe("Old album");
    expect(next.album_artist).toBe("Old album artist");
  });

  it("never touches genre — MusicBrainz recordings do not carry one", () => {
    expect(applyCandidate(form, candidate()).genre).toBe("Doom");
  });

  it("keeps year 0 distinguishable from no year", () => {
    expect(applyCandidate(form, candidate({ year: 0 })).year).toBe("0");
  });

  it("does not mutate the form it was given", () => {
    const original = { ...form };
    applyCandidate(form, candidate());
    expect(form).toEqual(original);
  });
});

describe("rankCandidates", () => {
  const at = (confidence: number, mbid: string) =>
    ({ mbid, title: "t", artist: "a", confidence }) as MatchCandidate;

  it("puts the best match first", () => {
    const ranked = rankCandidates([at(0.4, "c"), at(0.95, "a"), at(0.7, "b")]);
    expect(ranked.map((c) => c.mbid)).toEqual(["a", "b", "c"]);
  });

  it("does not mutate the list it was given", () => {
    const input = [at(0.4, "c"), at(0.95, "a")];
    rankCandidates(input);
    expect(input.map((c) => c.mbid)).toEqual(["c", "a"]);
  });

  it("handles an empty result", () => {
    expect(rankCandidates([])).toEqual([]);
  });
});

describe("confidenceClass", () => {
  it("separates strong, plausible and weak matches", () => {
    const strong = confidenceClass(0.9);
    const middling = confidenceClass(0.6);
    const weak = confidenceClass(0.2);
    expect(new Set([strong, middling, weak]).size).toBe(3);
  });

  it("treats the thresholds as inclusive lower bounds", () => {
    expect(confidenceClass(0.8)).toBe(confidenceClass(1));
    expect(confidenceClass(0.5)).toBe(confidenceClass(0.79));
  });
});
