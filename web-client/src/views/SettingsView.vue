<template>
  <div class="absolute inset-0 flex flex-col overflow-hidden bg-background">
    <div ref="scroller" class="content-col-children min-h-0 flex-1 overflow-y-auto pb-[var(--bottom-inset)]">
      <!-- Brand mark at the top, centred -->
      <div class="flex justify-center px-4 pt-6 pb-3">
        <img :src="logoUrl" alt="Muorg" class="h-16 w-16" />
      </div>

      <!-- Reports and statistics sit above the collapsible sections -->
      <button type="button" :class="[ROW, 'w-full text-left']" @click="router.push({ name: 'reports' })">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Reports</p>
          <p class="text-body-sm text-on-surface-variant">
            {{ reportIssues > 0
              ? `${reportIssues} thing${reportIssues === 1 ? "" : "s"} to look at`
              : "Missing tags, duplicates and artwork" }}
          </p>
        </div>
        <MageIcon name="chevron-right" class="h-5 w-5 shrink-0 text-on-surface-variant" />
      </button>
      <button type="button" :class="[ROW, 'w-full text-left']" @click="router.push({ name: 'stats' })">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Library statistics</p>
          <p class="text-body-sm text-on-surface-variant">Genres, top artists, ratings and metadata health</p>
        </div>
        <MageIcon name="chevron-right" class="h-5 w-5 shrink-0 text-on-surface-variant" />
      </button>

      <div class="flex flex-col gap-3 px-3 pb-4 pt-1">

      <!-- ─── Muorg Info (the first section) ───────────────────────── -->
      <SettingsSection
        title="Muorg Info"
        subtitle="Version, install and app details"
        icon="information-circle"
      >
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Version</p>
        </div>
        <button
          v-if="pwaUpdateAvailable"
          type="button"
          class="flex items-center gap-1 px-2 text-label-lg text-primary"
          @click="refreshApp"
        >
          <MageIcon name="reload" class="h-4 w-4" />
          <span>Refresh now</span>
        </button>
        <span class="text-body-md text-on-surface-variant">{{ version }}</span>
      </div>
      <div v-if="canInstall" :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Install app</p>
          <p v-if="isIos" class="text-body-sm text-on-surface-variant">
            Use Share → Add to Home Screen
          </p>
        </div>
        <button type="button" class="flex items-center gap-1 px-2 text-label-lg text-primary" @click="install()">
          <MageIcon name="download" class="h-4 w-4" />
          <span>Install</span>
        </button>
      </div>
      <div class="hidden lg:block"><KeyboardSettingsSection /></div>

      </SettingsSection>

      <!-- ─── Appearance ────────────────────────────────────────────── -->
      <SettingsSection
        title="Appearance"
        subtitle="Theme, accent color and card layout"
        icon="color-swatch"
      >
      <p :class="SUBHEAD">Theme</p>
      <div class="px-4 py-2">
        <p class="pb-2 text-body-lg text-on-surface">Mode</p>
        <SegmentedControl
          :model-value="settings.theme"
          :options="THEME_OPTIONS"
          @update:model-value="settings.setTheme($event as ThemeMode)"
        />
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Use true black</p>
          <p class="text-body-sm text-on-surface-variant">
            OLED-friendly pitch-black background
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.trueBlack)"
          role="switch"
          aria-label="Use true black"
          :data-tip="`True black (OLED) background is ${settings.trueBlack ? 'on' : 'off'} — click to turn ${settings.trueBlack ? 'off' : 'on'}`"
          :aria-checked="settings.trueBlack"
          @click="settings.setTrueBlack(!settings.trueBlack)"
        >
          <span :class="knobClass(settings.trueBlack)" />
        </button>
      </div>
      <div class="px-4 py-2">
        <p class="pb-2 text-body-lg text-on-surface">Accent color</p>
        <div class="flex flex-wrap gap-2.5">
          <button
            v-for="opt in ACCENT_OPTIONS"
            :key="opt.value"
            type="button"
            class="flex h-9 w-9 items-center justify-center rounded-full transition-transform hover:scale-110"
            :class="[
              settings.accent === opt.value ? 'ring-2 ring-on-surface ring-offset-2 ring-offset-background' : '',
              opt.color ? '' : 'glass-field',
            ]"
            :style="opt.color ? { backgroundColor: opt.color } : undefined"
            :aria-label="opt.label"
            :aria-pressed="settings.accent === opt.value"
            @click="settings.setAccent(opt.value)"
          >
            <MageIcon
              v-if="settings.accent === opt.value"
              :name="opt.color ? 'check' : 'color-swatch'"
              class="h-4 w-4 text-on-surface"
            />
          </button>
        </div>
      </div>
      <p :class="SUBHEAD">Library grid</p>
      <div class="px-4 py-2">
        <p class="pb-2 text-body-lg text-on-surface">Layout</p>
        <SegmentedControl
          :model-value="settings.albumViewStyle"
          :options="LAYOUT_OPTIONS"
          @update:model-value="settings.setAlbumViewStyle($event as LibraryViewStyle)"
        />
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Square Album Cards</p>
          <p class="text-body-sm text-on-surface-variant">
            {{ settings.squareAlbumCards ? "Square corners" : "Rounded corners" }}
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.squareAlbumCards)"
          role="switch"
          aria-label="Square Album Cards"
          :aria-checked="settings.squareAlbumCards"
          @click="settings.setSquareAlbumCards(!settings.squareAlbumCards)"
        >
          <span :class="knobClass(settings.squareAlbumCards)" />
        </button>
      </div>
      <div class="px-4 py-2">
        <p class="pb-2 text-body-lg text-on-surface">Album title and artist</p>
        <SegmentedControl
          :model-value="settings.albumCardCaption"
          :options="ALBUM_CARD_CAPTION_OPTIONS"
          @update:model-value="settings.setAlbumCardCaption($event as AlbumCardCaptionMode)"
        />
      </div>
      <p :class="SUBHEAD">Tracks table</p>
      <LibraryTableSettingsSection />
      <p :class="SUBHEAD">Player</p>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Square cover in player</p>
          <p class="text-body-sm text-on-surface-variant">
            {{ settings.squarePlayerCover ? "Square corners" : "Rounded corners" }}
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.squarePlayerCover)"
          role="switch"
          aria-label="Square cover in player"
          :aria-checked="settings.squarePlayerCover"
          @click="settings.setSquarePlayerCover(!settings.squarePlayerCover)"
        >
          <span :class="knobClass(settings.squarePlayerCover)" />
        </button>
      </div>
      <p :class="SUBHEAD">Motion</p>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Reduce motion</p>
          <p class="text-body-sm text-on-surface-variant">
            Keep navigation, skip the slide and fade travel
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.reduceMotion)"
          role="switch"
          aria-label="Reduce motion"
          :data-tip="`Reduce motion is ${settings.reduceMotion ? 'on' : 'off'} — click to turn ${settings.reduceMotion ? 'off' : 'on'}`"
          :aria-checked="settings.reduceMotion"
          @click="settings.setReduceMotion(!settings.reduceMotion)"
        >
          <span :class="knobClass(settings.reduceMotion)" />
        </button>
      </div>

      </SettingsSection>

      <!-- ─── Playback ───────────────────────────────────────────────── -->
      <SettingsSection
        title="Playback"
        subtitle="Queue behavior, mini player and audio"
        icon="play"
      >
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Continuous playback</p>
          <p class="text-body-sm text-on-surface-variant">
            Automatically play next track when queue ends
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.continuousPlayback)"
          role="switch"
          aria-label="Continuous playback"
          :data-tip="`Continuous playback is ${settings.continuousPlayback ? 'on' : 'off'} — click to turn ${settings.continuousPlayback ? 'off' : 'on'}`"
          :aria-checked="settings.continuousPlayback"
          @click="settings.setContinuousPlayback(!settings.continuousPlayback)"
        >
          <span :class="knobClass(settings.continuousPlayback)" />
        </button>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Tap mini player</p>
          <p class="text-body-sm text-on-surface-variant">
            {{ settings.miniPlayerTapOpensPlayer ? "Opens full player screen" : "Plays / pauses" }}
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.miniPlayerTapOpensPlayer)"
          role="switch"
          aria-label="Tap mini player opens the full player"
          :data-tip="settings.miniPlayerTapOpensPlayer ? 'Tapping the mini player opens the full player — click to make it play/pause instead' : 'Tapping the mini player plays/pauses — click to make it open the full player'"
          :aria-checked="settings.miniPlayerTapOpensPlayer"
          @click="settings.setMiniPlayerTapOpensPlayer(!settings.miniPlayerTapOpensPlayer)"
        >
          <span :class="knobClass(settings.miniPlayerTapOpensPlayer)" />
        </button>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Sleep timer</p>
          <p class="text-body-sm text-on-surface-variant">
            Show the sleep timer in the full player
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.sleepTimerEnabled)"
          role="switch"
          aria-label="Sleep timer"
          :data-tip="`Sleep timer is ${settings.sleepTimerEnabled ? 'on' : 'off'} — click to turn ${settings.sleepTimerEnabled ? 'off' : 'on'}`"
          :aria-checked="settings.sleepTimerEnabled"
          @click="settings.setSleepTimerEnabled(!settings.sleepTimerEnabled)"
        >
          <span :class="knobClass(settings.sleepTimerEnabled)" />
        </button>
      </div>
      <PlaybackAudioSettingsSection />

      </SettingsSection>

      <!-- ─── Library ────────────────────────────────────────────────── -->
      <SettingsSection
