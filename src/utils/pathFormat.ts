/**
 * Extracts metadata fields from a file path using a format template.
 * Format uses placeholders in angle brackets, e.g. <Artist>/<Album>/<TrackNumber> - <TrackTitle>.<ext>
 * Path separators (/) in the format define segments; the last N segments of the path are matched
 * against the N segments of the format.
 */
function escapeRegex(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

/** Parse one format segment that may contain multiple placeholders, e.g. "<TrackNumber> - <TrackTitle>.<ext>". */
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

    const parsed = parseSegment(fmt, seg);
    if (!parsed) return null;
    Object.assign(result, parsed);
  }

  return result;
}
