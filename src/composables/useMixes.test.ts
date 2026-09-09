import { beforeEach, describe, expect, it } from "vitest";
import { ref, type Ref } from "vue";
import {
  findMix,
  normalizeGenre,
  sampleTrackIds,
  useMixes,
  type Mix,
} from "./useMixes";
import type { CatalogTrack } from "../api";

/**
 * The mix lineup is cached at module scope so it stays stable for a session,
 * which means these tests have to reset it between cases. `refresh()` is the
 * supported way to do that.
 */
function track(id: number, genre: string | null): CatalogTrack {
  return {
    id,
    path: `/m/${id}.mp3`,
    root_id: 1,
    title: `t${id}`,
    artist: "a",
    album: "al",
    album_artist: "a",
    featuring: null,
    year: 2020,
    genre,
    track_number: 1,
    disc_number: 1,
    duration_secs: 180,
    format: "mp3",
    mtime_secs: 0,
    has_cover: false,
    rating: null,
    play_count: 0,
    last_played_at: null,
  };
}

/** A source backed by refs, so the composable's watch behaves as in an app. */
function source(tracks: Ref<CatalogTrack[]>, ready: Ref<boolean>) {
  return { tracks: () => tracks.value, ready: () => ready.value };
}

describe("useMixes", () => {
  let tracks: Ref<CatalogTrack[]>;
  let ready: Ref<boolean>;

  beforeEach(() => {
    tracks = ref<CatalogTrack[]>([]);
    ready = ref(true);
    useMixes(source(tracks, ready)).refresh();
  });

  it("builds nothing while the catalog is still streaming in", () => {
    // A cohort whose genres live on a later page would otherwise be frozen at
    // zero tracks for the session.
    ready.value = false;
    tracks.value = [track(1, "Metal")];
    expect(useMixes(source(tracks, ready)).mixes.value).toEqual([]);
  });

  it("builds nothing from an empty catalog", () => {
    expect(useMixes(source(tracks, ready)).mixes.value).toEqual([]);
  });

  it("produces eight mixes once the catalog is loaded", () => {
    tracks.value = Array.from({ length: 50 }, (_, i) => track(i, "Metal"));
    const { mixes } = useMixes(source(tracks, ready));
    expect(mixes.value).toHaveLength(8);
    for (const mix of mixes.value) {
      expect(mix.name).toBeTruthy();
      expect(mix.emoji).toBeTruthy();
    }
  });

  it("gives every mix a distinct id", () => {
    tracks.value = Array.from({ length: 50 }, (_, i) => track(i, "Metal"));
    const ids = useMixes(source(tracks, ready)).mixes.value.map((m) => m.id);
    expect(new Set(ids).size).toBe(ids.length);
  });

  it("only samples tracks whose genre fits the cohort", () => {
    // "Polka" matches no cohort, so no mix may contain it.
    tracks.value = [
      ...Array.from({ length: 20 }, (_, i) => track(i, "Metal")),
      ...Array.from({ length: 20 }, (_, i) => track(100 + i, "Polka")),
    ];
    const polkaIds = new Set(tracks.value.filter((t) => t.genre === "Polka").map((t) => t.id));
    for (const mix of useMixes(source(tracks, ready)).mixes.value) {
      for (const id of mix.trackIds) expect(polkaIds.has(id)).toBe(false);
    }
  });

  it("never repeats a track within one mix", () => {
    tracks.value = Array.from({ length: 200 }, (_, i) => track(i, "Metal"));
    for (const mix of useMixes(source(tracks, ready)).mixes.value) {
      expect(new Set(mix.trackIds).size).toBe(mix.trackIds.length);
    }
  });

  it("takes what exists rather than padding when the pool is small", () => {
    tracks.value = [track(1, "Metalcore"), track(2, "Metalcore")];
    const withTracks = useMixes(source(tracks, ready)).mixes.value.filter(
      (m) => m.trackIds.length > 0,
    );
    for (const mix of withTracks) expect(mix.trackIds.length).toBeLessThanOrEqual(2);
  });

  it("ignores tracks with no genre at all", () => {
    tracks.value = Array.from({ length: 20 }, (_, i) => track(i, null));
    const { mixes } = useMixes(source(tracks, ready));
    expect(mixes.value.every((m) => m.trackIds.length === 0)).toBe(true);
  });

  it("caches the lineup so it does not re-roll on every read", () => {
    tracks.value = Array.from({ length: 200 }, (_, i) => track(i, "Metal"));
    const { mixes } = useMixes(source(tracks, ready));
    expect(JSON.stringify(mixes.value)).toBe(JSON.stringify(mixes.value));
  });

  it("findMix resolves an id from the current lineup, and null otherwise", () => {
    tracks.value = Array.from({ length: 50 }, (_, i) => track(i, "Metal"));
    const first: Mix = useMixes(source(tracks, ready)).mixes.value[0];
    expect(findMix(first.id)?.name).toBe(first.name);
    expect(findMix(9999)).toBeNull();
  });
});

