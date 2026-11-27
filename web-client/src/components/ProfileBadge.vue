<template>
  <!-- NER-326: the active listening profile. Renders nothing at all while
       only the default profile exists — the feature is invisible until a
       second profile is created. Once it is on, this badge makes the active
       profile obvious everywhere it is shown (desktop rail, mobile shell). -->
  <button
    v-if="profilesStore.enabled && profilesStore.activeProfile"
    type="button"
    class="flex max-w-full items-center gap-1.5 rounded-full bg-primary/[0.15] px-2.5 py-1 text-label-sm text-primary transition-colors hover:bg-primary/25"
    :title="`Listening as ${profilesStore.activeProfile.name} — tap to switch profile`"
    :aria-label="`Listening as ${profilesStore.activeProfile.name}. Open profiles in settings.`"
    @click="openSettings"
  >
    <MageIcon name="user" class="h-3.5 w-3.5 shrink-0" />
    <span class="truncate">{{ profilesStore.activeProfile.name }}</span>
  </button>
</template>

<script setup lang="ts">
import MageIcon from "./MageIcon.vue";
import { useRouter } from "vue-router";
import { useProfilesStore } from "../stores/profiles";

const router = useRouter();
const profilesStore = useProfilesStore();

function openSettings(): void {
  void router.push({ name: "settings" });
}
</script>
