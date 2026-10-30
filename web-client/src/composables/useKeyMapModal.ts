import { ref } from "vue";

/** Whether the keyboard shortcut reference is showing. */
export const keyMapOpen = ref(false);

export function openKeyMap(): void {
  keyMapOpen.value = true;
}

export function closeKeyMap(): void {
  keyMapOpen.value = false;
}

export function toggleKeyMap(): void {
  keyMapOpen.value = !keyMapOpen.value;
}
