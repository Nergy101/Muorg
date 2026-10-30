
/** Matches catalog album identity: normalized album title plus album artist. */
export function albumIdentityKey(album: string | null | undefined, albumArtist: string | null | undefined): string {
  const name = (album || "Unknown Album").trim().toLocaleLowerCase();
  const artist = (albumArtist || "").trim().toLocaleLowerCase();
  return JSON.stringify([name || "Unknown Album", artist]);
}
