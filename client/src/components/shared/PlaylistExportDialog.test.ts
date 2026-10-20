import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { mount, type VueWrapper } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";

// The dialog writes through the Tauri fs plugin and picks the output folder
// through the dialog plugin; both are mocked so the test asserts the write
// call instead of touching the real file system.
vi.mock("@tauri-apps/plugin-fs", () => ({
  writeTextFile: vi.fn(async () => {}),
  readTextFile: vi.fn(async () => ""),
  readFile: vi.fn(async () => new Uint8Array()),
  BaseDirectory: { AppConfig: "AppConfig" },
}));
vi.mock("@tauri-apps/plugin-dialog", () => ({
  open: vi.fn(async () => "/music/exports"),
}));
vi.mock("@tauri-apps/api/path", () => ({
  join: vi.fn(async (...parts: string[]) => parts.join("/")),
  appConfigDir: vi.fn(async () => "/tmp"),
}));
vi.mock("@tauri-apps/api/core", () => ({ invoke: vi.fn(async () => null) }));

// The playlist's track ids come from the API; the tracks themselves are
// resolved from the catalog store.
vi.mock("../../api/playlists", () => ({
  getPlaylistTracks: vi.fn(async () => [1]),
}));

import { writeTextFile } from "@tauri-apps/plugin-fs";
import PlaylistExportDialog from "./PlaylistExportDialog.vue";
import { useCatalogStore } from "../../stores/catalog";
import type { CatalogTrack, Playlist } from "../../types";

const TRACK: CatalogTrack = {
  id: 1,
  path: "/music/Artist/Album/01 - Song.mp3",
  format: "mp3",
  has_cover: false,
  mtime_secs: 0,
  play_count: 0,
  root_id: 1,
  duration_secs: 183,
  artist: "Artist",
  title: "Song",
  album: "Album",
  album_artist: null,
  disc_number: null,
  featuring: null,
  genre: null,
  last_played_at: null,
  rating: null,
  track_number: null,
  year: null,
};

const PLAYLIST: Playlist = {
  id: 5,
  name: "My Playlist",
  track_count: 1,
  icon: null,
  smart_rules: null,
};

/**
 * The export must write the .m3u into the picked output folder through the
 * Tauri fs plugin. A blob-URL download does not work in a desktop webview
 * (no download delegate) and would ignore the chosen folder entirely.
 */
describe("PlaylistExportDialog", () => {
  let wrapper: VueWrapper | null = null;

  beforeEach(() => {
    setActivePinia(createPinia());
    vi.clearAllMocks();
    const catalog = useCatalogStore();
    catalog.roots = ["/music"];
    catalog.tracks = [TRACK];
  });

  afterEach(() => {
    wrapper?.unmount();
    wrapper = null;
    // The dialog teleports to body; drop its DOM so the next test starts clean.
    document.body.innerHTML = "";
  });

  async function mountDialog() {
    wrapper = mount(PlaylistExportDialog, {
      props: { playlist: PLAYLIST, open: true },
      attachTo: document.body,
    });
    // Let the immediate watcher's loadTracks() resolve.
    await new Promise((r) => setTimeout(r, 0));
    return wrapper;
  }

  // The dialog renders inside <Teleport to="body">, so its DOM lives on
  // document.body, not inside the wrapper's own tree.
  function button(label: string): HTMLButtonElement {
    const btn = [...document.body.querySelectorAll("button")].find(
      (b) => b.textContent?.trim() === label,
    );
    if (!btn) throw new Error(`button not found: ${label}`);
    return btn;
  }

  it("writes the .m3u into the picked output folder via the fs plugin", async () => {
    const wrapper = await mountDialog();

    button("Browse").click();
    await new Promise((r) => setTimeout(r, 0));

    button("Export").click();
    await new Promise((r) => setTimeout(r, 0));

    expect(writeTextFile).toHaveBeenCalledTimes(1);
    const [path, content] = vi.mocked(writeTextFile).mock.calls[0];
    expect(path).toBe("/music/exports/My Playlist.m3u");
    expect(content).toContain("#EXTM3U");
    expect(content).toContain("#EXTINF:183,Artist - Song");
    // Paths are relative to a folder next to the music root: ../<root>/…
    expect(content).toContain("../music/Artist/Album/01 - Song.mp3");
    expect(wrapper.emitted("close")).toHaveLength(1);
  });

  it("refuses to export without an output folder", async () => {
    const wrapper = await mountDialog();

    button("Export").click();
    await new Promise((r) => setTimeout(r, 0));

    expect(writeTextFile).not.toHaveBeenCalled();
    expect(document.body.textContent).toContain("Please select an output folder.");
    expect(wrapper.emitted("close")).toBeUndefined();
  });
});
