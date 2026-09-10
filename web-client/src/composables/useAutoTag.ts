import type { MatchCandidate } from "../types";

/** The metadata fields the edit sheet holds, all as strings. */
export interface EditForm {
  title: string;
  artist: string;
  album: string;
  album_artist: string;
  genre: string;
  year: string;
}

/**
 * Fill an edit form from a MusicBrainz match.
 *
 * Only `title` and `artist` are guaranteed on a candidate; the rest are
 * optional. A candidate that has nothing to say about a field must leave what
 * is already there rather than blanking it — a lookup that clears the genre you
 * just typed is worse than no lookup at all.
 *
 * Returns a new form so the caller decides when to commit it. Nothing here
 * writes to the server: applying a match stages an edit, it does not save one.
 */
export function applyCandidate(form: EditForm, candidate: MatchCandidate): EditForm {
  return {
    ...form,
    title: candidate.title,
    artist: candidate.artist,
    album: candidate.album || form.album,
    album_artist: candidate.album_artist || form.album_artist,
    year: candidate.year != null ? String(candidate.year) : form.year,
  };
}

/** Ranked highest-confidence first, which is the order worth showing. */
export function rankCandidates(candidates: MatchCandidate[]): MatchCandidate[] {
  return [...candidates].sort((a, b) => b.confidence - a.confidence);
}

/**
 * How a confidence score is coloured. Shared thresholds so a score reads the
 * same here as it does in the desktop app.
 */
export function confidenceClass(confidence: number): string {
  if (confidence >= 0.8) return "bg-primary/20 text-primary";
  if (confidence >= 0.5) return "bg-on-surface/10 text-on-surface-variant";
  return "bg-error/15 text-error";
}