title="Library"
        subtitle="Sorting and result display"
        icon="music">
      <ReportsSettingsSection />
      <p :class="SUBHEAD">Sorting</p>
      <div class="px-4 py-2">
        <p class="pb-2 text-body-lg text-on-surface">Default sort order</p>
        <SegmentedControl
          :model-value="settings.sortMode"
          :options="SORT_OPTIONS"
          @update:model-value="settings.setSortMode($event as SortMode)"
        />
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Sort direction</p>
          <p class="text-body-sm text-on-surface-variant">
            {{ settings.sortAscending ? "Ascending" : "Descending" }}
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.sortAscending)"
          role="switch"
          aria-label="Sort direction"
          :data-tip="settings.sortAscending ? 'Ascending — click to sort descending' : 'Descending — click to sort ascending'"
          :aria-checked="settings.sortAscending"
          @click="settings.setSortAscending(!settings.sortAscending)"
        >
          <span :class="knobClass(settings.sortAscending)" />
        </button>
      </div>
      <div class="px-4 py-2">
        <p class="pb-2 text-body-lg text-on-surface">Groupings default to collapsed or expanded</p>
        <SegmentedControl
          :model-value="tableStore.defaultGroupsExpanded ? 'expanded' : 'collapsed'"
          :options="GROUPING_DEFAULT_OPTIONS"
          @update:model-value="tableStore.setDefaultGroupsExpanded($event === 'expanded')"
        />
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Open on last tab</p>
          <p class="text-body-sm text-on-surface-variant">
            Restore the last visited tab after a reload
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.openOnLastTab)"
          role="switch"
          aria-label="Open on last tab"
          :data-tip="`Reopen on the last visited tab is ${settings.openOnLastTab ? 'on' : 'off'} — click to turn ${settings.openOnLastTab ? 'off' : 'on'}`"
          :aria-checked="settings.openOnLastTab"
          @click="settings.setOpenOnLastTab(!settings.openOnLastTab)"
        >
          <span :class="knobClass(settings.openOnLastTab)" />
        </button>
      </div>

      </SettingsSection>

      <!-- ─── Export & Rockbox ───────────────────────────────────────── -->
      <SettingsSection
        title="Export & Rockbox"
        subtitle="M3U playlist export and Rockbox-friendly covers"
        icon="export-list"
      >
      <ExportSettingsSection />
      </SettingsSection>

      <!-- ─── Smart Transform & Metadata Editing ─────────────────────── -->
      <SettingsSection
        title="Smart Transform & Metadata Editing"
        subtitle="Bulk rename, tag writing and path formats"
        icon="edit"
      >
      <WriteSettingsSection />
      <button type="button" :class="[ROW, 'w-full text-left']" @click="router.push({ name: 'smart-transform' })">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Smart Transform</p>
          <p class="text-body-sm text-on-surface-variant">Bulk-rename files by rewriting a path pattern</p>
        </div>
        <MageIcon name="chevron-right" class="h-5 w-5 shrink-0 text-on-surface-variant" />
      </button>
      </SettingsSection>

      <!-- ─── Offline ────────────────────────────────────────────────── -->
      <SettingsSection
