import { computed, type ComputedRef } from "vue";
import { useLibraryStore } from "../stores/library";
import type { CatalogTrack } from "../types";

/**
 * Ephemeral "Mixes" for the Home tab: 8 randomly assembled ~20-track
 * playlists, each sampled from genres that fit its name (Gym Fuel is
 * metalcore, Deep Focus is metal + electronic, …). They are never written to
 * the server; opening one renders it via the playlist detail view, and saving
 * goes through the normal New-playlist flow (name + emoji, then the mix's
 * tracks are added to the new playlist).
 *
 * A module-level cache keeps the same 8 mixes stable for the whole session:
 * KeepAlive keeps Home mounted, the mix detail view reads the same list, and
 * opening a mix then going back does not re-roll it under you. A fresh page
 * load re-rolls everything.
 */

export interface Mix {
  id: number;
  name: string;
  emoji: string;
  trackIds: number[];
}

const MIX_SIZE = 20;

/** Genres per mix, chosen to match the library's actual tags (verified against
 *  the live catalog). If a cohort yields fewer than MIX_SIZE tracks the mix
 *  falls back to the full catalog so it never comes up short. */
const MIX_DEFS: { name: string; emoji: string; genres: string[] }[] = [
  { name: "Midnight Drive", emoji: "🌙", genres: ["Electronic", "Lo-Fi", "Indie", "Instrumental"] },
  { name: "Morning Coffee", emoji: "☀️", genres: ["Indie Rock", "Indie", "Pop", "Pop Rock"] },
  { name: "Summer Breeze", emoji: "🏖️", genres: ["Country", "Indie Rock", "Indie", "Pop Rock"] },
  { name: "Gym Fuel", emoji: "🔥", genres: ["Metalcore", "Hardcore", "Nu Metal"] },
  { name: "Rainy Day", emoji: "🌧️", genres: ["Emo", "Post-Hardcore", "Alternative"] },
  { name: "Road Trip", emoji: "🚗", genres: ["Punk Rock", "Pop Punk", "Classic Rock"] },
  { name: "Deep Focus", emoji: "💫", genres: ["Metal", "Electronic", "Progressive Metal", "Instrumental"] },
  { name: "Party Starter", emoji: "🎉", genres: ["Nu Metal", "Pop", "Electronic", "Rap"] },
];

/** MIX_SIZE distinct random track ids from the cohort pool (partial
 *  Fisher–Yates shuffle); falls back to the full catalog when the cohort is
 *  smaller than MIX_SIZE. */
function sampleTrackIds(tracks: CatalogTrack[], genres: string[]): number[] {
  const wanted = new Set(genres.map((g) => g.trim().toLowerCase()));
  const pool = tracks.filter((t) => t.genre != null && wanted.has(t.genre.trim().toLowerCase()));
  const source = pool.length >= MIX_SIZE ? pool : tracks;
  const copy = [...source];
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
    sessionMixes = MIX_DEFS.map((def, i) => ({
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
