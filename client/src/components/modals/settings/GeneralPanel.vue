<script setup lang="ts">
/**
 * General tab: version and updates, cache, the settings file on disk, and the
 * links in the About section.
 *
 * The update-complete dialog and the link tooltip belong to this tab and
 * nothing else, so they live here and render through a Teleport rather than
 * staying behind in the modal shell.
 */
import { onMounted, ref, shallowRef } from "vue";
import { appConfigDir, join } from "@tauri-apps/api/path";
import { check } from "@tauri-apps/plugin-updater";
import type { Update } from "@tauri-apps/plugin-updater";
import { relaunch } from "@tauri-apps/plugin-process";
import { open as openShell } from "@tauri-apps/plugin-shell";
import { storeToRefs } from "pinia";
import * as catalogApi from "../../../api/catalog";
import { useSettingsStore } from "../../../stores/settings";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import packageJson from "../../../../package.json";

const settingsStore = useSettingsStore();
const { navWrap, navFocusFollowsMouse } = storeToRefs(settingsStore);

const appVersion = packageJson.version;

const updateCheckStatus = ref<
  "idle" | "checking" | "up-to-date" | "available" | "error"
>("idle");
const availableUpdate = shallowRef<Update | null>(null);
const updateError = ref<string | null>(null);
const updateDownloadProgress = ref<number | null>(null);
const showUpdateCompleteModal = ref(false);
const updateCompleteVersion = ref("");

const GITHUB_RELEASE_BASE = "https://github.com/Nergy101/Muorg/releases";

function releaseTagUrl(version: string) {
  const stripped = version.startsWith("v") ? version.slice(1) : version;
  return `${GITHUB_RELEASE_BASE}/tag/v${stripped}`;
}

async function checkForUpdates() {
  updateCheckStatus.value = "checking";
  updateError.value = null;
  availableUpdate.value = null;
  try {
    const update = await check();
    if (update) {
      availableUpdate.value = update;
      updateCheckStatus.value = "available";
    } else {
      updateCheckStatus.value = "up-to-date";
    }
  } catch (e) {
    updateError.value = e instanceof Error ? e.message : String(e);
    updateCheckStatus.value = "error";
  }
}

async function installUpdate() {
  const update = availableUpdate.value;
  if (!update) return;
  updateDownloadProgress.value = 0;
  let downloaded = 0;
  let contentLength: number | null = null;
  try {
    await update.downloadAndInstall((event) => {
      if (event.event === "Started" && event.data.contentLength != null) {
        contentLength = event.data.contentLength;
      } else if (event.event === "Progress") {
        downloaded += event.data.chunkLength;
        if (contentLength != null && contentLength > 0) {
          updateDownloadProgress.value = Math.min(
            100,
            Math.round((downloaded / contentLength) * 100),
          );
        }
      } else if (event.event === "Finished") {
        updateDownloadProgress.value = 100;
      }
    });
    updateDownloadProgress.value = null;
    updateCompleteVersion.value = update.version;
    showUpdateCompleteModal.value = true;
  } catch (e) {
    updateError.value = e instanceof Error ? e.message : String(e);
    updateDownloadProgress.value = null;
    updateCheckStatus.value = "error";
  }
}

function closeUpdateCompleteModal() {
  showUpdateCompleteModal.value = false;
  updateCompleteVersion.value = "";
}

function openReleaseUrl(url: string) {
  openShell(url);
}

const linkTooltip = ref<{ text: string; x: number; y: number } | null>(null);
let linkTooltipHideTimeout: ReturnType<typeof setTimeout> | null = null;

function showLinkTooltip(url: string, e: MouseEvent) {
  if (linkTooltipHideTimeout) clearTimeout(linkTooltipHideTimeout);
  const rect = (e.currentTarget as HTMLElement).getBoundingClientRect();
  linkTooltip.value = { text: url, x: rect.left + rect.width / 2, y: rect.top - 8 };
}

function hideLinkTooltip() {
  linkTooltipHideTimeout = setTimeout(() => {
    linkTooltip.value = null;
    linkTooltipHideTimeout = null;
  }, 80);
}

async function restartAfterUpdate() {
  closeUpdateCompleteModal();
  await relaunch();
}

const clearCacheStatus = ref<"idle" | "clearing" | "done" | "error">("idle");
const clearCacheError = ref<string | null>(null);

