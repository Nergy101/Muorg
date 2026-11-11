import { ref, watch } from "vue";
import type { Ref } from "vue";

/** Natural resolution of an image URL, null while loading/unavailable. */
export function useImageSize(imageUrl: Ref<string | null>) {
  const size = ref<{ width: number; height: number } | null>(null);

  watch(
    imageUrl,
    (url) => {
      if (!url) {
        size.value = null;
        return;
      }
      const img = new Image();
      img.onload = () => {
        size.value = { width: img.naturalWidth, height: img.naturalHeight };
      };
      img.onerror = () => {
        size.value = null;
      };
      img.src = url;
    },
    { immediate: true },
  );

  return size;
}
