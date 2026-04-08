import type { MetadataUpdate } from "../types";

/**
 * Extracts metadata fields from a file path using a format template.
 * Format uses placeholders in angle brackets, e.g. <Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>
 * Path separators (/) in the format define segments; the last N segments of the path are matched
 * against the N segments of the format.
 */

export const PATH_FIELD_MAP: Record<string, string> = {
  artist: "artist",
  album: "album",
  title: "title",
  tracktitle: "title",
  tracknumber: "trackNumber",
  track_number: "trackNumber",
  year: "year",
  genre: "genre",
  albumartist: "albumArtist",
  album_artist: "albumArtist",
  featuring: "featuring",
  discnumber: "discNumber",
  disc_number: "discNumber",
};

/**
 * Tries all templates against a path and returns the extracted fields from whichever
 * template produces the most mapped metadata fields. Returns null if none match.
 */
export function extractBestFromPath(
  templates: string[],
  path: string
): Record<string, string> | null {
  let best: Record<string, string> | null = null;
  let bestScore = -1;
  for (const template of templates) {
    const trimmed = template.trim();
    if (!trimmed) continue;
    const extracted = extractMetadataFromPath(trimmed, path);
    if (!extracted) continue;
    const score = Object.keys(buildUpdateFromExtracted(extracted)).length;
    if (score > bestScore) {
      best = extracted;
      bestScore = score;
    }
  }
  return best;
}

export function buildUpdateFromExtracted(extracted: Record<string, string>): MetadataUpdate {
  const update: MetadataUpdate = {};
  for (const [key, value] of Object.entries(extracted)) {
    const normalized = key.toLowerCase().replace(/_/g, "");
    const field = PATH_FIELD_MAP[normalized] ?? PATH_FIELD_MAP[key.toLowerCase()];
    if (!field || field === "pictureBase64") continue;
    if (field === "trackNumber" || field === "discNumber" || field === "year") {
      const n = value.trim() ? parseInt(value, 10) : NaN;
      if (!Number.isNaN(n)) {
        if (field === "trackNumber") update.track_number = n;
        else if (field === "discNumber") update.disc_number = n;
        else update.year = n;
      }
    } else {
      const s = value ?? "";
      if (field === "title") update.title = s || null;
      else if (field === "artist") update.artist = s || null;
      else if (field === "album") update.album = s || null;
      else if (field === "albumArtist") update.album_artist = s || null;
      else if (field === "featuring") update.featuring = s || null;
      else if (field === "genre") update.genre = s || null;
    }
  }
  return update;
}
function escapeRegex(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** Parse one format segment that may contain multiple placeholders, e.g. "<TrackNumber> - <TrackTitle>.<Format>". */
function parseSegment(formatSegment: string, pathSegment: string): Record<string, string> | null {
  const placeholders = [...formatSegment.matchAll(/<([^>]+)>/g)].map((m) => m[1]);
  if (placeholders.length === 0) return null;
  const parts = formatSegment.split(/(<[^>]+>)/g);
  let regexStr = "";
  for (const part of parts) {
    if (part.startsWith("<") && part.endsWith(">")) {
      regexStr += "(.+?)";
    } else {
      regexStr += escapeRegex(part);
    }
  }
  const re = new RegExp(`^${regexStr}$`);
  const match = pathSegment.match(re);
  if (!match) return null;
  const out: Record<string, string> = {};
  placeholders.forEach((name, i) => {
    out[name] = match[i + 1] ?? "";
  });
  return out;
}

/**
 * Builds a new absolute file path by:
 *  1. Extracting variables from `path` using `originTemplate`
 *  2. Preserving the path prefix (segments before the origin match)
 *  3. Filling `destTemplate` placeholders with those variables
 *
 * Returns null if the origin template doesn't match or the destination has
 * unfilled placeholders (missing vars).
 */
export function buildTransformPath(
  originTemplate: string,
  destTemplate: string,
  path: string,
): string | null {
  const vars = extractMetadataFromPath(originTemplate, path);
  if (!vars) return null;

  const lowerVars: Record<string, string> = {};
  for (const [k, v] of Object.entries(vars)) {
    lowerVars[k.toLowerCase()] = v;
  }

  const originSegmentCount = originTemplate.trim().split("/").filter(Boolean).length;
  const pathNormalized = path.replace(/\\/g, "/");
  const pathSegments = pathNormalized.split("/").filter(Boolean);
  const isAbsolute = pathNormalized.startsWith("/");
  const prefixSegments = pathSegments.slice(0, pathSegments.length - originSegmentCount);
  const prefix = (isAbsolute ? "/" : "") + prefixSegments.join("/");

  const destSegments = destTemplate.trim().split("/").filter(Boolean);
  const filled = destSegments.map((seg) =>
    seg.replace(/<([^>]+)>/g, (_, name) => lowerVars[name.toLowerCase()] ?? `<${name}>`),
  );

  if (filled.some((s) => /<[^>]+>/.test(s))) return null;

  return prefix ? `${prefix}/${filled.join("/")}` : `/${filled.join("/")}`;
}

export function extractMetadataFromPath(
  formatTemplate: string,
  path: string
): Record<string, string> | null {
  const trimmed = formatTemplate.trim();
  if (!trimmed) return null;

  const formatSegments = trimmed.split("/").map((s) => s.trim()).filter(Boolean);
  const pathNormalized = path.replace(/\\/g, "/");
  const pathSegments = pathNormalized.split("/").filter(Boolean);

  if (pathSegments.length < formatSegments.length) return null;

  const pathTail = pathSegments.slice(-formatSegments.length);
  const result: Record<string, string> = {};

  for (let i = 0; i < formatSegments.length; i++) {
    const fmt = formatSegments[i];
    const seg = pathTail[i];
    if (!seg) return null;

    // Only the last '.<text>' in the path segment is the extension; the rest is part of the name.
    const lastDot = seg.lastIndexOf(".");
    const baseName = lastDot >= 0 ? seg.slice(0, lastDot) : seg;
    const extValue = lastDot >= 0 ? seg.slice(lastDot + 1) : "";

    const extPlaceholderMatch = fmt.match(/\.\s*<([^>]+)>\s*$/);
    if (extPlaceholderMatch) {
      const extPlaceholder = extPlaceholderMatch[1];
      const fmtWithoutExt = fmt.slice(0, extPlaceholderMatch.index).trim();
      result[extPlaceholder.toLowerCase() === "ext" ? "Format" : extPlaceholder] = extValue;
      if (!fmtWithoutExt) {
        continue;
      }
      const singlePlaceholder = /^<([^>]+)>$/.exec(fmtWithoutExt);
      if (singlePlaceholder) {
        result[singlePlaceholder[1]] = baseName;
        continue;
      }
      const parsed = parseSegment(fmtWithoutExt, baseName);
      if (!parsed) return null;
      Object.assign(result, parsed);
      continue;
    }

    const singlePlaceholder = /^<([^>]+)>$/.exec(fmt);
    if (singlePlaceholder) {
      result[singlePlaceholder[1]] = seg;
      continue;
    }

    const hasPlaceholders = /<[^>]+>/.test(fmt);
    if (!hasPlaceholders) {
      if (seg !== fmt) return null;
      continue;
    }

    // Use full segment — baseName stripping only applies when the format has an explicit
    // extension placeholder (handled above), so dots in directory names are preserved.
    const parsed = parseSegment(fmt, seg);
    if (!parsed) return null;
    Object.assign(result, parsed);
  }

  return result;
}
