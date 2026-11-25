<script setup lang="ts">
import QueueList from "../components/playback/QueueList.vue";
</script>

<template>
  <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
    <div class="flex shrink-0 items-baseline gap-3 px-5 pb-2 pt-4">
      <h1 class="view-title">Queue</h1>
    </div>
    <div class="flex min-h-0 flex-1 flex-col overflow-hidden">
      <QueueList />
    </div>
  </div>
</template>
