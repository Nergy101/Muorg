// Regenerate every client-side view of the lucide icon set from one pinned
// source of truth: the `lucide-static` npm tarball.
//
//   lucide-static@LUCIDE_STATIC_VERSION (npm)
//            |
//            +--> src/lucideIconNames.ts                        (icon names + search tags, shared by client/ and web-client/)
//            +--> android-client/.../res/drawable/lucide_*.xml  (vector drawables, one per icon)
//            +--> android-client/.../ui/icon/LucideIcon.kt      (name -> R.drawable map + composable)
//
// Run it after bumping LUCIDE_STATIC_VERSION, and commit the result.
//
// Usage:
//   node scripts/sync-lucide-icons.mjs
//   node scripts/sync-lucide-icons.mjs --check   (diff instead of write, for CI)

import { execFileSync } from "node:child_process";
import { Buffer } from "node:buffer";
import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const LUCIDE_STATIC_VERSION = "1.73.0";
const TARBALL_URL = `https://registry.npmjs.org/lucide-static/-/lucide-static-${LUCIDE_STATIC_VERSION}.tgz`;
const CACHE_DIR = join(ROOT, "scripts", ".cache");
const CHECK = process.argv.includes("--check");

// ── Fetch + extract the pinned tarball ──────────────────────────────────────

function fetchPackage() {
  mkdirSync(CACHE_DIR, { recursive: true });
  const tgz = join(CACHE_DIR, `lucide-static-${LUCIDE_STATIC_VERSION}.tgz`);
  const dir = join(CACHE_DIR, `lucide-static-${LUCIDE_STATIC_VERSION}`);
  if (!existsSync(dir)) {
    if (!existsSync(tgz)) {
      execFileSync("curl", ["-sfL", "--max-time", "300", "-o", tgz, TARBALL_URL], {
        stdio: "inherit",
      });
    }
    mkdirSync(dir, { recursive: true });
    execFileSync("tar", ["xzf", tgz, "-C", dir]);
    rmSync(tgz, { force: true });
  }
  return join(dir, "package");
}

// ── SVG -> Android VectorDrawable ───────────────────────────────────────────
// lucide SVGs are flat: a 24x24 root with stroke attrs and only
// path/line/circle/ellipse/rect/polyline/polygon children. Anything else fails
// loudly here rather than shipping a broken drawable.

function attrsOf(tag) {
  const attrs = {};
  for (const m of tag.matchAll(/([a-zA-Z-]+)="([^"]*)"/g)) attrs[m[1]] = m[2];
  return attrs;
}

function num(v) {
  return Number(v);
}

function shapeToPathData(el) {
  const tag = el.match(/^<([a-z]+)/)[1];
  const a = attrsOf(el);
  switch (tag) {
    case "path":
      return a.d;
    case "line":
      return `M${a.x1} ${a.y1}L${a.x2} ${a.y2}`;
    case "circle": {
      const cx = num(a.cx), cy = num(a.cy), r = num(a.r);
      return `M${cx - r} ${cy}a${r} ${r} 0 1 0 ${2 * r} 0a${r} ${r} 0 1 0 ${-2 * r} 0Z`;
    }
    case "ellipse": {
      const cx = num(a.cx), cy = num(a.cy), rx = num(a.rx), ry = num(a.ry);
      return `M${cx - rx} ${cy}a${rx} ${ry} 0 1 0 ${2 * rx} 0a${rx} ${ry} 0 1 0 ${-2 * rx} 0Z`;
    }
    case "rect": {
      const x = num(a.x ?? 0), y = num(a.y ?? 0);
      const w = num(a.width), h = num(a.height);
      const rx = num(a.rx ?? a.ry ?? 0);
      if (rx <= 0) return `M${x} ${y}H${x + w}V${y + h}H${x}Z`;
      const ry = num(a.ry ?? a.rx);
      return (
        `M${x + rx} ${y}H${x + w - rx}a${rx} ${ry} 0 0 1 ${rx} ${ry}` +
        `V${y + h - ry}a${rx} ${ry} 0 0 1 ${-rx} ${ry}` +
        `H${x + rx}a${rx} ${ry} 0 0 1 ${-rx} ${-ry}` +
        `V${y + ry}a${rx} ${ry} 0 0 1 ${rx} ${-ry}Z`
      );
    }
    case "polyline":
    case "polygon": {
      const pts = a.points.trim().split(/[\s,]+/).map(Number);
      let d = `M${pts[0]} ${pts[1]}`;
      for (let i = 2; i < pts.length; i += 2) d += `L${pts[i]} ${pts[i + 1]}`;
      if (tag === "polygon") d += "Z";
      return d;
    }
    default:
      throw new Error(`unsupported SVG element <${tag}>`);
  }
}