/**
 * The cohort lineup is eight of sixteen picked at random per session, so
 * asserting "a Lo-Fi track ends up in some mix" is a coin flip — only two
 * cohorts list Lo-Fi genres, and the draw excludes both about a quarter of the
 * time. The matching itself is deterministic, so it is tested directly.
 */
describe("genre matching", () => {
  it("normalises away case, punctuation and spacing", () => {
    expect(normalizeGenre("Lo-Fi")).toBe("lofi");
    expect(normalizeGenre("Lofi")).toBe("lofi");
    expect(normalizeGenre("lo fi")).toBe("lofi");
    expect(normalizeGenre("Drum & Bass")).toBe("drumbass");
    expect(normalizeGenre("")).toBe("");
  });

  it("treats every spelling of a genre as the same pool", () => {
    for (const spelling of ["Lo-Fi", "Lofi", "lo fi", "LO-FI"]) {
      const picked = sampleTrackIds([track(1, spelling)], ["Lo-Fi"]);
      expect(picked, `"${spelling}" did not match`).toEqual([1]);
    }
  });

  it("matches a broad cohort genre inside a more specific tag", () => {
    // "Metal" must catch "Progressive Metal"...
    expect(sampleTrackIds([track(1, "Progressive Metal")], ["Metal"])).toEqual([1]);
    // ...and the containment works the other way too.
    expect(sampleTrackIds([track(1, "Metal")], ["Progressive Metal"])).toEqual([1]);
  });

  it("excludes a genre in no cohort", () => {
    expect(sampleTrackIds([track(1, "Polka")], ["Metal", "Punk"])).toEqual([]);
  });

  it("skips tracks with a null or blank genre", () => {
    expect(sampleTrackIds([track(1, null), track(2, "  ")], ["Metal"])).toEqual([]);
  });

  it("never returns a duplicate, and never more than the pool holds", () => {
    const pool = Array.from({ length: 5 }, (_, i) => track(i, "Metal"));
    const picked = sampleTrackIds(pool, ["Metal"]);
    expect(picked).toHaveLength(5);
    expect(new Set(picked).size).toBe(5);
  });

  it("caps a large pool at the mix size", () => {
    const pool = Array.from({ length: 500 }, (_, i) => track(i, "Metal"));
    const picked = sampleTrackIds(pool, ["Metal"]);
    expect(picked.length).toBeLessThanOrEqual(40);
    expect(new Set(picked).size).toBe(picked.length);
  });

  it("only ever returns ids that were in the pool", () => {
    const pool = Array.from({ length: 20 }, (_, i) => track(i, "Metal"));
    const ids = new Set(pool.map((t) => t.id));
    for (const id of sampleTrackIds(pool, ["Metal"])) expect(ids.has(id)).toBe(true);
  });
});