title="Offline"
        subtitle="Downloads and cache"
        icon="download">
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Offline mode</p>
          <p class="text-body-sm text-on-surface-variant">
            No network calls — only downloaded tracks are available
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.offlineMode)"
          role="switch"
          aria-label="Offline mode"
          :data-tip="`Offline mode is ${settings.offlineMode ? 'on' : 'off'} — click to turn ${settings.offlineMode ? 'off' : 'on'}`"
          :aria-checked="settings.offlineMode"
          @click="settings.setOfflineMode(!settings.offlineMode)"
        >
          <span :class="knobClass(settings.offlineMode)" />
        </button>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Automatic caching</p>
          <p class="text-body-sm text-on-surface-variant">
            Automatically cache recently played tracks. Manual playlist downloads work regardless
          </p>
        </div>
        <button
          type="button"
          :class="switchClass(settings.autoCacheEnabled)"
          role="switch"
          aria-label="Automatic caching"
          :data-tip="`Automatic caching is ${settings.autoCacheEnabled ? 'on' : 'off'} — click to turn ${settings.autoCacheEnabled ? 'off' : 'on'}`"
          :aria-checked="settings.autoCacheEnabled"
          @click="settings.setAutoCacheEnabled(!settings.autoCacheEnabled)"
        >
          <span :class="knobClass(settings.autoCacheEnabled)" />
        </button>
      </div>
      <div v-if="settings.autoCacheEnabled" class="px-4 py-2">
        <div class="flex items-center justify-between pb-2">
          <p class="text-body-lg text-on-surface">Max automatic cache size</p>
          <span class="text-body-sm tabular-nums text-on-surface-variant">
            {{ formatBytes(settings.autoCacheMaxBytes) }}
          </span>
        </div>
        <input
          type="range"
          :min="AUTO_CACHE_MIN_BYTES"
          :max="AUTO_CACHE_MAX_BYTES"
          :step="AUTO_CACHE_STEP_BYTES"
          :value="settings.autoCacheMaxBytes"
          aria-label="Max automatic cache size"
          class="w-full"
          @input="settings.setAutoCacheMaxBytes(($event.target as HTMLInputElement).valueAsNumber)"
        />
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Cached tracks</p>
          <p class="text-body-sm text-on-surface-variant">
            Downloaded for offline playback
          </p>
        </div>
        <span class="text-body-md text-on-surface-variant">
          {{ offline.cachedTrackCount }}
        </span>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Storage used</p>
          <p class="text-body-sm text-on-surface-variant">
            Cache Storage quota for downloaded tracks
          </p>
        </div>
        <span class="text-body-md text-on-surface-variant">
          {{ storageUsedLabel }}
        </span>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Downloaded playlists</p>
          <p class="text-body-sm text-on-surface-variant">
            {{ downloadedPlaylistNames || "None" }}
          </p>
        </div>
        <span class="text-body-md text-on-surface-variant">
          {{ offline.downloadedPlaylists.length }}
        </span>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Clear offline cache</p>
          <p class="text-body-sm text-on-surface-variant">
            Deletes every downloaded track from this device
          </p>
        </div>
        <button
          type="button"
          class="px-2 text-label-lg text-error disabled:opacity-60"
          :disabled="offline.cachedTrackCount === 0"
          @click="clearOfflineOpen = true"
        >
          Clear
        </button>
      </div>

      </SettingsSection>

      <!-- ─── Server ─────────────────────────────────────────────────── -->
      <SettingsSection
