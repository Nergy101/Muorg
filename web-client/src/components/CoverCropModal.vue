<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from "vue";
import { MAX_COVER_EDGE_PX } from "@shared/utils/imageData";

const props = defineProps<{ imageUrl: string }>();
const emit = defineEmits<{ apply: [dataUrl: string]; cancel: [] }>();

const maxEdge = MAX_COVER_EDGE_PX;

// The crop region is a rectangle in DISPLAY pixels over the displayed image,
// locked to the image's aspect ratio; on apply it is mapped back to natural
// image pixels and drawn to a canvas capped at MAX_COVER_EDGE_PX on its
// longest edge, keeping the aspect ratio. The image can be zoomed (fit = 1);
// zooming in lets the frame cover a small fraction of the source, so small
// regions can be cropped precisely.
const imgEl = ref<HTMLImageElement | null>(null);
const boxRef = ref<HTMLElement | null>(null);
const natW = ref(0);
const natH = ref(0);
/** Displayed image size at zoom 1 (fits the modal), in px. */
const baseW = ref(0);
const baseH = ref(0);
const zoom = ref(1);
const dispW = ref(0);
const dispH = ref(0);
// selection: top-left (x, y) + size (w, h), in display px
const sel = ref({ x: 0, y: 0, w: 0, h: 0 });
const drag = ref<null | { mode: "move" | "resize"; startX: number; startY: number; orig: { x: number; y: number; w: number; h: number } }>(null);

const ZOOM_MIN = 0.25;
const ZOOM_MAX = 8;

/** Aspect ratio (w/h) of the source image; the selection is locked to it. */
function aspect(): number {
  return natH.value > 0 ? natW.value / natH.value : 1;
}

/** Largest size with the image's aspect ratio that fits the scroll box. */
function fitSize(): { w: number; h: number } {
  const box = boxRef.value;
  const availW = (box?.clientWidth ?? 480) - 16; // p-2 padding
  const availH = Math.round(window.innerHeight * 0.6) - 16;
  let w = availW;
  let h = w / aspect();
  if (h > availH) {
    h = availH;
    w = h * aspect();
  }
  return { w, h };
}

function onImgLoad(): void {
  const img = imgEl.value;
  if (!img) return;
  natW.value = img.naturalWidth;
  natH.value = img.naturalHeight;
  const { w, h } = fitSize();
  baseW.value = w;
  baseH.value = h;
  dispW.value = w * zoom.value;
  dispH.value = h * zoom.value;
  // Largest centered rect with the image's aspect ratio that fits the display.
  sel.value = { x: 0, y: 0, w: dispW.value, h: dispH.value };
}

function onResize(): void {
  if (!natW.value) return;
  const { w, h } = fitSize();
  const f = zoom.value;
  baseW.value = w;
  baseH.value = h;
  dispW.value = w * f;
  dispH.value = h * f;
  sel.value = clampSel(sel.value);
}

function setZoom(z: number): void {
  zoom.value = Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, z));
}

function zoomBy(factor: number): void {
  setZoom(zoom.value * factor);
}

function onWheel(e: WheelEvent): void {
  zoomBy(e.deltaY < 0 ? 1.15 : 1 / 1.15);
}

// Keep the selection proportional when the zoom changes.
watch(zoom, (z, old) => {
  const f = z / old;
  dispW.value = baseW.value * z;
  dispH.value = baseH.value * z;
  sel.value = clampSel({ x: sel.value.x * f, y: sel.value.y * f, w: sel.value.w * f, h: sel.value.h * f });
});

function clampSel(s: { x: number; y: number; w: number; h: number }) {
  const ratio = aspect();
  let w = Math.max(24, Math.min(s.w, dispW.value));
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

onMounted(() => window.addEventListener("resize", onResize));
onBeforeUnmount(() => {
  window.removeEventListener("resize", onResize);
  drag.value = null;
});
</script>

<template>
  <Teleport to="body">
    <div class="fixed inset-0 z-50 flex items-center justify-center bg-black/70 p-4" @click.self="emit('cancel')">
      <div class="glass-strong w-full max-w-lg rounded-[28px] p-5 shadow-2xl lg:max-w-3xl">
        <h2 class="text-title-lg text-on-surface">Crop cover</h2>
        <p class="mt-1 text-body-sm text-on-surface-variant">
          Drag the frame to move it, drag its corner to resize. Zoom with the buttons or the
          mouse wheel to crop a smaller region. The crop keeps the image's aspect ratio and is
          saved at up to {{ maxEdge }}×{{ maxEdge }}px.
        </p>

        <div
          ref="boxRef"
          class="mt-4 max-h-[60vh] overflow-auto rounded-xl bg-black/40 p-2 lg:max-h-[70vh]"
          @wheel.prevent="onWheel"
        >
          <div
            class="relative mx-auto shrink-0"
            :style="{ width: `${dispW}px`, height: `${dispH}px` }"
          >
          <img
            ref="imgEl"
            :src="props.imageUrl"
            alt="Cover to crop"
            class="block select-none"
            :style="{ width: `${dispW}px`, height: `${dispH}px` }"
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

        <!-- Zoom controls: zooming in shrinks the frame relative to the source
             image, so small regions can be cropped precisely. -->
        <div class="mt-2 flex items-center justify-center gap-2">
          <button
            type="button"
            class="h-8 w-8 rounded-full bg-surface-variant text-label-lg text-on-surface"
            aria-label="Zoom out"
            @click="zoomBy(1 / 1.25)"
          >−</button>
          <span class="w-12 text-center text-body-sm text-on-surface-variant">{{ Math.round(zoom * 100) }}%</span>
          <button
            type="button"
            class="h-8 w-8 rounded-full bg-surface-variant text-label-lg text-on-surface"
            aria-label="Zoom in"
            @click="zoomBy(1.25)"
          >+</button>
          <button
            type="button"
            class="rounded-full bg-surface-variant px-3 py-1.5 text-label-sm text-on-surface"
            @click="setZoom(1)"
          >Fit</button>
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
