/**
 * ReplayGain volume math, mirroring the desktop app's `getReplayGainDb` /
 * `applyEffectiveVolume` in `client/src/components/playback/PlayerBar.vue`:
 * pick the track or album gain tag by mode, add the user preamp, convert the
 * dB delta to a linear multiplier, and apply it on top of the user's volume.
 *
 * Desktop always clamps the final `<audio>.volume` to 1 regardless of the
 * "prevent clipping" setting — the element's own range already forbids values
 * above unity, so a plain clamp-to-1 is a no-op either way there. This module
 * goes one step further and makes the toggle actually do something: when a
 * `replaygain_*_peak` tag is present, clipping prevention caps the linear
 * multiplier so the track's loudest sample does not exceed full scale
 * (true ReplayGain peak limiting), instead of just clamping the whole mix to
 * the element's maximum. With no known peak it falls back to desktop's
 * clamp-to-1 behaviour.
 */

export type ReplayGainMode = "off" | "track" | "album";

/** The ReplayGain tag subset this module reads; a superset of the server's
 *  `TrackMetadata` shape so real metadata objects satisfy it directly. */
export interface ReplayGainTags {
  replaygain_track_gain_db?: number | null;
  replaygain_album_gain_db?: number | null;
  replaygain_track_peak?: number | null;
  replaygain_album_peak?: number | null;
}

export interface ReplayGainOptions {
  mode: ReplayGainMode;
  /** dB applied on top of the tag's gain, positive or negative. */
  preampDb: number;
  preventClipping: boolean;
}

/** Converts a decibel delta to a linear amplitude multiplier. */
export function dbToLinear(db: number): number {
  return Math.pow(10, db / 20);
}

/**
 * Resolves the ReplayGain dB adjustment for a track: the track or album gain
 * tag (by mode) plus the user preamp. Mirrors desktop's `getReplayGainDb`.
 * Returns 0 (no change) when the mode is off, there are no tags, or the tag
 * for the selected mode is missing.
 */
export function getReplayGainDb(
  tags: ReplayGainTags | null | undefined,
  options: Pick<ReplayGainOptions, "mode" | "preampDb">,
): number {
  if (options.mode === "off" || !tags) return 0;
  const rg =
    options.mode === "album" ? tags.replaygain_album_gain_db : tags.replaygain_track_gain_db;
  const withPreamp = (rg ?? 0) + (options.preampDb ?? 0);
  return Number.isFinite(withPreamp) ? withPreamp : 0;
}

/**
 * Computes the effective `<audio>.volume` for a track: the user's volume
 * scaled by the ReplayGain linear multiplier, always within the element's
 * [0, 1] range (values above unity would need a Web Audio GainNode, which this
 * app does not use — see the player store for why).
 *
 * Desktop only clamps to 1. With "prevent clipping" and a usable peak tag this
 * also caps the multiplier at `1 / peak`, so a file whose samples already
 * exceed full scale (peak > 1) is turned down instead of played clipped. For a
 * normal peak <= 1 the cap is >= 1 and the unity clamp binds first, exactly as
 * on desktop.
 */
export function computeReplayGainVolume(
  userVolume: number,
  tags: ReplayGainTags | null | undefined,
  options: ReplayGainOptions,
): number {
  const base = Math.min(1, Math.max(0, userVolume));
  let linear = dbToLinear(getReplayGainDb(tags, options));
  if (options.preventClipping) {
    const peak =
      options.mode === "album" ? tags?.replaygain_album_peak : tags?.replaygain_track_peak;
    if (peak != null && Number.isFinite(peak) && peak > 0) linear = Math.min(linear, 1 / peak);
  }
  return Math.max(0, Math.min(1, base * linear));
}
