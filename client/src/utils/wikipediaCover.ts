/**
 * Finding an album cover on Wikipedia.
 *
 * Three calls to the MediaWiki API — search for the album's article, list the
 * images on it, resolve one to a URL — plus the heuristic that decides which of
 * those images is the cover. An article's image list is mostly chrome: rating
 * stars, edit pencils, navigation arrows and audio-sample icons, with the cover
 * somewhere among them, so picking the first one gets you a star icon.
 *
 * Extracted from `MetadataEditor.vue` so the scoring can be tested without
 * mounting a 1,700-line form.
 */

const WIKI_API = "https://en.wikipedia.org/w/api.php";

/** Split `File:Some Album cover.jpg` into a comparable name and its extension. */
export function normalizeFileTitle(title: string): { name: string; ext: string } {
  const withoutPrefix = title.replace(/^File:/i, "").trim();
  const lastDot = withoutPrefix.lastIndexOf(".");
  const name = (lastDot >= 0 ? withoutPrefix.slice(0, lastDot) : withoutPrefix)
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "");
  const ext = (lastDot >= 0 ? withoutPrefix.slice(lastDot + 1) : "").toLowerCase();
  return { name, ext };
}

/**
 * How much this filename looks like the album's cover art.
 *
 * Higher is better; negative means "almost certainly page furniture". The
 * strongest signal is the album's own name appearing in the filename, which is
 * how Wikipedia names cover uploads.
 */
export function scoreImageAsAlbumArt(fileTitle: string, albumName: string): number {
  const { name, ext } = normalizeFileTitle(fileTitle);
  const albumNorm = albumName.toLowerCase().replace(/[^a-z0-9]+/g, "");
  let score = 0;
  if (albumNorm && name.includes(albumNorm)) score += 2;
  if (/cover|albumcover|albumart|albumartwork/i.test(fileTitle)) score += 1;
  if (/album/i.test(fileTitle)) score += 0.5;
  // Icons and SVGs are the page's own furniture, never the artwork.
  if (/icon|edit|button|star|arrow|progressive|\.svg$/i.test(fileTitle) || ext === "svg") score -= 2;
  if (ext === "svg") score -= 1;
  if (["jpg", "jpeg", "png", "webp"].includes(ext)) score += 0.5;
  return score;
}

/** The best-scoring `File:` title, or `null` when the page has no images. */
export function pickBestAlbumImage(
  imageTitles: { title: string }[],
  albumName: string,
): string | null {
  if (!imageTitles?.length) return null;
  const scored = imageTitles
    .filter((img) => img.title.startsWith("File:"))
    .map((img) => ({ title: img.title, score: scoreImageAsAlbumArt(img.title, albumName) }));
  scored.sort((a, b) => b.score - a.score);
  return scored[0]?.title ?? null;
}

/** What a lookup can come back with, so the caller can say why nothing happened. */
export type CoverLookup =
  | { ok: true; url: string }
  | { ok: false; reason: string };

/**
 * Look up a cover for `album` by `artist`.
 *
 * `origin: "*"` on every request is what makes these callable from a browser at
 * all — MediaWiki only sends CORS headers for anonymous cross-origin requests
 * that ask for it.
 */
export async function findAlbumCoverUrl(
  album: string,
  artist: string,
  fetchImpl: typeof fetch = fetch,
): Promise<CoverLookup> {
  const albumName = album.trim();
  const artistName = artist.trim();
  const query = [albumName, artistName].filter(Boolean).join(" ") || albumName;
  if (!query) return { ok: false, reason: "Enter an album (or artist) name first." };

  // "(album)" steers the search away from the song or the film of the same name.
  const searchQuery = albumName ? `${albumName} (album)` : query;
  const searchRes = await fetchImpl(
    `${WIKI_API}?${new URLSearchParams({
      action: "query",
      generator: "search",
      gsrsearch: searchQuery,
      gsrlimit: "5",
      format: "json",
      origin: "*",
    })}`,
  );
  const searchData = (await searchRes.json()) as {
    query?: { pages?: Record<string, { pageid: number; title: string; index?: number }> };
  };
  // `generator=search` returns a keyed object, not a ranked array; `index` is
  // the only thing carrying the search order.
  const pages = searchData?.query?.pages;
  const firstPage = pages
    ? Object.values(pages).sort((a, b) => (a.index ?? 99) - (b.index ?? 99))[0]
    : undefined;
  if (!firstPage?.pageid) {
    return { ok: false, reason: "No Wikipedia page found for this album." };
  }

  const imagesRes = await fetchImpl(
    `${WIKI_API}?${new URLSearchParams({
      action: "query",
      pageids: String(firstPage.pageid),
      prop: "images",
      format: "json",
      origin: "*",
    })}`,
  );
  const imagesData = (await imagesRes.json()) as {
    query?: { pages?: Record<string, { images?: { title: string }[] }> };
  };
  const images = imagesData?.query?.pages?.[String(firstPage.pageid)]?.images ?? [];
  const bestTitle = pickBestAlbumImage(images, albumName || query);
  if (!bestTitle) return { ok: false, reason: "No image found on this Wikipedia page." };

  const infoRes = await fetchImpl(
    `${WIKI_API}?${new URLSearchParams({
      action: "query",
      titles: bestTitle,
      prop: "imageinfo",
      iiprop: "url",
      iiurlwidth: "800",
      format: "json",
      origin: "*",
    })}`,
  );
  const infoData = (await infoRes.json()) as {
    query?: { pages?: Record<string, { imageinfo?: { url: string }[] }> };
  };
  const filePage = infoData?.query?.pages && Object.values(infoData.query.pages)[0];
  const url = filePage?.imageinfo?.[0]?.url;
  return url ? { ok: true, url } : { ok: false, reason: "Could not get image URL." };
}