function svgToVectorDrawable(svg) {
  const root = svg.match(/<svg[^>]*>/)[0];
  const rootAttrs = attrsOf(root);
  const viewBox = (rootAttrs.viewBox ?? "0 0 24 24").split(/\s+/).map(Number);
  const strokeWidth = rootAttrs["stroke-width"] ?? "2";
  const lineCap = rootAttrs["stroke-linecap"] ?? "round";
  const lineJoin = rootAttrs["stroke-linejoin"] ?? "round";
  const body = svg.slice(svg.indexOf(">") + 1, svg.lastIndexOf("</svg>"));
  const paths = [];
  for (const m of body.matchAll(/<(path|line|circle|ellipse|rect|polyline|polygon)\b[^>]*\/?>/g)) {
    const el = m[0];
    const a = attrsOf(el);
    const fill = a.fill && a.fill !== "none" ? "#FF000000" : "#00000000";
    paths.push(
      `    <path android:pathData="${shapeToPathData(el)}" android:fillColor="${fill}"` +
        ` android:strokeColor="#FF000000" android:strokeWidth="${a["stroke-width"] ?? strokeWidth}"` +
        ` android:strokeLineCap="${lineCap}" android:strokeLineJoin="${lineJoin}"/>`,
    );
  }
  if (!paths.length) throw new Error("no drawable elements found");
  return (
    `<vector xmlns:android="http://schemas.android.com/apk/res/android"` +
    ` android:width="24dp" android:height="24dp"` +
    ` android:viewportWidth="${viewBox[2]}" android:viewportHeight="${viewBox[3]}">\n` +
    paths.join("\n") +
    `\n</vector>\n`
  );
}

// ── Generators ──────────────────────────────────────────────────────────────

function generateTsCatalog(pkgDir, names, tags) {
  const lines = [
    "// Generated by scripts/sync-lucide-icons.mjs — do not edit by hand.",
    `// Source: lucide-static@${LUCIDE_STATIC_VERSION} (ISC license, https://lucide.dev).`,
    "",
    "/** Every lucide icon name (kebab-case), sorted. */",
    "export const LUCIDE_ICON_NAMES: readonly string[] = [",
    ...names.map((n) => `  "${n}",`),
    "];",
    "",
    "/** Search tags per icon name (subset of LUCIDE_ICON_NAMES; icons without tags are absent). */",
    "export const LUCIDE_ICON_TAGS: Readonly<Record<string, readonly string[]>> = {",
    ...Object.keys(tags)
      .sort()
      .map((n) => `  "${n}": [${tags[n].map((t) => `"${t}"`).join(", ")}],`),
    "};",
    "",
  ];
  return lines.join("\n");
}

function generateKotlin(pkgDir, names) {
  const entries = names
    .map((n) => `    "${n}" to R.drawable.lucide_${n.replace(/-/g, "_")},`)
    .join("\n");
  return `package nl.muorg.android.ui.icon

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import nl.muorg.android.R

/**
 * Lucide icons (https://lucide.dev, ISC) — the full set from
 * lucide-static@${LUCIDE_STATIC_VERSION}, converted to Android vector
 * drawables by scripts/sync-lucide-icons.mjs. Call sites use lucide's own
 * kebab-case names so the web picker and this composable read the same.
 *
 * The lookup is a compile-time map rather than \`getIdentifier\`, so a typo is
 * a missing key at runtime — handled below — and never a silently blank icon
 * in a release build where resource names have been stripped.
 */
private val LUCIDE_ICONS: Map<String, Int> = mapOf(
${entries}
)

/** Every lucide icon name (kebab-case), sorted. */
val LUCIDE_ICON_NAMES: List<String> = LUCIDE_ICONS.keys.toList()

/** Resource id for a lucide icon name, falling back to the eighth-notes glyph. */
@DrawableRes
fun lucideIconRes(name: String): Int = LUCIDE_ICONS[name] ?: R.drawable.lucide_eighth_notes

@Composable
fun LucideIcon(
    name: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    contentDescription: String? = null,
) {
    Icon(
        painter = painterResource(lucideIconRes(name)),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
`;
}

