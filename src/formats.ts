/**
 * The server streams MP3 as-is and transcodes every other format to MP3,
 * so a streamed non-MP3 track can only seek by reloading from `?start=`.
 * Mirrors `AudioFormat::needs_transcode` in muorg-core.
 */
export function needsTranscode(format: string | null | undefined): boolean {
  return format?.toLowerCase() !== "mp3";
}

/** Lossless formats get the highlighted badge in track lists. */
export function isLossless(format: string | null | undefined): boolean {
  const f = format?.toLowerCase();
  return f === "flac" || f === "wav";
}
