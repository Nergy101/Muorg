<script setup lang="ts">
/**
 * Theme tab: colour theme and the player's glow treatment.
 *
 * The glow options are previewed live rather than described, because the
 * difference between them is entirely visual.
 */
import { computed } from "vue";
import { storeToRefs } from "pinia";
import { useSettingsStore } from "../../../stores/settings";
import type { ThemeId, PlayerGlowIntensity, PlayerGlowMode } from "../../../stores/settings";
import { getGlowBlobs, getSimpleGlowBlobs } from "../../../composables/useDominantColor";
import FeatherIcon from "@shared/components/FeatherIcon.vue";

const settingsStore = useSettingsStore();
const { theme, playerGlowIntensity, playerGlowMode } = storeToRefs(settingsStore);

const glowSettingsDisabled = computed(() => playerGlowIntensity.value === "off");

const themeOptions: {
  value: ThemeId;
  label: string;
  description: string;
  swatchClass: string;
}[] = [
  {
    value: "auto",
    label: "Auto",
    description: "Follow system appearance (dark or light).",
    swatchClass: "from-stone-900 via-stone-700 to-stone-400",
  },
  {
    value: "dark",
    label: "Dark",
    description: "High-contrast dark library view.",
    swatchClass: "from-stone-900 via-stone-800 to-stone-600",
  },
  {
    value: "light",
    label: "Light",
    description: "Bright, paper-like library theme.",
    swatchClass: "from-stone-50 via-stone-100 to-stone-300",
  },
  {
    value: "orkish",
    label: "Orkish",
    description: "Parchment-like greenish light theme.",
    swatchClass: "from-lime-800 via-lime-500 to-amber-300",
  },
  {
    value: "doom",
    label: "DOOM",
    description: "High-contrast crimson terminal theme.",
    swatchClass: "from-red-900 via-amber-700 to-yellow-400",
  },
];

const playerGlowOptions: { value: PlayerGlowIntensity; label: string }[] = [
  { value: "off", label: "Off" },
  { value: "subdued", label: "Subdued" },
  { value: "default", label: "Default" },
  { value: "vibrant", label: "Vibrant" },
];

const playerGlowModeOptions: {
  value: PlayerGlowMode;
  label: string;
  description: string;
}[] = [
  {
    value: "dynamic",
    label: "Dynamic",
    description: "Muorg picks the best effect based on the album cover",
  },
  {
    value: "vivid",
    label: "Vivid",
    description: "Always use center-color blobs",
  },
  {
    value: "edge-blur",
    label: "Edge blur",
    description: "Always use blurred art bleeding in from edges",
  },
  {
    value: "bland",
    label: "Bland",
    description: "Always use a subtle soft glow",
  },
];

/** Primary green RGB for vivid glow demo. */
const GLOW_DEMO_RGB = "91,124,50";
/** Light gray for bland glow demo (white/gray cover). */
const GLOW_DEMO_BLAND_RGB = "220,220,220";
/** Very dark tint of the demo color for background (not pure black). */
const GLOW_DEMO_NEAR_BLACK = "rgb(10,14,6)";

const glowDemoBlobs = computed(() => {
  const raw = getGlowBlobs(GLOW_DEMO_RGB, "settings-demo");
  const v = playerGlowIntensity.value;
  if (v === "off") return [];
  const scale = v === "subdued" ? 0.4 : v === "vibrant" ? 1.6 : 1.2;
  return raw.map((b) => ({ ...b, opacity: Math.min(1, b.opacity * scale) }));
});

const blandGlowDemoBlobs = computed(() => {
  const raw = getSimpleGlowBlobs(GLOW_DEMO_BLAND_RGB, "settings-demo-bland");
  if (playerGlowIntensity.value === "off") return [];
  const v = playerGlowIntensity.value;
  const scale = v === "subdued" ? 0.4 : v === "vibrant" ? 1.6 : 1.2;
  return raw.map((b) => ({ ...b, opacity: Math.min(1, b.opacity * scale) }));
});

