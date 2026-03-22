<script setup lang="ts">
import { ref, watch, onMounted, onBeforeUnmount, nextTick } from "vue";
import { Picker } from "emoji-mart";
import data from "@emoji-mart/data";

const props = defineProps<{ open: boolean }>();
const emit = defineEmits<{ pick: [emoji: string] }>();

const containerRef = ref<HTMLElement | null>(null);
let picker: InstanceType<typeof Picker> | null = null;
let themeObserver: MutationObserver | null = null;

function currentEmojiMartTheme(): "dark" | "light" {
  const t = document.documentElement.dataset.theme ?? "dark";
  return t === "light" || t === "orkish" ? "light" : "dark";
}

function mountPicker() {
  if (!containerRef.value || picker) return;
  picker = new Picker({
    data,
    onEmojiSelect: (e: { native: string }) => emit("pick", e.native),
    theme: currentEmojiMartTheme(),
    set: "native",
    previewPosition: "none",
    skinTonePosition: "none",
    dynamicWidth: true,
    maxFrequentRows: 2,
    perLine: 8,
    parent: containerRef.value,
  });
}

function destroyPicker() {
  if (picker) {
    (picker as unknown as { destroy?: () => void }).destroy?.();
    picker = null;
    if (containerRef.value) containerRef.value.innerHTML = "";
  }
}

function remount() {
  destroyPicker();
  nextTick(mountPicker);
}

watch(
  () => props.open,
  (open) => {
    if (open) nextTick(mountPicker);
    else destroyPicker();
  },
);

onMounted(() => {
  if (props.open) nextTick(mountPicker);
  // Remount when app theme changes so emoji-mart base theme and CSS vars update.
  themeObserver = new MutationObserver(() => {
    if (props.open) remount();
  });
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ["data-theme"],
  });
});

onBeforeUnmount(() => {
  destroyPicker();
  themeObserver?.disconnect();
});
</script>

<template>
  <div v-show="open" ref="containerRef" class="emoji-picker-panel" />
</template>

<style>
/* ── Base (dark theme) ──────────────────────────────────────────────────── */
.emoji-picker-panel em-emoji-picker {
  --border-radius: 8px;
  --color-border: #57534e;
  --color-border-over: #78716c;
  --rgb-background: 28, 25, 23;
  --rgb-input: 41, 37, 36;
  --rgb-color: 231, 229, 228;
  --rgb-accent: 91, 124, 50;
  --shadow: 0 8px 32px rgba(0,0,0,0.5), 0 0 0 1px rgba(255,255,255,0.06);
  width: 100%;
  max-height: 320px;
}

/* ── Light theme ────────────────────────────────────────────────────────── */
[data-theme="light"] .emoji-picker-panel em-emoji-picker {
  --color-border: #d6d3d1;
  --color-border-over: #a8a29e;
  --rgb-background: 250, 250, 249;
  --rgb-input: 245, 245, 244;
  --rgb-color: 28, 25, 23;
  --rgb-accent: 77, 124, 44;
  --shadow: 0 4px 16px rgba(0,0,0,0.12), 0 0 0 1px rgba(0,0,0,0.06);
}

/* ── Doom theme ─────────────────────────────────────────────────────────── */
[data-theme="doom"] .emoji-picker-panel em-emoji-picker {
  --color-border: #4a1515;
  --color-border-over: #6b2020;
  --rgb-background: 13, 2, 2;
  --rgb-input: 26, 5, 5;
  --rgb-color: 196, 160, 53;
  --rgb-accent: 139, 0, 0;
  --shadow: 0 8px 32px rgba(0,0,0,0.7), 0 0 0 1px rgba(196,160,53,0.08);
}

/* ── Orkish theme ───────────────────────────────────────────────────────── */
[data-theme="orkish"] .emoji-picker-panel em-emoji-picker {
  --color-border: #c5e1a5;
  --color-border-over: #aed581;
  --rgb-background: 232, 245, 233;
  --rgb-input: 220, 237, 200;
  --rgb-color: 27, 94, 32;
  --rgb-accent: 46, 125, 50;
  --shadow: 0 4px 16px rgba(0,0,0,0.1), 0 0 0 1px rgba(27,94,32,0.1);
}
</style>
