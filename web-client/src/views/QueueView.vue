<template>
  <!-- Tab-level queue: reached from the mini player, so the mini player and the
       bottom nav stay put below it. See PlayerQueueView for the full-screen
       variant pushed from the maximized player. -->
  <QueuePanel />
</template>

<script setup lang="ts">
import QueuePanel from "../components/QueuePanel.vue";
</script>