async function clearCache() {
  clearCacheStatus.value = "clearing";
  clearCacheError.value = null;
  try {
    await catalogApi.clearCache();
    clearCacheStatus.value = "done";
  } catch (e) {
    clearCacheError.value = e instanceof Error ? e.message : String(e);
    clearCacheStatus.value = "error";
  }
}

const settingsFilePath = ref<string | null>(null);
onMounted(async () => {
  try {
    const dir = await appConfigDir();
    settingsFilePath.value = await join(dir, "settings.yml");
  } catch {
    settingsFilePath.value = null;
  }
});

async function copyPathToClipboard(path: string) {
  try {
    await navigator.clipboard.writeText(path);
  } catch {
    // Clipboard access can be denied; copying a path is not worth an error.
  }
}
</script>

<template>
            <div class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="sliders"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                General
              </p>
              <div class="settings-section">
                <p
                  class="mb-2 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="download-cloud"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Updates
                  <span class="ml-auto font-normal text-stone-500">v{{ appVersion }}</span>
                </p>
                <button
                  type="button"
                  class="rounded border border-stone-600 bg-stone-800 px-3 py-1.5 text-xs text-stone-200 hover:bg-stone-700 disabled:opacity-60"
                  :disabled="updateCheckStatus === 'checking'"
                  @click="checkForUpdates"
                >
                  <span v-if="updateCheckStatus === 'checking'">Checking…</span>
                  <span v-else>Check for updates</span>
                </button>
                <div v-if="updateCheckStatus === 'up-to-date'" class="settings-uptodate-notice mt-3 rounded border p-2.5">
                  <p class="text-xs font-medium text-blue-300">Up to date</p>
                  <p class="mt-0.5 text-[11px] text-stone-400">You're running the latest version of Muorg.</p>
                </div>
                <p v-if="updateCheckStatus === 'error'" class="mt-1 text-xs text-amber-400">Check failed</p>
                <p v-if="updateError" class="mt-1 text-xs text-amber-400">
                  {{ updateError }}
                </p>
                <div
                  v-if="availableUpdate"
                  class="settings-update-notice mt-3 rounded border p-2.5"
                >
                  <p class="text-xs font-medium text-stone-200">
                    New version available: {{ availableUpdate.version }}
                  </p>
                  <p class="mt-0.5 text-[11px] text-stone-400">
                    Current version: {{ availableUpdate.currentVersion }}.
                    <button
                      v-if="availableUpdate.body || availableUpdate.date"
                      type="button"
                      class="underline decoration-dotted underline-offset-2 hover:text-stone-200"
                      @click="
                        openReleaseUrl(
                          releaseTagUrl(availableUpdate!.version),
                        )
                      "
                    >
                      View release notes
                    </button>
                  </p>
                  <div class="mt-2 flex items-center gap-3">
                    <button
                      type="button"
                      class="settings-action-btn rounded px-3 py-1.5 text-xs font-medium disabled:cursor-not-allowed disabled:opacity-60"
                      :disabled="updateDownloadProgress !== null"
                      @click="installUpdate"
                    >
                      <span v-if="updateDownloadProgress === null"
                        >Download and install</span
                      >
                      <span v-else
                        >Downloading… {{ updateDownloadProgress }}%</span
                      >
                    </button>
                  </div>
                </div>
              </div>

              <div v-if="settingsFilePath" class="settings-section">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="file"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Settings file
                </p>
                <p class="break-all font-mono text-[11px] text-stone-400">
                  {{ settingsFilePath }}
                </p>
                <div class="mt-1 flex gap-2">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 rounded border border-stone-600 px-2.5 py-1 text-[11px] text-stone-300 hover:bg-stone-700"
                    @click="copyPathToClipboard(settingsFilePath)"
                  >
                    <FeatherIcon name="clipboard" class="h-3 w-3 shrink-0" />
                    Copy path
                  </button>
                  <button
                    type="button"
                    class="rounded border border-stone-600 px-2.5 py-1 text-[11px] text-stone-300 hover:bg-stone-700"
                    @click="openReleaseUrl(`file://${settingsFilePath}`)"
                  >
                    Open in file manager
                  </button>
                </div>
              </div>

              <div class="settings-section">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="database"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Database cache
                </p>
                <p class="mb-2 text-xs text-stone-500">
                  Removed folders are kept in the database temporarily so
                  playlists can be restored if the folder is re-added. Use this
                  to free that space immediately.
                </p>
                <button
                  type="button"
                  class="rounded border border-stone-600 bg-stone-800 px-3 py-1.5 text-xs text-stone-200 hover:bg-stone-700 disabled:cursor-not-allowed disabled:opacity-60"
                  :disabled="clearCacheStatus === 'clearing'"
                  @click="clearCache"
                >
                  <span v-if="clearCacheStatus === 'idle'">Clear cache</span>
                  <span v-else-if="clearCacheStatus === 'clearing'"
                    >Clearing…</span
                  >
                  <span v-else-if="clearCacheStatus === 'done'"
                    >Cache cleared</span
                  >
                  <span v-else-if="clearCacheStatus === 'error'">Failed</span>
                </button>
                <p v-if="clearCacheError" class="mt-1 text-xs text-amber-400">
                  {{ clearCacheError }}
                </p>
              </div>

              <div class="settings-section">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="compass"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Navigation
                </p>
                <label
                  class="flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="navWrap"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setNavWrap(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Wrap keyboard navigation
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, moving past the last item with the keyboard
                  wraps around to the start (and vice versa).
                </p>
                <label
                  class="mt-2 flex cursor-pointer items-center gap-2 text-xs font-medium text-stone-500"
                >
                  <input
                    type="checkbox"
                    :checked="navFocusFollowsMouse"
                    class="rounded border-stone-600"
                    @change="
                      (e) =>
                        settingsStore.setNavFocusFollowsMouse(
                          (e.target as HTMLInputElement).checked,
                        )
                    "
                  />
                  Focus follows mouse
                </label>
                <p class="mt-0.5 text-xs text-stone-500">
                  When enabled, moving the mouse over items also updates the
                  keyboard focus target.
                </p>
              </div>

              <div class="settings-section border-t border-stone-700 pt-4">
                <p
                  class="mb-2 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="heart"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Credits
                </p>
                <p class="text-xs text-stone-500">
                  Made with ❤️ by
                  <button
                    type="button"
                    class="inline-flex items-center gap-1 underline decoration-dotted underline-offset-2 hover:text-stone-300"
                    @click="openReleaseUrl('https://github.com/Nergy101')"
                    @mouseenter="showLinkTooltip('https://github.com/Nergy101', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="user" class="h-3.5 w-3.5 shrink-0" />
                    Nergy101
                  </button>
                </p>
                <div class="mt-2 flex flex-wrap gap-x-4 gap-y-1 text-xs">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 underline decoration-dotted underline-offset-2 text-stone-400 hover:text-stone-300"
                    @click="openReleaseUrl('https://github.com/Nergy101/Muorg')"
                    @mouseenter="showLinkTooltip('https://github.com/Nergy101/Muorg', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="github" class="h-3.5 w-3.5 shrink-0" />
                    GitHub
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 underline decoration-dotted underline-offset-2 text-stone-400 hover:text-stone-300"
                    @click="openReleaseUrl('https://blog.nergy.space/')"
                    @mouseenter="showLinkTooltip('https://blog.nergy.space/', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="book-open" class="h-3.5 w-3.5 shrink-0" />
                    Blog
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 underline decoration-dotted underline-offset-2 text-stone-400 hover:text-stone-300"
                    @click="openReleaseUrl('https://portfolio.nergy.space/')"
                    @mouseenter="showLinkTooltip('https://portfolio.nergy.space/', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="briefcase" class="h-3.5 w-3.5 shrink-0" />
                    Portfolio
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 underline decoration-dotted underline-offset-2 text-stone-400 hover:text-stone-300"
                    @click="openReleaseUrl('https://retroranker.site')"
                    @mouseenter="showLinkTooltip('https://retroranker.site', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="award" class="h-3.5 w-3.5 shrink-0" />
                    RetroRanker
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 underline decoration-dotted underline-offset-2 text-stone-400 hover:text-stone-300"
                    @click="openReleaseUrl('https://ko-fi.com/nergy')"
                    @mouseenter="showLinkTooltip('https://ko-fi.com/nergy', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="coffee" class="h-3.5 w-3.5 shrink-0" />
                    Ko-fi
                  </button>
                </div>
              </div>

              <div class="settings-section mt-3">
                <p class="mb-2 flex items-center gap-2 text-xs font-semibold text-stone-400">
                  <FeatherIcon name="star" class="h-3.5 w-3.5 shrink-0 text-stone-500" />
                  Thanks to…
                </p>
                <div class="flex flex-col items-start gap-1 text-xs text-stone-500">
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 text-left underline decoration-dotted underline-offset-2 hover:text-stone-300"
                    @click="openReleaseUrl('https://feathericons.com')"
                    @mouseenter="showLinkTooltip('https://feathericons.com', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="feather" class="h-3.5 w-3.5 shrink-0" />
                    Feather Icons
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 text-left underline decoration-dotted underline-offset-2 hover:text-stone-300"
                    @click="openReleaseUrl('https://tauri.app')"
                    @mouseenter="showLinkTooltip('https://tauri.app', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="box" class="h-3.5 w-3.5 shrink-0" />
                    Tauri, Vue &amp; Rust
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 text-left underline decoration-dotted underline-offset-2 hover:text-stone-300"
                    @click="openReleaseUrl('https://github.com/pdeljanov/Symphonia')"
                    @mouseenter="showLinkTooltip('https://github.com/pdeljanov/Symphonia', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="headphones" class="h-3.5 w-3.5 shrink-0" />
                    Symphonia
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 text-left underline decoration-dotted underline-offset-2 hover:text-stone-300"
                    @click="openReleaseUrl('https://www.sqlite.org')"
                    @mouseenter="showLinkTooltip('https://www.sqlite.org', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="database" class="h-3.5 w-3.5 shrink-0" />
                    SQLite
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 text-left underline decoration-dotted underline-offset-2 hover:text-stone-300"
                    @click="openReleaseUrl('https://tailwindcss.com')"
                    @mouseenter="showLinkTooltip('https://tailwindcss.com', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="wind" class="h-3.5 w-3.5 shrink-0" />
                    Tailwind CSS
                  </button>
                  <button
                    type="button"
                    class="inline-flex items-center gap-1.5 text-left underline decoration-dotted underline-offset-2 hover:text-stone-300"
                    @click="openReleaseUrl('https://www.rockbox.org')"
                    @mouseenter="showLinkTooltip('https://www.rockbox.org', $event)"
                    @mouseleave="hideLinkTooltip"
                  >
                    <FeatherIcon name="music" class="h-3.5 w-3.5 shrink-0" />
                    Rockbox community
                  </button>
                </div>
              </div>
            </div>


  <Teleport to="body">
    <div
      v-if="linkTooltip"
      class="pointer-events-none fixed z-[500] rounded-lg border border-stone-600 bg-stone-800 px-3 py-2 text-xs text-stone-300 shadow-[0_8px_32px_rgba(0,0,0,0.5),0_0_0_1px_rgba(255,255,255,0.06)] whitespace-nowrap"
      :style="{ left: linkTooltip.x + 'px', top: linkTooltip.y + 'px', transform: 'translate(-50%, -100%)' }"
    >
      {{ linkTooltip.text }}
    </div>
  </Teleport>

  <Teleport to="body">
    <div
      v-if="showUpdateCompleteModal"
      class="fixed inset-0 z-[305] flex items-center justify-center bg-stone-950/70 p-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="update-complete-title"
      @keydown.escape="closeUpdateCompleteModal"
      @click.self="closeUpdateCompleteModal"
    >
      <div
        class="w-full max-w-sm rounded-lg border border-stone-600 bg-stone-800 p-4 shadow-xl"
        @click.stop
      >
        <h2
          id="update-complete-title"
          class="text-sm font-semibold text-stone-200"
        >
          Update installed
        </h2>
        <p class="mt-2 text-xs text-stone-400">
          Version {{ updateCompleteVersion }} has been installed. Restart the
          app to use the new version.
        </p>
        <div class="mt-4 flex justify-end gap-2">
          <button
            type="button"
            class="rounded border border-stone-600 px-3 py-1.5 text-sm text-stone-400 hover:bg-stone-700 hover:text-stone-200"
            @click="closeUpdateCompleteModal"
          >
            Later
          </button>
          <button
            type="button"
            class="settings-action-btn rounded px-3 py-1.5 text-sm font-medium"
            @click="restartAfterUpdate"
          >
            Restart now
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
