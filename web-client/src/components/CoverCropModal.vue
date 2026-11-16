<script setup lang="ts">
import { onBeforeUnmount, ref } from "vue";
import { MAX_COVER_EDGE_PX } from "@shared/utils/imageData";

const props = defineProps<{ imageUrl: string }>();
const emit = defineEmits<{ apply: [dataUrl: string]; cancel: [] }>();

const maxEdge = MAX_COVER_EDGE_PX;

// The crop region is a rectangle in DISPLAY pixels over the fitted image,
// locked to the image's aspect ratio; on apply it is mapped back to natural
// image pixels and drawn to a canvas capped at MAX_COVER_EDGE_PX on its
// longest edge, keeping the aspect ratio.
const imgEl = ref<HTMLImageElement | null>(null);
const natW = ref(0);
const natH = ref(0);
const dispW = ref(0);
const dispH = ref(0);
// selection: top-left (x, y) + size (w, h), in display px
const sel = ref({ x: 0, y: 0, w: 0, h: 0 });
const drag = ref<null | { mode: "move" | "resize"; startX: number; startY: number; orig: { x: number; y: number; w: number; h: number } }>(null);

/** Aspect ratio (w/h) of the source image; the selection is locked to it. */
function aspect(): number {
  return natH.value > 0 ? natW.value / natH.value : 1;
}

function onImgLoad(): void {
  const img = imgEl.value;
  if (!img) return;
  natW.value = img.naturalWidth;
  natH.value = img.naturalHeight;
  const rect = img.getBoundingClientRect();
  dispW.value = rect.width;
  dispH.value = rect.height;
  // Largest centered rect with the image's aspect ratio that fits the display.
  let w = rect.width;
  let h = w / aspect();
  if (h > rect.height) {
    h = rect.height;
    w = h * aspect();
  }
  sel.value = { x: (rect.width - w) / 2, y: (rect.height - h) / 2, w, h };
}

function clampSel(s: { x: number; y: number; w: number; h: number }) {
  const ratio = aspect();
  let w = Math.max(32, Math.min(s.w, dispW.value));
  let h = w / ratio;
  if (h > dispH.value) {
    h = dispH.value;
    w = h * ratio;
  }
  return {
    w,
    h,
    x: Math.min(Math.max(0, s.x), dispW.value - w),
    y: Math.min(Math.max(0, s.y), dispH.value - h),
  };
}

function onPointerDown(e: PointerEvent, mode: "move" | "resize"): void {
  e.preventDefault();
  (e.target as HTMLElement).setPointerCapture(e.pointerId);
  drag.value = { mode, startX: e.clientX, startY: e.clientY, orig: { ...sel.value } };
}

function onPointerMove(e: PointerEvent): void {
  const d = drag.value;
  if (!d) return;
  const dx = e.clientX - d.startX;
  const dy = e.clientY - d.startY;
  if (d.mode === "move") {
    sel.value = clampSel({ ...d.orig, x: d.orig.x + dx, y: d.orig.y + dy });
  } else {
    // resize from the bottom-right corner, keeping the image's aspect ratio
    sel.value = clampSel({ ...d.orig, w: d.orig.w + Math.max(dx, dy) });
  }
}

function onPointerUp(): void {
  drag.value = null;
}

function apply(): void {
  const img = imgEl.value;
  if (!img || natW.value === 0) return;
  const scale = natW.value / dispW.value;
  const sx = sel.value.x * scale;
  const sy = sel.value.y * scale;
  const sw = sel.value.w * scale;
  const sh = sel.value.h * scale;
  // Cap the stored cover at the Rockbox-friendly default size (500px on the
  // longest edge), keeping the aspect ratio.
  const outScale = Math.min(1, MAX_COVER_EDGE_PX / Math.max(sw, sh));
  const outW = Math.max(1, Math.round(sw * outScale));
  const outH = Math.max(1, Math.round(sh * outScale));
  const canvas = document.createElement("canvas");
  canvas.width = outW;
  canvas.height = outH;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  ctx.drawImage(img, sx, sy, sw, sh, 0, 0, outW, outH);
  emit("apply", canvas.toDataURL("image/jpeg", 0.92));
}

onBeforeUnmount(() => {
  drag.value = null;
});
</script>

<template>
  <Teleport to="body">
    <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" @click.self="emit('cancel')">
      <div class="glass-strong w-full max-w-lg rounded-[28px] p-5 shadow-2xl">
        <h2 class="text-title-lg text-on-surface">Crop cover</h2>
        <p class="mt-1 text-body-sm text-on-surface-variant">
          Drag the frame to move it, drag its corner to resize. The crop keeps the image's
          aspect ratio and is saved at up to {{ maxEdge }}×{{ maxEdge }}px.
        </p>

        <div class="mt-4 flex items-center justify-center overflow-hidden rounded-xl bg-black/40 p-2">
          <div class="relative shrink-0">
          <img
            ref="imgEl"
            :src="props.imageUrl"
            alt="Cover to crop"
            class="block max-h-[60vh] max-w-[80vw] select-none"
            draggable="false"
            @load="onImgLoad"
          />
          <!-- dimmed area outside the crop rect -->
          <div
            v-if="dispW"
            class="pointer-events-none absolute inset-0"
            :style="{
              clipPath: `polygon(0 0, 100% 0, 100% 100%, 0 100%, 0 0, ${sel.x}px ${sel.y}px, ${sel.x}px ${sel.y + sel.h}px, ${sel.x + sel.w}px ${sel.y + sel.h}px, ${sel.x + sel.w}px ${sel.y}px, ${sel.x}px ${sel.y}px)`,
              background: 'rgba(0,0,0,0.55)',
            }"
          />
          <!-- the selection, locked to the image's aspect ratio -->
          <div
            v-if="dispW"
            class="absolute cursor-move border-2 border-primary"
            :style="{ left: `${sel.x}px`, top: `${sel.y}px`, width: `${sel.w}px`, height: `${sel.h}px` }"
            @pointerdown="onPointerDown($event, 'move')"
            @pointermove="onPointerMove"
            @pointerup="onPointerUp"
          >
            <div
              class="absolute -bottom-2 -right-2 h-5 w-5 cursor-nwse-resize rounded-full border-2 border-primary bg-surface"
              @pointerdown.stop="onPointerDown($event, 'resize')"
            />
          </div>
          </div>
        </div>

        <div class="mt-5 flex justify-end gap-2">
          <button
            type="button"
            class="rounded-full px-4 py-2 text-label-lg text-on-surface-variant"
            @click="emit('cancel')"
          >Cancel</button>
          <button
            type="button"
            class="rounded-full px-4 py-2 text-label-lg text-primary"
            :disabled="!dispW"
            @click="apply"
          >Apply crop</button>
        </div>
      </div>
    </div>
  </Teleport>
</template>
