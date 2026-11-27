<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from "vue";
import { storeToRefs } from "pinia";
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { useCatalogStore } from "../../stores/catalog";
import { useSettingsStore } from "../../stores/settings";
import { back, forward, canBack, canForward, navigate, currentView, type ViewName } from "../../state/navigation";
import { modKeyCombo } from "../../utils/platform";
import ProfileBadge from "./ProfileBadge.vue";

const store = useCatalogStore();
const settings = useSettingsStore();
const { searchQuery } = storeToRefs(store);
const { theme } = storeToRefs(settings);

const searchRef = ref<HTMLInputElement | null>(null);

function onSearchInput(e: Event) {
  const q = (e.target as HTMLInputElement).value;
  store.setSearchQuery(q);
  if (q.trim()) navigate({ name: "search" });
}

function onKeydown(e: KeyboardEvent) {
  if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
    e.preventDefault();
    searchRef.value?.focus();
    searchRef.value?.select();
  }
}

onMounted(() => document.addEventListener("keydown", onKeydown));
onUnmounted(() => document.removeEventListener("keydown", onKeydown));

// 3-switch display control (handover: Albums / List / Artists)
const DISPLAY_SWITCHES: { id: ViewName; icon: string; label: string }[] = [
  { id: "albums", icon: "disc", label: "Albums" },
  { id: "list", icon: "list", label: "List" },
  { id: "artists", icon: "user", label: "Artists" },
];
const DISPLAY_IDS = ["albums", "list", "artists"] as const;
const displayActive = computed(() => {
  // Only on the Library main pages — hidden on album/artist detail pages etc.
  const n = currentView.value.name;
  return (DISPLAY_IDS as readonly string[]).includes(n) ? n : null;
});

const isDark = () => {
  const t = theme.value;
  if (t === "dark" || t === "doom") return true;
  if (t === "light" || t === "orkish") return false;
  return window.matchMedia("(prefers-color-scheme: dark)").matches;
};

function toggleTheme() {
  settings.setTheme(isDark() ? "light" : "dark");
}
</script>

<template>
  <header class="topbar">
    <button type="button" class="topbar-pill-btn" :disabled="!canBack" aria-label="Back" @click="back()">
      <FeatherIcon name="chevron-left" class="h-4 w-4" />
    </button>
    <button type="button" class="topbar-pill-btn" :disabled="!canForward" aria-label="Forward" @click="forward()">
      <FeatherIcon name="chevron-right" class="h-4 w-4" />
    </button>

    <div class="topbar-search-wrap">
      <FeatherIcon name="search" class="topbar-search-icon h-4 w-4" />
      <input
        ref="searchRef"
        class="topbar-search"
        type="text"
        :placeholder="`Search tracks, albums, artists…  (${modKeyCombo('K')})`"
        :value="searchQuery"
        @input="onSearchInput"
        @keydown.enter="settings.addSearch(searchQuery)"
      />
      <button
        v-if="searchQuery"
        type="button"
        class="topbar-search-clear"
        aria-label="Clear search"
        @click="store.setSearchQuery('')"
      >
        <FeatherIcon name="x" class="h-3.5 w-3.5" />
      </button>
    </div>

    <!-- Display 3-switch: Albums / List / Artists (left of the theme switcher).
         Only on the Library pages. -->
    <div v-if="displayActive" class="seg-switch" role="group" aria-label="Display mode">
      <button
        v-for="s in DISPLAY_SWITCHES"
        :key="s.id"
        type="button"
        :class="{ active: displayActive === s.id }"
        :title="s.label"
        :aria-label="s.label"
        :aria-pressed="displayActive === s.id"
        @click="navigate({ name: s.id })"
      >
        <FeatherIcon :name="s.icon" class="h-4 w-4" />
      </button>
    </div>

    <!-- NER-326: active listening profile in the navigation bar. -->
    <ProfileBadge />

    <button type="button" class="topbar-pill-btn" :title="isDark() ? 'Switch to light theme' : 'Switch to dark theme'" @click="toggleTheme">
      <FeatherIcon :name="isDark() ? 'sun' : 'moon'" class="h-4 w-4" />
    </button>
  </header>
</template>
