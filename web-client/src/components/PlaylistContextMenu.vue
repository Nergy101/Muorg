<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="fixed inset-0 z-40"
      @click="onBackdropClick"
      @contextmenu.prevent="close"
    />
    <div
      v-if="visible"
      ref="menuEl"
      class="ctx-menu"
      :style="{ top: y + 'px', left: x + 'px' }"
    >
      <button class="ctx-menu-item" @click.stop="emit('rename'); close()">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
          <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
        </svg>
        Rename
      </button>
      <div class="ctx-menu-separator" />
      <button class="ctx-menu-item danger" @click.stop="emit('delete'); close()">
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
          <polyline points="3 6 5 6 21 6" /><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
        </svg>
        Delete
      </button>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, nextTick } from "vue";

const emit = defineEmits<{ rename: []; delete: [] }>();

const visible = ref(false);
const x = ref(0);
const y = ref(0);
const menuEl = ref<HTMLElement | null>(null);
let openedAt = 0;

function open(event: MouseEvent): void {
  openedAt = Date.now();
  x.value = event.clientX;
  y.value = event.clientY;
  visible.value = true;
  nextTick(() => {
    if (!menuEl.value) return;
    const rect = menuEl.value.getBoundingClientRect();
    if (rect.right > window.innerWidth) x.value = window.innerWidth - rect.width - 8;
    if (rect.bottom > window.innerHeight) y.value = window.innerHeight - rect.height - 8;
  });
}

function onBackdropClick(): void {
  if (Date.now() - openedAt < 300) return;
  close();
}

function close(): void {
  visible.value = false;
}

defineExpose({ open, close });
</script>
