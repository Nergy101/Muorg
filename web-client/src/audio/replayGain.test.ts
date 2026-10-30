import { describe, expect, it } from "vitest";
import { computeReplayGainVolume, dbToLinear, getReplayGainDb } from "./replayGain";

describe("dbToLinear", () => {
  it("0 dB is unity gain", () => {
    expect(dbToLinear(0)).toBeCloseTo(1, 10);
  });
  it("+6 dB roughly doubles amplitude", () => {
    expect(dbToLinear(6)).toBeCloseTo(1.9953, 3);
  });
  it("-6 dB roughly halves amplitude", () => {
    expect(dbToLinear(-6)).toBeCloseTo(0.5012, 3);
  });
});

describe("getReplayGainDb", () => {
  const tags = {
    replaygain_track_gain_db: -3,
    replaygain_album_gain_db: -1.5,
    replaygain_track_peak: 0.98,
    replaygain_album_peak: 0.95,
  };

  it("is 0 when mode is off, even with tags present", () => {
    expect(getReplayGainDb(tags, { mode: "off", preampDb: 5 })).toBe(0);
  });

  it("is 0 when there are no tags", () => {
    expect(getReplayGainDb(null, { mode: "track", preampDb: 0 })).toBe(0);
  });

  it("picks the track gain in track mode", () => {
    expect(getReplayGainDb(tags, { mode: "track", preampDb: 0 })).toBe(-3);
  });

  it("picks the album gain in album mode", () => {
    expect(getReplayGainDb(tags, { mode: "album", preampDb: 0 })).toBe(-1.5);
  });

  it("adds the preamp on top of the tag gain", () => {
    expect(getReplayGainDb(tags, { mode: "track", preampDb: 4 })).toBe(1);
  });

  it("falls back to 0 dB when the selected tag is missing", () => {
    expect(
      getReplayGainDb(
        { replaygain_track_gain_db: null, replaygain_album_gain_db: null },
        { mode: "track", preampDb: 2 },
      ),
    ).toBe(2);
  });
});

describe("computeReplayGainVolume", () => {
  it("off mode returns the user volume unchanged", () => {
    expect(
      computeReplayGainVolume(0.6, { replaygain_track_gain_db: -6 }, { mode: "off", preampDb: 0, preventClipping: true }),
    ).toBeCloseTo(0.6, 10);
  });

  it("negative gain reduces the effective volume", () => {
    const v = computeReplayGainVolume(
      0.8,
      { replaygain_track_gain_db: -6 },
      { mode: "track", preampDb: 0, preventClipping: false },
    );
    expect(v).toBeCloseTo(0.8 * dbToLinear(-6), 10);
  });

  it("positive gain increases the effective volume, clamped to 1", () => {
    const v = computeReplayGainVolume(
      0.9,
      { replaygain_track_gain_db: 6 },
      { mode: "track", preampDb: 0, preventClipping: false },
    );
    // 0.9 * ~2 = ~1.8, clamped to the element's max.
    expect(v).toBe(1);
  });

  it("clamps to [0, 1] regardless of input volume range", () => {
    expect(
      computeReplayGainVolume(-1, null, { mode: "off", preampDb: 0, preventClipping: false }),
    ).toBe(0);
    expect(
      computeReplayGainVolume(5, null, { mode: "off", preampDb: 0, preventClipping: false }),
    ).toBe(1);
  });

  it("without preventClipping, a boosted track still clamps to the element max (1)", () => {
    const v = computeReplayGainVolume(
      1,
      { replaygain_track_gain_db: 6, replaygain_track_peak: 0.5 },
      { mode: "track", preampDb: 0, preventClipping: false },
    );
    expect(v).toBe(1);
  });

  it("with preventClipping and a normal peak (<= 1), the unity clamp binds exactly as on desktop", () => {
    const v = computeReplayGainVolume(
      1,
      { replaygain_track_gain_db: 6, replaygain_track_peak: 0.5 },
      { mode: "track", preampDb: 0, preventClipping: true },
    );
    expect(v).toBe(1);
  });

  it("with preventClipping and a peak above full scale, turns the track down by 1/peak", () => {
    // The file's samples already reach 1.25 of full scale; 0 dB gain would
    // clip, so the multiplier is capped at 1 / 1.25 = 0.8.
    const v = computeReplayGainVolume(
      1,
      { replaygain_track_gain_db: 0, replaygain_track_peak: 1.25 },
      { mode: "track", preampDb: 0, preventClipping: true },
    );
    expect(v).toBeCloseTo(0.8, 5);
  });

  it("ignores an unusable peak (0 or missing) and falls back to the unity clamp", () => {
    const v = computeReplayGainVolume(
      1,
      { replaygain_track_gain_db: 6, replaygain_track_peak: 0 },
      { mode: "track", preampDb: 0, preventClipping: true },
    );
    expect(v).toBe(1);
  });

  it("uses the album peak in album mode", () => {
    const v = computeReplayGainVolume(
      1,
      { replaygain_album_gain_db: 6, replaygain_album_peak: 0.5, replaygain_track_peak: 0.1 },
      { mode: "album", preampDb: 0, preventClipping: true },
    );
    expect(v).toBeCloseTo(1, 5);
  });
});