title="Server"
        subtitle="Connection, folders, device and library stats"
        icon="server">
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Server URL</p>
        </div>
        <span class="min-w-0 truncate text-body-md text-on-surface-variant">{{ serverUrl }}</span>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Device name</p>
          <p class="text-body-sm text-on-surface-variant">Shown in play history on the server</p>
        </div>
        <input
          type="text"
          :value="settings.deviceName"
          autocomplete="off"
          spellcheck="false"
          aria-label="Device name"
          class="w-44 rounded-lg bg-surface px-3 py-1.5 text-right text-body-md text-on-surface outline-none focus:ring-2 focus:ring-primary"
          @change="settings.setDeviceName(($event.target as HTMLInputElement).value)"
        />
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Refresh library</p>
        </div>
        <button
          type="button"
          class="px-2 text-label-lg text-primary disabled:opacity-60"
          :disabled="refreshStatus === 'loading'"
          @click="refresh"
        >{{ refreshLabel }}</button>
      </div>
      <div :class="ROW">
        <div class="min-w-0 flex-1">
          <p class="text-body-lg text-on-surface">Log out</p>
        </div>
        <button type="button" class="px-2 text-label-lg text-error" @click="logoutOpen = true">
          Log out
        </button>
      </div>

      <div :class="ROW">
        <span class="min-w-0 flex-1 text-body-lg text-on-surface">Tracks</span>
        <span class="text-body-md text-on-surface-variant">
          {{ trackCount.toLocaleString() }}
        </span>
      </div>
      <div :class="ROW">
        <span class="min-w-0 flex-1 text-body-lg text-on-surface">Albums</span>
        <span class="text-body-md text-on-surface-variant">
          {{ albumCount.toLocaleString() }}
        </span>
      </div>
      <div :class="ROW">
        <span class="min-w-0 flex-1 text-body-lg text-on-surface">Artists</span>
        <span class="text-body-md text-on-surface-variant">
          {{ artistCount.toLocaleString() }}
        </span>
      </div>
      <div :class="ROW">
        <span class="min-w-0 flex-1 text-body-lg text-on-surface">Total duration</span>
        <span class="text-body-md text-on-surface-variant">{{ totalDurationLabel }}</span>
      </div>

      <p :class="SUBHEAD">Library folders</p>
      <LibraryFoldersSection />
      <p :class="SUBHEAD">Server settings (from config.toml)</p>
      <ServerConfigSection />

      <MaintenanceSection />

      </SettingsSection>

      <!-- ─── Development ─────────────────────────────────────────────── -->
      <SettingsSection
