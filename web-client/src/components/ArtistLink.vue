<template>
  <!-- An artist credit that opens the artist page. Rendered as a span with
       role="link" rather than an <a>/<button>: it sits inside clickable cards
       and rows (some of them <button>s), and a click on it must stop there
       instead of also opening the album or playing the track.
       Placeholder credits ("Unknown Artist", "Various Artists", "—") and a
       disabled link render as plain text. -->
  <component
    :is="block ? 'div' : 'span'"
    v-if="linkable"
    role="link"
    tabindex="0"
    class="artist-link cursor-pointer"
    :class="block ? 'min-w-0' : ''"
    :aria-label="`View artist ${name}`"
    @click="onClick"
    @keydown.enter.stop.prevent="open"
  >
    <slot>{{ name }}</slot>
  </component>
  <component :is="block ? 'div' : 'span'" v-else :class="block ? 'min-w-0' : ''">
    <slot>{{ name || fallback }}</slot>
  </component>
</template>

<script setup lang="ts">
import { computed } from "vue";
import { useRouter } from "vue-router";
import { isLinkableArtist } from "../discography";

const props = withDefaults(
  defineProps<{
    name: string | null | undefined;
    /** Shown when there is no credit at all. */
    fallback?: string;
    /** Render as a block (for wrapping a MarqueeText). */
    block?: boolean;
    /** Plain text, e.g. while a list is in selection mode. */
    disabled?: boolean;
  }>(),
  { fallback: "—", block: false, disabled: false },
);

const emit = defineEmits<{ navigate: [name: string] }>();

const router = useRouter();

const linkable = computed(() => !props.disabled && isLinkableArtist(props.name));

/** A modified click (Shift/Ctrl/Cmd) is a selection gesture on the row or
 *  card underneath, so it passes through; a plain click stops here. */
function onClick(e: MouseEvent): void {
  if (e.shiftKey || e.ctrlKey || e.metaKey) return;
  e.stopPropagation();
  e.preventDefault();
  open();
}

function open(): void {
  const name = props.name?.trim();
  if (!name) return;
  emit("navigate", name);
  void router.push({ name: "artist", params: { name } });
}
</script>

<style scoped>
.artist-link {
  text-underline-offset: 2px;
}
@media (hover: hover) {
  .artist-link:hover {
    text-decoration: underline;
  }
}
.artist-link:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 1px;
  border-radius: 2px;
}
</style>
