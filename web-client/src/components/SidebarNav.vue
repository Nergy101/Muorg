<template>
  <!-- Desktop navigation rail (lg+). Expanded, the rail sits in the app shell
       as a vertical aside; collapsed it becomes a full-width horizontal top
       bar (App.vue switches the shell to flex-col while collapsed), and views
       teleport their controls into its right end (see collapsedNavTrailing).
       Mobile keeps the bottom nav; the rail is hidden below lg either way. -->
  <SidebarRail />
</template>

<script setup lang="ts">
import SidebarRail from "./SidebarRail.vue";
</script>
