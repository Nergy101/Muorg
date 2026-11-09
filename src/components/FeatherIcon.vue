<script setup lang="ts">
import { computed } from "vue";
import MageIcon from "./MageIcon.vue";

const props = defineProps<{
  name: string;
  class?: string;
}>();

/**
 * Compatibility wrapper: the desktop client was written against Feather icon
 * names, but every client now renders the vendored Mage set (see MageIcon).
 * This table is the only place the old Feather names live — an unmapped name
 * falls through to MageIcon, which renders nothing for unknown names.
 */
const MAGE_NAMES: Record<string, string> = {
  "activity": "sound-waves",
  "alert-circle": "exclamation-circle",
  "archive": "archive",
  "arrow-left": "arrow-left",
  "arrow-right": "arrow-right",
  "award": "ribbon",
  "book-open": "book",
  "bookmark": "bookmark",
  "box": "box",
  "briefcase": "briefcase",
  "cast": "screencast",
  "check": "check",
  "check-circle": "check-circle",
  "chevron-down": "chevron-down",
  "chevron-right": "chevron-right",
  "clipboard": "clipboard",
  "clock": "clock",
  "coffee": "cup-hot",
  "command": "keyboard",
  "copy": "copy",
  "corner-up-left": "arrow-up-left",
  "corner-up-right": "arrow-up-right",
  "database": "database",
  "download": "download",
  "edit-2": "edit-pen",
  "feather": "robot",
  "file-text": "note-text",
  "folder": "folder",
  "folder-plus": "folder-plus",
  "github": "github",
  "globe": "globe",
  "grid": "layout-grid",
  "headphones": "headphone-mute",
  "heart": "heart",
  "image": "image",
  "info": "information-circle",
  "layers": "stack",
  "list": "arrowlist",
  "loader": "refresh-reverse",
  "maximize-2": "maximize",
  "minimize-2": "minimize",
  "minus-square": "minus-square",
  "music": "music",
  "paperclip": "attachment",
  "pie-chart": "dashboard-circle-chart",
  "play": "play",
  "plus": "plus",
  "plus-square": "plus-square",
  "refresh-cw": "refresh",
  "repeat": "reload",
  "rotate-ccw": "reload-reverse",
  "save": "save-floppy",
  "search": "search",
  "settings": "settings",
  "shuffle": "exchange-b",
  "skip-back": "previous",
  "skip-forward": "next",
  "square": "select-box",
  "star": "star",
  "trash-2": "trash-2",
  "trending-up": "chart-up",
  "upload": "upload",
  "user": "user",
  "wifi": "wifi",
  "wifi-off": "wifi",
  "wind": "color-swatch",
  "x": "multiply",
  "x-circle": "multiply-circle",
  "zap": "zap",
  "zoom-in": "zoom-in",
  "bar-chart-2": "dashboard-bar",
  "play-circle": "play-circle",
  "sliders": "filter",
  "layout": "layout-grid",
};

const mageName = computed(() => MAGE_NAMES[props.name] ?? props.name);
</script>

<template>
  <MageIcon :name="mageName" :class="props.class" />
</template>
