import { computed, type ComputedRef } from "vue";
import { useLibraryStore } from "../stores/library";
import type { CatalogTrack } from "../types";

/**
 * Ephemeral "Mixes" for the Home tab: a rotating set of randomly assembled
 * ~20-track playlists, each sampled from genres that fit its name (Gym Fuel is
 * metalcore, Deep Focus is metal + electronic, …). Sixteen cohorts exist but
 * only eight are shown per session, picked at random — so the lineup moves
 * in and out between page loads. They are never written to the server; opening
 * one renders it via the playlist detail view, and saving goes through the
 * normal New-playlist flow (name + emoji, then the mix's tracks are added to
 * the new playlist).
 *
 * A module-level cache keeps the chosen 8 mixes (and their track picks) stable
 * for the whole session: KeepAlive keeps Home mounted, the mix detail view
 * reads the same list, and opening a mix then going back does not re-roll it
 * under you. A fresh page load re-rolls both the lineup and the tracks.
 */

export interface Mix {
  id: number;
  name: string;
  emoji: string;
  trackIds: number[];
}

const MIX_SIZE = 40;
/** How many of the cohorts are shown per session. */
const MIX_COUNT = 8;

/** Sixteen genre cohorts, chosen to match the library's actual tags (verified
 *  against the live catalog). Each mix samples from its own genre pool only
 *  (up to MIX_SIZE tracks) — a small pool just makes a shorter mix, never a
 *  mix polluted with unrelated genres. */
const MIX_DEFS: { name: string; emoji: string; genres: string[] }[] = [
  { name: "Midnight Drive", emoji: "🌙", genres: ["Electronic", "Lo-Fi", "Indie", "Instrumental"] },
  { name: "Morning Coffee", emoji: "☀️", genres: ["Indie Rock", "Indie", "Pop", "Pop Rock"] },
  { name: "Summer Breeze", emoji: "🏖️", genres: ["Country", "Indie Rock", "Indie", "Pop Rock"] },
  { name: "Gym Fuel", emoji: "🔥", genres: ["Metalcore", "Hardcore", "Nu Metal"] },
  { name: "Rainy Day", emoji: "🌧️", genres: ["Emo", "Post-Hardcore", "Alternative"] },
  { name: "Road Trip", emoji: "🚗", genres: ["Punk Rock", "Pop Punk", "Classic Rock"] },
  { name: "Deep Focus", emoji: "💫", genres: ["Metal", "Electronic", "Progressive Metal", "Instrumental"] },
  { name: "Party Starter", emoji: "🎉", genres: ["Nu Metal", "Pop", "Electronic", "Rap"] },
  { name: "Mosh Pit", emoji: "🤘", genres: ["Thrash Metal", "Death Metal", "Metal"] },
  { name: "Golden Hour", emoji: "🌅", genres: ["Alternative Rock", "Indie Rock", "Pop Rock"] },
  { name: "Late Night Lo-Fi", emoji: "😴", genres: ["Lo-Fi", "Rap", "Instrumental"] },
  { name: "Skatepark", emoji: "🛹", genres: ["Pop Punk", "Punk Rock", "Hardcore"] },
  { name: "Sunday Chill", emoji: "😌", genres: ["Alternative Indie", "Indie", "Pop"] },
  { name: "Thunderstorm", emoji: "⛈️", genres: ["Thrash Metal", "Death Metal", "Hard Rock"] },
  { name: "Sing-Along", emoji: "🎤", genres: ["Pop", "Pop Rock", "Pop Punk"] },
  { name: "Power Hour", emoji: "⚡", genres: ["Nu Metal", "Hard Rock", "Metalcore"] },
];

/** Up to MIX_SIZE distinct random track ids from the cohort's genre pool
 *  (partial Fisher–Yates shuffle). If the pool is smaller than MIX_SIZE the
 *  mix just takes what's there — it never mixes in unrelated genres. */
function sampleTrackIds(tracks: CatalogTrack[], genres: string[]): number[] {
  const wanted = new Set(genres.map((g) => g.trim().toLowerCase()));
  const pool = tracks.filter((t) => t.genre != null && wanted.has(t.genre.trim().toLowerCase()));
  const copy = [...pool];
  const out: number[] = [];
  const n = Math.min(MIX_SIZE, copy.length);
  for (let i = 0; i < n; i++) {
    const j = i + Math.floor(Math.random() * (copy.length - i));
    [copy[i], copy[j]] = [copy[j], copy[i]];
    out.push(copy[i].id);
  }
  return out;
}

let sessionMixes: Mix[] | null = null;

export function useMixes(): { mixes: ComputedRef<Mix[]> } {
  const lib = useLibraryStore();
  const mixes = computed<Mix[]>(() => {
    if (sessionMixes) return sessionMixes;
    const tracks = lib.tracks;
    if (tracks.length === 0) return [];
    // Rotate the lineup: partial shuffle of the cohorts, keep the first
    // MIX_COUNT. Same shuffle pattern as the track sampling.
    const selected = [...MIX_DEFS];
    for (let i = 0; i < MIX_COUNT; i++) {
      const j = i + Math.floor(Math.random() * (selected.length - i));
      [selected[i], selected[j]] = [selected[j], selected[i]];
    }
    sessionMixes = selected.slice(0, MIX_COUNT).map((def, i) => ({
      id: i + 1,
      name: def.name,
      emoji: def.emoji,
      trackIds: sampleTrackIds(tracks, def.genres),
    }));
    return sessionMixes;
  });
  return { mixes };
}

/** The session mix with this id, or null (e.g. a deep link after a reload). */
export function findMix(id: number): Mix | null {
  return sessionMixes?.find((m) => m.id === id) ?? null;
}