// ── Main ────────────────────────────────────────────────────────────────────

function main() {
  const pkgDir = fetchPackage();
  const iconsDir = join(pkgDir, "icons");
  const tags = JSON.parse(readFileSync(join(pkgDir, "tags.json"), "utf8"));
  // tags.json keys are the canonical icon set (2,055 in 1.73.0) — the icons/
  // dir additionally ships deprecated aliases (e.g. music.svg) that the
  // @lucide/vue icons map no longer exports.
  const names = Object.keys(tags).sort();
  for (const n of names) {
    if (!existsSync(join(iconsDir, `${n}.svg`))) {
      throw new Error(`canonical icon ${n} has no SVG in lucide-static`);
    }
  }

  const tsOut = join(ROOT, "src", "lucideIconNames.ts");
  const ktOut = join(
    ROOT,
    "android-client/app/src/main/java/nl/muorg/android/ui/icon/LucideIcon.kt",
  );
  const drawableDir = join(ROOT, "android-client/app/src/main/res/drawable");

  const tsContent = generateTsCatalog(pkgDir, names, tags);
  const ktContent = generateKotlin(pkgDir, names);

  // Vector drawables: rewrite the whole lucide_* set so removed/renamed
  // upstream icons do not linger.
  const drawables = names.map((n) => {
    const svg = readFileSync(join(iconsDir, `${n}.svg`), "utf8");
    return [join(drawableDir, `lucide_${n.replace(/-/g, "_")}.xml`), svgToVectorDrawable(svg)];
  });

  if (CHECK) {
    const stale = [];
    if (!existsSync(tsOut) || readFileSync(tsOut, "utf8") !== tsContent) stale.push("src/lucideIconNames.ts");
    if (!existsSync(ktOut) || readFileSync(ktOut, "utf8") !== ktContent) stale.push("android-client/.../LucideIcon.kt");
    const existing = new Set(
      readdirSync(drawableDir).filter((f) => f.startsWith("lucide_") && f.endsWith(".xml")),
    );
    for (const [file, content] of drawables) {
      const base = file.split("/").pop();
      existing.delete(base);
      if (!existsSync(file) || readFileSync(file, "utf8") !== content) stale.push(`res/drawable/${base}`);
    }
    for (const f of existing) stale.push(`res/drawable/${f} (stale)`);
    if (stale.length) {
      console.error(`lucide icon artifacts out of date (${stale.length} files); run scripts/sync-lucide-icons.mjs`);
      process.exit(1);
    }
    console.log(`lucide icon artifacts up to date (${names.length} icons, lucide-static@${LUCIDE_STATIC_VERSION})`);
    return;
  }

  mkdirSync(drawableDir, { recursive: true });
  for (const f of readdirSync(drawableDir)) {
    if (f.startsWith("lucide_") && f.endsWith(".xml")) rmSync(join(drawableDir, f));
  }
  for (const [file, content] of drawables) writeFileSync(file, content);
  writeFileSync(tsOut, tsContent);
  writeFileSync(ktOut, ktContent);

  const bytes = drawables.reduce((s, [, c]) => s + Buffer.byteLength(c), 0);
  console.log(
    `synced ${names.length} lucide icons (lucide-static@${LUCIDE_STATIC_VERSION}): ` +
      `src/lucideIconNames.ts, LucideIcon.kt, ${drawables.length} vector drawables (${(bytes / 1e6).toFixed(1)} MB raw)`,
  );
}

main();