title="Development"
        subtitle="Links and diagnostics"
        icon="wrench">
      <a
        :class="ROW"
        href="https://github.com/Nergy101/Muorg"
        target="_blank"
        rel="noopener noreferrer"
      >
        <div class="flex min-w-0 flex-1 items-center gap-3">
          <MageIcon name="github" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <p class="text-body-lg text-on-surface">See GitHub page</p>
        </div>
      </a>
      <a
        :class="ROW"
        href="https://docs.muorg.nergy.space"
        target="_blank"
        rel="noopener noreferrer"
      >
        <div class="flex min-w-0 flex-1 items-center gap-3">
          <MageIcon name="book" class="h-5 w-5 shrink-0 text-on-surface-variant" />
          <p class="text-body-lg text-on-surface">See Muorg Docs</p>
        </div>
      </a>

      <div class="h-6" />
      </SettingsSection>
    </div>

    <ConfirmDialog
      :open="logoutOpen"
      title="Log out?"
      message="This will clear your server URL and API key. You'll need to reconnect. Your search history, sort order and theme stay on this device."
      confirm-label="Log out"
      danger
      @confirm="onLogout"
      @cancel="logoutOpen = false"
    />

    <ConfirmDialog
      :open="clearOfflineOpen"
      title="Clear offline cache?"
      message="This deletes every downloaded track from this device. Downloaded playlists will need to be downloaded again for offline playback."
      confirm-label="Clear"
      danger
      @confirm="onClearOffline"
      @cancel="clearOfflineOpen = false"
    />
  </div>
</div></template>

