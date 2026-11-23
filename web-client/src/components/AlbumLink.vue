<template>
  <!-- An album title that opens the album page. Rendered as a span with
       role="link" rather than an <a>/<button>: it sits inside clickable cards
       and rows (some of them <button>s), and a click on it must stop there
       instead of also opening the parent card or playing the track.
       The link only exists on desktop viewports (lg+): on mobile/PWA the
       title renders as plain text, because the small tap target is too
       easy to hit by accident next to the row/card it sits in.
       Placeholder titles ("—", "Unknown Album") and a disabled link render
       as plain text. -->
  <component
    :is="block ? 'div' : 'span'"
    v-if="linkable"
    role="link"
    tabindex="0"
    class="album-link cursor-pointer hidden"
    :class="block ? 'min-w-0 lg:block' : 'lg:inline'"
    :aria-label="`View album ${name}`"
    @click="onClick"
    @keydown.enter.stop.prevent="open"
  >
    <slot>{{ name }}</slot>
  </component>
  <component
    :is="block ? 'div' : 'span'"
    v-if="linkable"
    class="lg:hidden"
    :class="block ? 'min-w-0' : ''"
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
import { isLinkableAlbum } from "../discography";

const props = withDefaults(
  defineProps<{
    name: string | null | undefined;
    /** The library's album key used in the /album/:albumKey route. */
    albumKey: string | null | undefined;
    /** Shown when there is no album title at all. */
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

const linkable = computed(
  () => !props.disabled && isLinkableAlbum(props.name) && !!props.albumKey,
);

/** A modified click (Shift/Ctrl/Cmd) is a selection gesture on the row or
 *  card underneath, so it passes through; a plain click stops here. */
function onClick(e: MouseEvent): void {
  if (e.shiftKey || e.ctrlKey || e.metaKey) return;
  e.stopPropagation();
  e.preventDefault();
  open();
}

function open(): void {
  const key = props.albumKey;
  if (!key || !isLinkableAlbum(props.name)) return;
  emit("navigate", props.name);
  void router.push({ name: "album", params: { albumKey: key } });
}
</script>

<style scoped>
.album-link {
  text-underline-offset: 2px;
}
@media (hover: hover) {
  .album-link:hover {
    text-decoration: underline;
  }
}
.album-link:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 1px;
  border-radius: 2px;
}
</style>