/** Edge blur demo: bland center + colorful edges. Simulated with a blurred gradient. */
const edgeBlurDemoOpacity = computed(() => {
  if (playerGlowIntensity.value === "off") return 0;
  const v = playerGlowIntensity.value;
  if (v === "subdued") return 0.45;
  if (v === "vibrant") return 0.85;
  return 0.65;
});

function getGlowDemoBlobStyle(blob: {
  cx: number;
  cy: number;
  rx: number;
  ry: number;
  opacity: number;
  rgb: string;
}): Record<string, string> {
  const o = blob.opacity;
  const o2 = (o * 0.6).toFixed(2);
  const o3 = (o * 0.2).toFixed(2);
  const gradient = `radial-gradient(ellipse at center, rgba(${blob.rgb},${o.toFixed(2)}) 0%, rgba(${blob.rgb},${o2}) 25%, rgba(${blob.rgb},${o3}) 45%, rgba(${blob.rgb},0.04) 70%, transparent 90%)`;
  return {
    background: gradient,
    transform: `translate(${blob.cx * 100}%, ${blob.cy * 100}%) translate(-50%, -50%) scale(${blob.rx}, ${blob.ry})`,
    filter: "blur(24px)",
  };
}

</script>

<template>
            <div class="space-y-3">
              <p
                class="flex items-center gap-2 text-xs font-semibold text-stone-400"
              >
                <FeatherIcon
                  name="sun"
                  class="h-3.5 w-3.5 shrink-0 text-stone-500"
                />
                Theme
              </p>
              <div class="settings-section">
                <p class="mb-1 text-xs font-medium text-stone-500">
                  Choose your palette
                </p>
                <div
                  class="mt-2 grid grid-cols-1 gap-2 sm:grid-cols-2 md:grid-cols-3"
                >
                  <button
                    v-for="opt in themeOptions"
                    :key="opt.value"
                    type="button"
                    class="group flex items-center gap-3 rounded-md border px-2.5 py-2 text-left text-xs transition"
                    :class="
                      theme === opt.value
                        ? 'settings-option-card--active shadow-inner'
                        : 'border-stone-600 bg-stone-900/60 hover:border-stone-400 hover:bg-stone-800'
                    "
                    @click="settingsStore.setTheme(opt.value)"
                  >
                    <div
                      class="h-8 w-8 shrink-0 rounded-full bg-gradient-to-br shadow-sm ring-1 ring-black/40"
                      :class="opt.swatchClass"
                      aria-hidden="true"
                    />
                    <div class="min-w-0">
                      <p class="text-xs font-semibold text-stone-100">
                        {{ opt.label }}
                        <span
                          v-if="theme === opt.value"
                          class="ml-1 settings-option-badge rounded px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide"
                        >
                          Active
                        </span>
                      </p>
                      <p class="mt-0.5 text-[11px] text-stone-400 line-clamp-2">
                        {{ opt.description }}
                      </p>
                    </div>
                  </button>
                </div>
                <p class="mt-2 text-[11px] text-stone-500">
                  "Auto" follows your OS preference. "Orkish" uses a
                  parchment-like light theme; "DOOM" is a high-contrast dark
                  theme.
                </p>
              </div>

              <div class="settings-section space-y-2">
                <p
                  class="mb-1 flex items-center gap-2 text-xs font-semibold text-stone-400"
                >
                  <FeatherIcon
                    name="sunrise"
                    class="h-3.5 w-3.5 shrink-0 text-stone-500"
                  />
                  Maximized player glow
                </p>
                <p class="text-[11px] text-stone-500">
                  Colorful blurry shadows behind the album art in fullscreen
                  mode, derived from the cover.
                </p>
                <div class="mt-2 flex flex-wrap gap-2">
                  <button
                    v-for="opt in playerGlowModeOptions"
                    :key="opt.value"
                    type="button"
                    class="flex flex-col gap-0.5 rounded-md border px-2.5 py-2 text-left transition"
                    :class="
                      playerGlowMode === opt.value
                        ? 'settings-option-card--active'
                        : 'border-stone-600 bg-stone-900/60 hover:border-stone-400 hover:bg-stone-800'
                    "
                    @click="settingsStore.setPlayerGlowMode(opt.value)"
                  >
                    <span class="text-xs font-medium text-stone-200">{{
                      opt.label
                    }}</span>
                    <span class="text-[10px] text-stone-500">{{
                      opt.description
                    }}</span>
                  </button>
                </div>
                <div class="mt-2 flex flex-wrap gap-2">
                  <button
                    v-for="opt in playerGlowOptions"
                    :key="opt.value"
                    type="button"
                    class="rounded-md border px-2.5 py-1.5 text-xs font-medium transition"
                    :class="
                      playerGlowIntensity === opt.value
                        ? 'settings-option-card--active'
                        : 'border-stone-600 bg-stone-900/60 hover:border-stone-400 hover:bg-stone-800'
                    "
                    @click="settingsStore.setPlayerGlowIntensity(opt.value)"
                  >
                    {{ opt.label }}
                  </button>
                </div>
                <div
                  v-if="!glowSettingsDisabled && playerGlowMode === 'dynamic'"
                  class="mt-3 space-y-3"
                >
                  <p class="text-[11px] text-stone-500">
                    Muorg picks one of three effects, in order:
                  </p>
                  <ul
                    class="list-inside list-decimal space-y-0.5 text-[11px] text-stone-500"
                  >
                    <li>
                      <strong class="text-stone-400">Vivid</strong> uses the
                      average album cover color if it's not bland.
                    </li>
                    <li>
                      If the average is bland but the edges are colorful, we use
                      <strong class="text-stone-400">Edge blur</strong>.
                    </li>
                    <li>
                      Otherwise, if it's all bland (e.g. a white cover), we do a
                      <strong class="text-stone-400">Bland</strong> soft glow.
                    </li>
                  </ul>
                  <div class="flex flex-wrap gap-4">
                    <div class="space-y-1">
                      <p class="text-[11px] font-medium text-stone-500">
                        Vivid
                      </p>
                      <p class="text-[10px] text-stone-600">
                        Average cover color is vivid: procedural blobs from
                        center
                      </p>
                      <div
                        :key="`vivid-${playerGlowIntensity}`"
                        class="glow-demo-container inline-block rounded-lg border border-stone-600 p-6 shadow-lg"
                        :style="{ backgroundColor: GLOW_DEMO_NEAR_BLACK }"
                      >
                        <div
                          class="relative flex h-48 w-40 flex-col items-center justify-center"
                        >
                          <template v-if="glowDemoBlobs.length">
                            <div
                              v-for="(blob, i) in glowDemoBlobs"
                              :key="`vivid-${i}`"
                              class="pointer-events-none absolute inset-0 origin-top-left"
                              :style="getGlowDemoBlobStyle(blob)"
                            />
                          </template>
                          <div
                            class="relative z-10 flex flex-col items-center gap-1"
                          >
                            <div
                              class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-stone-900 shadow-2xl ring-1 ring-black/40"
                            >
                              <span
                                class="inline-flex items-center justify-center text-xl text-stone-400"
                                aria-hidden="true"
                                >♪</span
                              >
                            </div>
                            <span
                              class="max-w-[140px] truncate text-center text-[11px] font-medium text-stone-300 drop-shadow-md"
                              >Track title</span
                            >
                          </div>
                        </div>
                      </div>
                    </div>
                    <div class="space-y-1">
                      <p class="text-[11px] font-medium text-stone-500">
                        Edge blur
                      </p>
                      <p class="text-[10px] text-stone-600">
                        Average bland, edges vivid: blurred album art
                      </p>
                      <div
                        :key="`edge-${playerGlowIntensity}`"
                        class="glow-demo-container inline-block rounded-lg border border-stone-600 p-6 shadow-lg"
                        :style="{ backgroundColor: GLOW_DEMO_NEAR_BLACK }"
                      >
                        <div
                          class="relative flex h-48 w-40 flex-col items-center justify-center"
                        >
                          <div
                            v-if="edgeBlurDemoOpacity > 0"
                            class="glow-demo-edge-blur pointer-events-none absolute inset-0 flex items-center justify-center"
                            :style="{ opacity: edgeBlurDemoOpacity }"
                          >
                            <div
                              class="h-32 w-32 flex-shrink-0 rounded-lg"
                              style="
                                background: radial-gradient(
                                  ellipse at center,
                                  #d4d4d4 0%,
                                  #3b82f6 25%,
                                  #8b5cf6 45%,
                                  #ec4899 65%,
                                  #f59e0b 85%,
                                  #22c55e 100%
                                );
                                filter: blur(40px);
                              "
                            />
                          </div>
                          <div
                            class="relative z-10 flex flex-col items-center gap-1"
                          >
                            <div
                              class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-stone-900 shadow-2xl ring-1 ring-black/40"
                            >
                              <span
                                class="inline-flex items-center justify-center text-xl text-stone-400"
                                aria-hidden="true"
                                >♪</span
                              >
                            </div>
                            <span
                              class="max-w-[140px] truncate text-center text-[11px] font-medium text-stone-300 drop-shadow-md"
                              >Track title</span
                            >
                          </div>
                        </div>
                      </div>
                    </div>
                    <div class="space-y-1">
                      <p class="text-[11px] font-medium text-stone-500">
                        Bland
                      </p>
                      <p class="text-[10px] text-stone-600">
                        All bland (e.g. white cover): soft glow of that color
                      </p>
                      <div
                        :key="`bland-${playerGlowIntensity}`"
                        class="glow-demo-container inline-block rounded-lg border border-stone-600 p-6 shadow-lg"
                        :style="{ backgroundColor: GLOW_DEMO_NEAR_BLACK }"
                      >
                        <div
                          class="relative flex h-48 w-40 flex-col items-center justify-center"
                        >
                          <template v-if="blandGlowDemoBlobs.length">
                            <div
                              v-for="(blob, i) in blandGlowDemoBlobs"
                              :key="`bland-${i}`"
                              class="pointer-events-none absolute inset-0 origin-top-left"
                              :style="getGlowDemoBlobStyle(blob)"
                            />
                          </template>
                          <div
                            class="relative z-10 flex flex-col items-center gap-1"
                          >
                            <div
                              class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-stone-900 shadow-2xl ring-1 ring-black/40"
                            >
                              <span
                                class="inline-flex items-center justify-center text-xl text-stone-400"
                                aria-hidden="true"
                                >♪</span
                              >
                            </div>
                            <span
                              class="max-w-[140px] truncate text-center text-[11px] font-medium text-stone-300 drop-shadow-md"
                              >Track title</span
                            >
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
                <!-- Single-mode previews when not Dynamic -->
                <div
                  v-else-if="!glowSettingsDisabled && playerGlowMode === 'vivid'"
                  class="mt-3"
                >
                  <div
                    :key="`vivid-preview-${playerGlowIntensity}`"
                    class="glow-demo-container inline-block rounded-lg border border-stone-600 p-6 shadow-lg"
                    :style="{ backgroundColor: GLOW_DEMO_NEAR_BLACK }"
                  >
                    <div class="relative flex h-48 w-40 flex-col items-center justify-center">
                      <template v-if="glowDemoBlobs.length">
                        <div
                          v-for="(blob, i) in glowDemoBlobs"
                          :key="`vivid-p-${i}`"
                          class="pointer-events-none absolute inset-0 origin-top-left"
                          :style="getGlowDemoBlobStyle(blob)"
                        />
                      </template>
                      <div class="relative z-10 flex flex-col items-center gap-1">
                        <div class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-stone-900 shadow-2xl ring-1 ring-black/40">
                          <span class="inline-flex items-center justify-center text-xl text-stone-400" aria-hidden="true">♪</span>
                        </div>
                        <span class="max-w-[140px] truncate text-center text-[11px] font-medium text-stone-300 drop-shadow-md">Track title</span>
                      </div>
                    </div>
                  </div>
                </div>
                <div
                  v-else-if="!glowSettingsDisabled && playerGlowMode === 'edge-blur'"
                  class="mt-3"
                >
                  <div
                    :key="`edge-preview-${playerGlowIntensity}`"
                    class="glow-demo-container inline-block rounded-lg border border-stone-600 p-6 shadow-lg"
                    :style="{ backgroundColor: GLOW_DEMO_NEAR_BLACK }"
                  >
                    <div class="relative flex h-48 w-40 flex-col items-center justify-center">
                      <div
                        v-if="edgeBlurDemoOpacity > 0"
                        class="glow-demo-edge-blur pointer-events-none absolute inset-0 flex items-center justify-center"
                        :style="{ opacity: edgeBlurDemoOpacity }"
                      >
                        <div
                          class="h-32 w-32 flex-shrink-0 rounded-lg"
                          style="background: radial-gradient(ellipse at center, #d4d4d4 0%, #3b82f6 25%, #8b5cf6 45%, #ec4899 65%, #f59e0b 85%, #22c55e 100%); filter: blur(40px);"
                        />
                      </div>
                      <div class="relative z-10 flex flex-col items-center gap-1">
                        <div class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-stone-900 shadow-2xl ring-1 ring-black/40">
                          <span class="inline-flex items-center justify-center text-xl text-stone-400" aria-hidden="true">♪</span>
                        </div>
                        <span class="max-w-[140px] truncate text-center text-[11px] font-medium text-stone-300 drop-shadow-md">Track title</span>
                      </div>
                    </div>
                  </div>
                </div>
                <div
                  v-else-if="!glowSettingsDisabled && playerGlowMode === 'bland'"
                  class="mt-3"
                >
                  <div
                    :key="`bland-preview-${playerGlowIntensity}`"
                    class="glow-demo-container inline-block rounded-lg border border-stone-600 p-6 shadow-lg"
                    :style="{ backgroundColor: GLOW_DEMO_NEAR_BLACK }"
                  >
                    <div class="relative flex h-48 w-40 flex-col items-center justify-center">
                      <template v-if="blandGlowDemoBlobs.length">
                        <div
                          v-for="(blob, i) in blandGlowDemoBlobs"
                          :key="`bland-p-${i}`"
                          class="pointer-events-none absolute inset-0 origin-top-left"
                          :style="getGlowDemoBlobStyle(blob)"
                        />
                      </template>
                      <div class="relative z-10 flex flex-col items-center gap-1">
                        <div class="flex h-16 w-16 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-stone-900 shadow-2xl ring-1 ring-black/40">
                          <span class="inline-flex items-center justify-center text-xl text-stone-400" aria-hidden="true">♪</span>
                        </div>
                        <span class="max-w-[140px] truncate text-center text-[11px] font-medium text-stone-300 drop-shadow-md">Track title</span>
                      </div>
                    </div>
                  </div>
                </div>
                <!-- Glow off: intensity set to Off -->
                <div
                  v-else-if="glowSettingsDisabled"
                  class="mt-3 inline-block overflow-hidden rounded-lg border border-stone-600 shadow-lg opacity-60"
                  :style="{ backgroundColor: GLOW_DEMO_NEAR_BLACK }"
                >
                  <div
                    class="relative flex h-64 w-56 flex-col items-center justify-center"
                  >
                    <div class="relative z-10 flex flex-col items-center gap-2">
                      <div
                        class="flex h-24 w-24 shrink-0 items-center justify-center overflow-hidden rounded-lg bg-stone-900 shadow-2xl ring-1 ring-black/40"
                      >
                        <span
                          class="inline-flex items-center justify-center text-2xl text-stone-500"
                          aria-hidden="true"
                        >
                          ♪
                        </span>
                      </div>
                      <span
                        class="max-w-[180px] truncate text-center text-xs font-medium text-stone-500 drop-shadow-md"
                      >
                        Glow off
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

</template>