<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import MageIcon from "../components/MageIcon.vue";
import SettingsSection from "../components/settings/SettingsSection.vue";
import SegmentedControl from "../components/SegmentedControl.vue";
import ConfirmDialog from "../components/ConfirmDialog.vue";
import ReportsSettingsSection from "../components/settings/ReportsSettingsSection.vue";
import LibraryFoldersSection from "../components/settings/LibraryFoldersSection.vue";
import LibraryTableSettingsSection from "../components/settings/LibraryTableSettingsSection.vue";
import WriteSettingsSection from "../components/settings/WriteSettingsSection.vue";
import MaintenanceSection from "../components/settings/MaintenanceSection.vue";
import ServerConfigSection from "../components/settings/ServerConfigSection.vue";
import ExportSettingsSection from "../components/settings/ExportSettingsSection.vue";
import PlaybackAudioSettingsSection from "../components/settings/PlaybackAudioSettingsSection.vue";
import KeyboardSettingsSection from "../components/settings/KeyboardSettingsSection.vue";
import { pwaUpdateAvailable, refreshApp } from "../composables/usePwaUpdate";
import { getServerUrl } from "../api/client";
import { logout } from "../logout";
import { useLibraryStore } from "../stores/library";
import { usePlaylistStore } from "../stores/playlists";
import { useSettingsStore } from "../stores/settings";
import { useLibraryTableStore } from "../stores/libraryTable";
import { useOfflineStore } from "../stores/offline";
import { useInstallPrompt } from "../composables/useInstallPrompt";
import { useScrollMemory } from "../composables/useScrollMemory";
import { reportCounts } from "@shared/reports";
import logoUrl from "../assets/muorg-logo.svg";
import type { AccentColor, AlbumCardCaptionMode, LibraryViewStyle, SortMode, ThemeMode } from "../types";

const ROW = "flex min-h-14 items-center justify-between gap-4 px-4 py-2";
/** Subsection header inside a settings section, for context on what the
 *  rows below it edit. */
const SUBHEAD = "px-4 pb-1 pt-3 text-label-sm font-semibold uppercase tracking-[0.8px] text-on-surface-variant";

const SORT_OPTIONS: { value: SortMode; label: string }[] = [
  { value: "album", label: "Album" },
  { value: "artist", label: "Artist" },
  { value: "year", label: "Year" },
];
const GROUPING_DEFAULT_OPTIONS = [
  { value: "collapsed", label: "Collapsed" },
  { value: "expanded", label: "Expanded" },
];
const LAYOUT_OPTIONS = [
  { value: "grid", label: "Album covers" },
  { value: "list", label: "Album list" },
  { value: "tracks", label: "Tracks" },
];
const ALBUM_CARD_CAPTION_OPTIONS: { value: AlbumCardCaptionMode; label: string }[] = [
  { value: "overlay", label: "Overlay" },
  { value: "below", label: "Underneath" },
  { value: "hover", label: "Overlay on hover" },
  { value: "none", label: "None" },
];
const THEME_OPTIONS = [
  { value: "dark", label: "Dark" },
  { value: "light", label: "Light" },
  { value: "system", label: "Auto" },
];
/** Swatch colors mirror the accent palettes in style.css. */
const ACCENT_OPTIONS: { value: AccentColor; label: string; color?: string }[] = [
  { value: "dynamic", label: "Dynamic — follows album art" },
  { value: "green", label: "Green", color: "#5b7c32" },
  { value: "blue", label: "Blue", color: "#4a7fc1" },
  { value: "purple", label: "Purple", color: "#7d63b8" },
  { value: "orange", label: "Orange", color: "#b97a2a" },
  { value: "red", label: "Red", color: "#b84a4a" },
  { value: "teal", label: "Teal", color: "#3d8f85" },
];

const router = useRouter();
const route = useRoute();
const lib = useLibraryStore();

// Deep link: arriving with #backup-before-write (e.g. from the "Backups OFF"
// hint on the metadata editors) scrolls the backup toggle into view and
// focuses its switch.
watch(
  () => route.hash,
  async (hash) => {
    if (hash !== "#backup-before-write") return;
    await nextTick();
    const el = document.getElementById("backup-before-write");
    el?.scrollIntoView({ behavior: "smooth", block: "center" });
    el?.querySelector<HTMLElement>('[role="switch"]')?.focus();
  },
  { immediate: true },
);

/** Only the reports that mean something is wrong; see SidebarNav for the same
 *  reasoning — a play-history count next to "Reports" is not a to-do. */
