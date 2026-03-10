/**
 * Extracts metadata fields from a file path using a format template.
 * Format uses placeholders in angle brackets, e.g. <Artist>/<Album>/<TrackNumber> - <TrackTitle>.<Format>
 * Path separators (/) in the format define segments; the last N segments of the path are matched
 * against the N segments of the format.
 */
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
      result[singlePlaceholder[1]] = baseName;
      continue;
    }

    const hasPlaceholders = /<[^>]+>/.test(fmt);
    if (!hasPlaceholders) {
      if (seg !== fmt) return null;
      continue;
    }

    // Use full segment only when format ends with a literal extension (e.g. .mp3); otherwise use baseName so dots in the name stay in the extracted value.
    const endsWithLiteralExt = /\.(?!\s*<[^>]+>\s*$)[^<>*]+$/.test(fmt);
    const pathForMatch = endsWithLiteralExt ? seg : baseName;
    const parsed = parseSegment(fmt, pathForMatch);
    if (!parsed) return null;
    Object.assign(result, parsed);
  }

  return result;
}
