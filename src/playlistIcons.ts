/**
 * Playlist icon format: `"<lucide-name>:#RRGGBB"` (e.g. `"guitar:#F87171"`),
 * stored in the playlists.icon TEXT column. The server treats the column as
 * an opaque string, so legacy emoji values keep working — they resolve to
 * the default below instead of rendering as emoji.
 */

export const DEFAULT_PLAYLIST_ICON_NAME = "eighth-notes";
export const DEFAULT_PLAYLIST_ICON_COLOR = "#FFFFFF";
export const DEFAULT_PLAYLIST_ICON = `${DEFAULT_PLAYLIST_ICON_NAME}:${DEFAULT_PLAYLIST_ICON_COLOR}`;

/** Icon for the server-side Favorites playlist: a gold star. */
export const FAVORITES_PLAYLIST_ICON = "star:#FFD700";

/** Swatches offered in the playlist icon picker, in display order. */
export const PLAYLIST_ICON_COLORS: readonly string[] = [
  "#FFFFFF",
  "#F87171",
  "#FB923C",
  "#FBBF24",
  "#A3E635",
  "#34D399",
  "#2DD4BF",
  "#22D3EE",
  "#60A5FA",
  "#818CF8",
  "#A78BFA",
  "#E879F9",
  "#F472B6",
  "#FB7185",
  "#D6D3D1",
  "#78716C",
];

export interface ResolvedPlaylistIcon {
  name: string;
  color: string;
}

const ICON_NAME_RE = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const COLOR_RE = /^#[0-9a-fA-F]{6}$/;

export function formatPlaylistIcon(name: string, color: string): string {
  return `${name}:${color}`;
}

/**
 * Parse a stored playlist icon. Anything that is not a well-formed
 * `name:#RRGGBB` pair — including legacy emoji — resolves to the default
 * eighth-notes-in-white icon.
 */
export function resolvePlaylistIcon(icon?: string | null): ResolvedPlaylistIcon {
  if (icon) {
    const sep = icon.indexOf(":");
    if (sep > 0) {
      const name = icon.slice(0, sep);
      const color = icon.slice(sep + 1);
      if (ICON_NAME_RE.test(name) && COLOR_RE.test(color)) {
        return { name, color };
      }
    }
  }
  return { name: DEFAULT_PLAYLIST_ICON_NAME, color: DEFAULT_PLAYLIST_ICON_COLOR };
}

/**
 * Music-themed lucide icons (all present in the canonical set) used for
 * randomly assigning mix icons.
 */
export const MUSIC_ICON_NAMES: readonly string[] = [
  "eighth-note",
  "eighth-notes",
  "half-note",
  "sixteenth-notes",
  "guitar",
  "guitar-electric",
  "drum",
  "drumstick",
  "disc",
  "disc-2",
  "disc-3",
  "disc-album",
  "headphones",
  "speaker",
  "mic",
  "mic-audio-lines",
  "audio-lines",
  "audio-waveform",
  "keyboard-music",
  "list-music",
  "file-music",
  "radio",
];

/** A random music icon in a random palette color, as a stored icon string. */
export function randomMusicIcon(): string {
  const name = MUSIC_ICON_NAMES[Math.floor(Math.random() * MUSIC_ICON_NAMES.length)];
  const color = PLAYLIST_ICON_COLORS[Math.floor(Math.random() * PLAYLIST_ICON_COLORS.length)];
  return formatPlaylistIcon(name, color);
}

/** kebab-case lucide name -> PascalCase key of the `@lucide/vue` icons map. */
export function lucideComponentName(kebab: string): string {
  return kebab
    .split("-")
    .map((part) => part.charAt(0).toUpperCase() + part.slice(1))
    .join("");
}