const reportIssues = computed(() => {
  const counts = reportCounts(lib.tracks, settings.missingMetadataFields);
  return counts.missing_metadata + counts.duplicates + counts.missing_album_cover;
});
const playlistStore = usePlaylistStore();
const settings = useSettingsStore();
const tableStore = useLibraryTableStore();
const offline = useOfflineStore();
const { canInstall, isIos, install } = useInstallPrompt();

const version = __APP_VERSION__;
const serverUrl = computed(() => getServerUrl() || "Not configured");

function switchClass(on: boolean): string {
  return `flex h-8 w-[52px] shrink-0 items-center rounded-full px-1 transition-colors duration-200 ${
    on ? "bg-primary" : "bg-surface-variant"
  }`;
}

function knobClass(on: boolean): string {
  return `block h-6 w-6 rounded-full bg-white transition-transform duration-200 ${
    on ? "translate-x-5" : "translate-x-0"
  }`;
}

const scroller = ref<HTMLElement | null>(null);
useScrollMemory(scroller);

// --- Library refresh -------------------------------------------------------

const refreshStatus = ref<"idle" | "loading" | "success" | "error">("idle");

const refreshLabel = computed(() => {
  switch (refreshStatus.value) {
    case "loading":
      return "Refreshing…";
    case "success":
      return "Updated";
    case "error":
      return "Failed";
    default:
      return "Refresh";
  }
});

let refreshTimer: ReturnType<typeof setTimeout> | undefined;

async function refresh(): Promise<void> {
  refreshStatus.value = "loading";
  await lib.loadLibrary();
  refreshStatus.value = lib.error ? "error" : "success";
  clearTimeout(refreshTimer);
  refreshTimer = setTimeout(() => {
    refreshStatus.value = "idle";
    refreshTimer = undefined;
  }, 2000);
}

onUnmounted(() => clearTimeout(refreshTimer));

// --- Stats -----------------------------------------------------------------

// Prefer the server /api/stats totals; fall back to the loaded catalog so the
// counts always show even before the stats round-trip lands (or if it fails).
const trackCount = computed(() => lib.stats?.track_count ?? lib.tracks.length);
const albumCount = computed(() => {
  if (lib.stats != null) return lib.stats.album_count;
  return new Set(lib.tracks.map((t) => lib.keyForTrack(t))).size;
});
const artistCount = computed(() => {
  if (lib.stats != null) return lib.stats.artist_count;
  return new Set(lib.tracks.map((t) => t.album_artist ?? t.artist ?? "")).size;
});

const totalDurationLabel = computed(() => {
  const secs =
    lib.stats?.total_duration_secs ??
    lib.tracks.reduce((s, t) => s + (t.duration_secs ?? 0), 0);
  const h = Math.floor(secs / 3600);
  const m = Math.floor((secs % 3600) / 60);
  return h > 0 ? `${h}h ${m}m` : `${m}m`;
});

// --- Logout ----------------------------------------------------------------

const logoutOpen = ref(false);

async function onLogout(): Promise<void> {
  logoutOpen.value = false;
  await logout();
}

// --- Offline cache ---------------------------------------------------------

const AUTO_CACHE_MIN_BYTES = 256 * 1024 * 1024; // 256 MiB
const AUTO_CACHE_MAX_BYTES = 8 * 1024 * 1024 * 1024; // 8 GiB
const AUTO_CACHE_STEP_BYTES = 256 * 1024 * 1024; // 256 MiB

function formatBytes(bytes: number): string {
  if (bytes <= 0) return "0 MB";
  const mb = bytes / (1024 * 1024);
  if (mb < 1) return `${Math.max(1, Math.round(mb * 10) / 10)} MB`;
  if (mb < 1024) return `${Math.round(mb)} MB`;
  return `${(mb / 1024).toFixed(1)} GB`;
}

const storageUsedLabel = computed(() => formatBytes(offline.cachedBytes));

const downloadedPlaylistNames = computed(() =>
  offline.downloadedPlaylists
    .map((id) => playlistStore.playlists.find((p) => p.id === id)?.name)
    .filter((n): n is string => n != null)
    .join(", "),
);

const clearOfflineOpen = ref(false);

async function onClearOffline(): Promise<void> {
  clearOfflineOpen.value = false;
  await offline.clearAll();
}
</script>
