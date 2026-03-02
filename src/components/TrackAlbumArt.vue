<script setup lang="ts">
import { computed, onMounted } from "vue";
import { useCatalogStore } from "../stores/catalog";

const props = defineProps<{ path: string }>();
const store = useCatalogStore();

const cover = computed(() => store.getCover(props.path));

onMounted(() => {
  store.fetchCover(props.path);
});
</script>

<template>
  <div class="flex h-8 w-8 shrink-0 items-center justify-center overflow-hidden rounded bg-stone-800">
    <img
      v-if="cover"
      :src="`data:image/jpeg;base64,${cover}`"
      alt=""
      class="h-full w-full object-cover"
    />
  </div>
</template>
