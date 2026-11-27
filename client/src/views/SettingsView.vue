<script setup lang="ts">
/**
 * Settings page, rebuilt after the web-client's settings screen: top links
 * (Reports / Library statistics) plus collapsible sections — Muorg Info,
 * Appearance (theme, accent, card style), Playback, Library, Export & Rockbox,
 * Smart Transform & Metadata Editing, Offline, Server, Development.
 */
import { ref } from "vue";
import { storeToRefs } from "pinia";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import GeneralPanel from "../components/modals/settings/GeneralPanel.vue";
import PlaybackPanel from "../components/modals/settings/PlaybackPanel.vue";
import TablePanel from "../components/modals/settings/TablePanel.vue";
import ExportsPanel from "../components/modals/settings/ExportsPanel.vue";
import SmartTransformPanel from "../components/modals/settings/SmartTransformPanel.vue";
import SmartSuggestionsPanel from "../components/modals/settings/SmartSuggestionsPanel.vue";
import ConnectionPanel from "../components/modals/settings/ConnectionPanel.vue";
import ProfilesPanel from "../components/modals/settings/ProfilesPanel.vue";
import KeyboardPanel from "../components/modals/settings/KeyboardPanel.vue";
import { useSettingsStore, type AccentColor, type CardStyle, type ArtCorners } from "../stores/settings";
import { useCatalogStore } from "../stores/catalog";
import { navigate } from "../state/navigation";
import * as catalogApi from "../api/catalog";

const settings = useSettingsStore();
const store = useCatalogStore();
const { accentColor, cardStyle, artCorners, mixCardsFollowLayout, searchResultsFollowLayout } = storeToRefs(settings);

const ACCENTS: { id: AccentColor; color: string; label: string }[] = [
  { id: "dynamic", color: "conic-gradient(#5b7c32, #7d63b8, #b97a2a, #b84a4a, #3d8f85, #4a7fc1, #5b7c32)", label: "Dynamic (album art)" },
  { id: "green", color: "#5b7c32", label: "Green" },
  { id: "blue", color: "#4a7fc1", label: "Blue" },
  { id: "purple", color: "#7d63b8", label: "Purple" },
  { id: "orange", color: "#b97a2a", label: "Orange" },
  { id: "red", color: "#b84a4a", label: "Red" },
  { id: "teal", color: "#3d8f85", label: "Teal" },
];

const CARD_STYLES: { value: CardStyle; label: string }[] = [
  { value: "overlay", label: "Overlay on cover" },
  { value: "below", label: "Below in card" },
];

const ART_CORNERS: { value: ArtCorners; label: string }[] = [
  { value: "rounded", label: "Rounded corners" },
  { value: "square", label: "Square corners" },
];

const SECTIONS = [
  { id: "info", label: "Muorg Info", sub: "Version, install and app details", icon: "info" },
  { id: "appearance", label: "Appearance", sub: "Theme, accent color and card layout", icon: "color-swatch" },
  { id: "playback", label: "Playback", sub: "Queue behavior, mini player and audio", icon: "play" },
  { id: "library", label: "Library", sub: "Sorting and result display", icon: "disc" },
  { id: "export", label: "Export & Rockbox", sub: "M3U playlist export and Rockbox-friendly covers", icon: "upload" },
  { id: "smart", label: "Smart Transform & Metadata Editing", sub: "Bulk rename, tag writing and path formats", icon: "edit-2" },
  { id: "offline", label: "Offline", sub: "Downloads and cache", icon: "download" },
  { id: "server", label: "Server", sub: "Connection, folders, device and library stats", icon: "server" },
  { id: "profiles", label: "Listening profiles", sub: "Separate play history and stats per profile", icon: "user" },
  { id: "dev", label: "Development", sub: "Links and diagnostics", icon: "wrench" },
] as const;

