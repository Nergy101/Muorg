<template>
  <!-- NER-326: the active listening profile in the top navigation bar. Renders
       nothing while the current library context has only the default profile. -->
  <button
    v-if="profilesStore.enabled && profilesStore.activeProfile"
    type="button"
    class="flex max-w-40 items-center gap-1.5 rounded-full bg-primary/15 px-2.5 py-1 text-xs font-medium text-primary transition-colors hover:bg-primary/25"
    :title="`Listening as ${profilesStore.activeProfile.name} — open Settings to switch`"
    :aria-label="`Listening as ${profilesStore.activeProfile.name}. Open settings.`"
    @click="navigate({ name: 'settings' })"
  >
    <FeatherIcon name="user" class="h-3.5 w-3.5 shrink-0" />
    <span class="truncate">{{ profilesStore.activeProfile.name }}</span>
  </button>
</template>

<script setup lang="ts">
import FeatherIcon from "@shared/components/FeatherIcon.vue";
import { useProfilesStore } from "../../stores/profiles";
import { navigate } from "../../state/navigation";

const profilesStore = useProfilesStore();
</script>
