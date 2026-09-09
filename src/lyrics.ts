/**
 * LRC parsing, shared by every client that shows lyrics.
 *
 * `GET /api/tracks/{id}/lyrics` returns the embedded text plus a `sync_format`
 * of `"lrc"` or `"plain"`. When it is `lrc` the text carries `[mm:ss.xx]`
 * timestamps and the player can follow along; when it is `plain` there is
 * nothing to parse and the text is shown as-is.
 */

export interface LrcLine {
  /** Seconds from the start of the track. */
  time: number;
  text: string;
}

/**
 * Parse timestamped lines out of an LRC document, in time order.
 *
 * Lines without a timestamp — the `[ar:]` / `[ti:]` metadata header, blank
 * separators — are dropped, as are timestamps with no text after them.
 */
export function parseLrc(text: string): LrcLine[] {
  const out: LrcLine[] = [];
  for (const raw of text.split(/\r?\n/)) {
    const m = raw.trim().match(/^\[(\d{1,2}):(\d{1,2})(?:\.(\d{1,3}))?\](.*)$/);
    if (!m) continue;
    const mins = parseInt(m[1], 10);
    const secs = parseInt(m[2], 10);
    // `.5` means half a second, `.50` means half a second, `.500` likewise —
    // pad to milliseconds before dividing so all three agree.
    const frac = m[3] ? parseInt(m[3].padEnd(3, "0").slice(0, 3), 10) / 1000 : 0;
    const lineText = m[4].trim();
    if (lineText) out.push({ time: mins * 60 + secs + frac, text: lineText });
  }
  return out.sort((a, b) => a.time - b.time);
}

/**
 * Index of the line that should be highlighted at `positionSecs`, or `-1`
 * before the first timestamp. `lines` must be sorted — `parseLrc` sorts.
 */
export function activeLrcIndex(lines: LrcLine[], positionSecs: number): number {
  let idx = -1;
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].time <= positionSecs) idx = i;
    else break;
  }
  return idx;
}
