<template>
  <!-- Full-screen queue, pushed from the maximized player. Identical content to
       the /queue page; the difference lives in App.vue, which drops the mini
       player and the bottom nav for this route, so the list owns the whole
       viewport and the bottom safe area. -->
  <QueuePanel safe-bottom />
</template>

<script setup lang="ts">
import QueuePanel from "../components/QueuePanel.vue";
</script>
