import type { CatalogTrack } from "./types";

function parseLength(minSec: string): number {
  const [m, s] = minSec.split(":").map(Number);
  return (m ?? 0) * 60 + (s ?? 0);
}

const ALBUM = "A Kiss for the Whole World";
const ARTIST = "Enter Shikari";
const MOCK_ROOT = "/mock";

const MOCK_TRACK_LIST: { title: string; length: string }[] = [
  { title: "A Kiss for the Whole World x", length: "3:31" },
  { title: "(Pls) Set Me on Fire", length: "3:04" },
  { title: "It Hurts", length: "3:18" },
  { title: "Leap into the Lightning", length: "3:04" },
  { title: "Feed Your Soul", length: "1:18" },
  { title: "Dead Wood", length: "3:50" },
  { title: "Jailbreak", length: "3:54" },
  { title: "Bloodshot", length: "3:24" },
  { title: "Bloodshot (Coda)", length: "1:17" },
  { title: "Goldfish", length: "3:20" },
  { title: "Giant Pacific Octopus (I Don't Know You Anymore)", length: "2:37" },
  { title: "Giant Pacific Octopus Swirling Off into Infinity...", length: "1:15" },
];

export const MOCK_ROOTS = [MOCK_ROOT];

/** Path to a real file used to supply album art for mock tracks (e.g. when running with --mock). */
export const MOCK_COVER_SOURCE_PATH =
  "/Users/chris/Documents/Music/Enter Shikari/Albums/2023 - A Kiss for the Whole World/04 - Leap into the Lightning.flac";

export const MOCK_TRACKS: CatalogTrack[] = MOCK_TRACK_LIST.map(({ title, length }, i) => ({
  id: i + 1,
  path: `${MOCK_ROOT}/${String(i + 1).padStart(2, "0")} - ${title}.mp3`,
  root_id: 0,
  title,
  artist: ARTIST,
  album: ALBUM,
  album_artist: ARTIST,
  featuring: null,
  year: 2023,
  genre: null,
  track_number: i + 1,
  disc_number: 1,
  duration_secs: parseLength(length),
  format: "mp3",
  mtime_secs: 0,
  has_cover: true, // cover loaded from MOCK_COVER_SOURCE_PATH at runtime
  rating: null,
}));
