<template>
  <nav
    class="safe-bottom flex shrink-0 items-stretch justify-around border-t border-stone-700 bg-stone-900/95 px-2 pt-1 backdrop-blur md:hidden"
    aria-label="Primary"
  >
    <button
      v-for="tab in tabs"
      :key="tab.id"
      type="button"
      class="flex min-h-11 flex-1 flex-col items-center justify-center gap-0.5 rounded-t-lg px-1 py-1 transition-colors active:bg-stone-800/60"
      :class="active === tab.id ? 'text-stone-100' : 'text-stone-500'"
      :aria-label="tab.label"
      @click="emit('select', tab.id)"
    >
      <span
        class="flex items-center gap-1.5 rounded-full px-3.5 py-1.5 transition-colors"
        :class="active === tab.id ? 'border border-accent bg-accent/15 text-stone-100' : 'border border-transparent'"
      >
        <FeatherIcon :name="tab.icon" class="h-4 w-4" />
        <span class="text-xs font-medium">{{ tab.label }}</span>
      </span>
    </button>
  </nav>
</template>

<script setup lang="ts">
import FeatherIcon from "@shared/components/FeatherIcon.vue";

export type MobileTab = "music" | "albums" | "playlists" | "now-playing";

defineProps<{ active: MobileTab }>();

const emit = defineEmits<{ select: [tab: MobileTab] }>();

const tabs: { id: MobileTab; label: string; icon: string }[] = [
  { id: "music", label: "Music", icon: "music" },
  { id: "albums", label: "Albums", icon: "disc" },
  { id: "playlists", label: "Playlists", icon: "layers" },
  { id: "now-playing", label: "Playing", icon: "play-circle" },
];
</script>