// All panels collapsed by default; remember which the user opened/closed.
const SECTIONS_KEY = "muorg-settings-sections";
function loadOpenSections(): Record<string, boolean> {
  try {
    const raw = window.localStorage.getItem(SECTIONS_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as unknown;
    if (parsed && typeof parsed === "object") return parsed as Record<string, boolean>;
  } catch { /* ignore */ }
  return {};
}
const openSections = ref<Record<string, boolean>>(loadOpenSections());
function toggle(id: string) {
  openSections.value[id] = !openSections.value[id];
  try {
    window.localStorage.setItem(SECTIONS_KEY, JSON.stringify(openSections.value));
  } catch { /* ignore */ }
}

const clearingCache = ref(false);
const cacheMsg = ref("");
async function clearCache() {
  clearingCache.value = true;
  cacheMsg.value = "";
  try {
    store.clearCoverCache();
    await catalogApi.clearCoverCacheDir();
    cacheMsg.value = "Cover cache cleared.";
  } catch (e) {
    cacheMsg.value = e instanceof Error ? e.message : String(e);
  } finally {
    clearingCache.value = false;
  }
}

const purging = ref(false);
const purgeMsg = ref("");
async function purgeDeleted() {
  purging.value = true;
  purgeMsg.value = "";
  try {
    await catalogApi.purgeDeletedTracks();
    purgeMsg.value = "Deleted tracks purged.";
  } catch (e) {
    purgeMsg.value = e instanceof Error ? e.message : String(e);
  } finally {
    purging.value = false;
  }
}
</script>

<template>
  <div class="app-main-scroll">
    <div class="view-pad">
      <h1 class="view-title mb-5">Settings</h1>

      <!-- Top links -->
      <div class="mb-5 flex flex-col">
        <button type="button" class="flex items-center gap-3 py-2.5 text-left" @click="navigate({ name: 'reports' })">
          <span class="min-w-0 flex-1">
            <span class="block text-[15px] font-semibold text-on-surface">Reports</span>
            <span class="block text-xs text-on-surface-variant">Missing tags, duplicates and artwork</span>
          </span>
          <FeatherIcon name="chevron-right" class="h-4 w-4 text-on-surface-variant" />
        </button>
        <button type="button" class="flex items-center gap-3 py-2.5 text-left" @click="navigate({ name: 'stats' })">
          <span class="min-w-0 flex-1">
            <span class="block text-[15px] font-semibold text-on-surface">Library statistics</span>
            <span class="block text-xs text-on-surface-variant">Top artists, ratings and metadata health</span>
          </span>
          <FeatherIcon name="chevron-right" class="h-4 w-4 text-on-surface-variant" />
        </button>
      </div>

      <!-- Collapsible sections -->
      <div class="flex flex-col gap-2.5">
        <div v-for="s in SECTIONS" :key="s.id" class="overflow-hidden rounded-2xl bg-surface">
          <button
            type="button"
            class="flex w-full items-center gap-3 px-4 py-3 text-left"
            :aria-expanded="!!openSections[s.id]"
            @click="toggle(s.id)"
          >
            <FeatherIcon :name="s.icon" class="h-5 w-5 shrink-0 text-on-primary-container" />
            <span class="min-w-0 flex-1">
              <span class="block text-[14.5px] font-semibold text-on-surface">{{ s.label }}</span>
              <span class="block truncate text-xs text-on-surface-variant">{{ s.sub }}</span>
            </span>
            <FeatherIcon
              name="chevron-down"
              class="h-4 w-4 shrink-0 text-on-surface-variant transition-transform"
              :class="openSections[s.id] ? 'rotate-180' : ''"
            />
          </button>
          <div v-if="openSections[s.id]" class="border-t border-outline/15 px-4 py-4">
            <GeneralPanel v-if="s.id === 'info'" />
            <template v-else-if="s.id === 'appearance'">
              <h3 class="mb-2 text-[13px] font-bold uppercase tracking-wider text-on-surface-variant">Theme</h3>
              <div class="mb-1 flex flex-wrap gap-2">
                <button
                  v-for="t in [{ id: 'auto', label: 'Auto', icon: 'monitor' }, { id: 'dark', label: 'Dark', icon: 'moon' }, { id: 'light', label: 'Light', icon: 'sun' }]"
                  :key="t.id"
                  type="button"
                  class="btn-outline !py-2"
                  :class="{ '!border-primary !text-on-primary-container': settings.theme === t.id }"
                  @click="settings.setTheme(t.id as 'auto' | 'dark' | 'light')"
                >
                  <FeatherIcon :name="t.icon" class="h-4 w-4" /> {{ t.label }}
                </button>
              </div>
              <h3 class="mb-2 mt-5 text-[13px] font-bold uppercase tracking-wider text-on-surface-variant">Accent</h3>
              <div class="flex flex-wrap items-center gap-3">
                <button
                  v-for="a in ACCENTS"
                  :key="a.id"
                  type="button"
                  class="flex items-center gap-2 rounded-full bg-surface-container px-3 py-1.5 text-xs text-on-surface hover:bg-surface-variant"
                  :class="{ '!ring-2 !ring-primary': accentColor === a.id }"
                  :title="a.label"
                  @click="settings.setAccentColor(a.id)"
                >
                  <span class="accent-swatch !h-4 !w-4" :class="{ active: accentColor === a.id }" :style="{ background: a.color }" />
                  {{ a.label }}
                </button>
              </div>
              <h3 class="mb-2 mt-5 text-[13px] font-bold uppercase tracking-wider text-on-surface-variant">Card layout</h3>
              <div class="flex flex-wrap gap-3">
                <button
                  v-for="opt in CARD_STYLES"
                  :key="opt.value"
                  type="button"
                  class="w-44 rounded-xl border-2 p-2 text-left transition-colors"
                  :class="cardStyle === opt.value ? 'border-primary bg-primary/10' : 'border-outline bg-surface hover:bg-surface-variant'"
                  @click="settings.setCardStyle(opt.value)"
                >
                  <!-- Preview: mini card mockup -->
                  <div class="relative mb-1.5 aspect-square w-full overflow-hidden rounded-lg bg-surface-variant">
                    <div class="absolute inset-0 bg-gradient-to-br from-stone-500/60 to-stone-700/60" />
                    <div
                      v-if="opt.value === 'overlay'"
                      class="absolute inset-x-0 bottom-0 h-8 bg-black/40 backdrop-blur-sm"
                    >
                      <div class="px-1.5 pt-1.5">
                        <div class="h-1.5 w-3/4 rounded bg-white/80" />
                        <div class="mt-0.5 h-1 w-1/2 rounded bg-white/50" />
                      </div>
                    </div>
                  </div>
                  <div v-if="opt.value === 'below'" class="mb-1.5 px-0.5">
                    <div class="h-1.5 w-3/4 rounded bg-on-surface/70" />
                    <div class="mt-0.5 h-1 w-1/2 rounded bg-on-surface-variant/60" />
                  </div>
                  <div class="flex items-center gap-1.5 text-xs font-medium text-on-surface">
                    <FeatherIcon v-if="cardStyle === opt.value" name="check-circle" class="h-3.5 w-3.5 text-primary" />
                    {{ opt.label }}
                  </div>
                </button>
              </div>

              <label class="mt-4 flex cursor-pointer items-center gap-2 text-sm text-on-surface">
                <input
                  type="checkbox"
                  class="rounded border-outline"
                  :checked="mixCardsFollowLayout"
                  @change="settings.setMixCardsFollowLayout(($event.target as HTMLInputElement).checked)"
                />
                Mix cards follow the card layout (off = always “Below in card”)
              </label>
              <label class="mt-2 flex cursor-pointer items-center gap-2 text-sm text-on-surface">
                <input
                  type="checkbox"
                  class="rounded border-outline"
                  :checked="searchResultsFollowLayout"
                  @change="settings.setSearchResultsFollowLayout(($event.target as HTMLInputElement).checked)"
                />
                Search results follow the card layout (off = always “Below in card”)
              </label>

              <h3 class="mb-2 mt-5 text-[13px] font-bold uppercase tracking-wider text-on-surface-variant">Album Art</h3>
              <div class="flex flex-wrap gap-3">
                <button
                  v-for="opt in ART_CORNERS"
                  :key="opt.value"
                  type="button"
                  class="w-36 rounded-xl border-2 p-2 text-left transition-colors"
                  :class="artCorners === opt.value ? 'border-primary bg-primary/10' : 'border-outline bg-surface hover:bg-surface-variant'"
                  @click="settings.setArtCorners(opt.value)"
                >
                  <div
                    class="mb-1.5 aspect-square w-full bg-gradient-to-br from-stone-500/60 to-stone-700/60"
                    :class="opt.value === 'square' ? '!rounded-none' : 'rounded-lg'"
                  />
                  <div class="flex items-center gap-1.5 text-xs font-medium text-on-surface">
                    <FeatherIcon v-if="artCorners === opt.value" name="check-circle" class="h-3.5 w-3.5 text-primary" />
                    {{ opt.label }}
                  </div>
                </button>
              </div>
            </template>
            <PlaybackPanel v-else-if="s.id === 'playback'" />
            <TablePanel v-else-if="s.id === 'library'" />
            <ExportsPanel v-else-if="s.id === 'export'" />
            <template v-else-if="s.id === 'smart'">
              <SmartTransformPanel />
              <SmartSuggestionsPanel />
            </template>
            <div v-else-if="s.id === 'offline'">
              <p class="mb-3 text-sm text-on-surface-variant">Covers are cached on disk and in memory.</p>
              <button type="button" class="btn-outline !py-2 !text-xs" :disabled="clearingCache" @click="clearCache">
                {{ clearingCache ? "Clearing…" : "Clear cover cache" }}
              </button>
              <p v-if="cacheMsg" class="mt-2 text-xs text-on-surface-variant">{{ cacheMsg }}</p>
            </div>
            <ConnectionPanel v-else-if="s.id === 'server'" />
            <ProfilesPanel v-else-if="s.id === 'profiles'" />
            <template v-else-if="s.id === 'dev'">
              <KeyboardPanel />
              <h3 class="mb-2 mt-5 text-[13px] font-bold uppercase tracking-wider text-on-surface-variant">Credits</h3>
              <p class="text-sm text-on-surface-variant">
                Muorg — The Music Organizer from Hell. Built with Tauri, Vue and Rust.
                Icons: Mage Icons (Apache-2.0). Logo: the Muorg orc.
              </p>
              <h3 class="mb-2 mt-5 text-[13px] font-bold uppercase tracking-wider text-on-surface-variant">Database cache</h3>
              <button type="button" class="btn-outline !py-2 !text-xs" :disabled="clearingCache" @click="clearCache">
                {{ clearingCache ? "Clearing…" : "Clear cover cache" }}
              </button>
              <p v-if="cacheMsg" class="mt-2 text-xs text-on-surface-variant">{{ cacheMsg }}</p>
              <h3 class="mb-2 mt-5 text-[13px] font-bold uppercase tracking-wider text-on-surface-variant">Removed tracks</h3>
              <p class="mb-2 text-sm text-on-surface-variant">
                Permanently delete tracks that were removed from disk (soft-deleted rows older than 30 days).
              </p>
              <button type="button" class="btn-outline !py-2 !text-xs" :disabled="purging" @click="purgeDeleted">
                {{ purging ? "Purging…" : "Purge deleted tracks" }}
              </button>
              <p v-if="purgeMsg" class="mt-2 text-xs text-on-surface-variant">{{ purgeMsg }}</p>
            </template>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
