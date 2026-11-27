import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { api, getBackendMode, getOnlineServerUrl } from "../api/client";
import type { Profile } from "@shared/api";

function loadPref<T>(key: string, fallback: T): T {
  try {
    const v = window.localStorage.getItem(key);
    return v != null ? (JSON.parse(v) as T) : fallback;
  } catch {
    return fallback;
  }
}

function savePref(key: string, value: unknown): void {
  window.localStorage.setItem(key, JSON.stringify(value));
}

/**
 * Profiles are per library context: the local sidecar and every remote server
 * each have their own profile list (server-side data) and their own
 * "listening as" choice, stored under a key derived from the context.
 */
function contextKey(): string {
  return getBackendMode() === "local" ? "local" : getOnlineServerUrl() || "local";
}

function activeKey(): string {
  return `muorg.activeProfileId.${contextKey()}`;
}

/**
 * NER-326: listening profiles.
 *
 * The feature is invisible until it exists: a fresh library has exactly one
 * profile ("You"), and with a single profile nothing in the UI mentions
 * profiles. Creating a second one switches the feature on, and from then on
 * the active profile is always shown (top bar, home, settings).
 */
export const useProfilesStore = defineStore("profiles", () => {
  const profiles = ref<Profile[]>([]);
  const activeProfileId = ref<number>(loadPref(activeKey(), 1 /* the default profile */));
  const loaded = ref(false);

  /** The profile plays are recorded under right now. */
  const activeProfile = computed(
    () => profiles.value.find((p) => p.id === activeProfileId.value) ?? profiles.value[0] ?? null,
  );

  /** True once a second profile exists in the current library context. */
  const enabled = computed(() => loaded.value && profiles.value.length > 1);

  /** Ids to scope "just my stuff" queries to: the active profile, or none. */
  const activeFilter = computed<number[] | undefined>(() =>
    enabled.value && activeProfile.value ? [activeProfile.value.id] : undefined,
  );

  /** Re-read the stored choice for the current library context. */
  function bindContext(): void {
    activeProfileId.value = loadPref(activeKey(), 1);
  }

  /** True when the user already picked a profile for the current context. */
  function hasStoredChoice(): boolean {
    try {
      return window.localStorage.getItem(activeKey()) != null;
    } catch {
      return false;
    }
  }

  async function refresh(): Promise<void> {
    try {
      profiles.value = await api.listProfiles();
      loaded.value = true;
      if (!profiles.value.some((p) => p.id === activeProfileId.value)) {
        // Deleted server-side (or a stale stored id) — fall back home.
        activeProfileId.value = profiles.value[0]?.id ?? 1;
        savePref(activeKey(), activeProfileId.value);
      }
    } catch {
      // Offline or old server without the endpoints: single-profile view.
      profiles.value = [];
      loaded.value = false;
    }
  }

  function setActive(id: number): void {
    if (!profiles.value.some((p) => p.id === id)) return;
    activeProfileId.value = id;
    savePref(activeKey(), id);
  }

  async function create(name: string): Promise<Profile> {
    const profile = await api.createProfile(name);
    await refresh();
    setActive(profile.id);
    return profile;
  }

  async function rename(id: number, name: string): Promise<void> {
    await api.renameProfile(id, name);
    await refresh();
  }

  async function remove(id: number): Promise<void> {
    await api.deleteProfile(id);
    if (activeProfileId.value === id) setActive(1);
    await refresh();
  }

  return {
    profiles,
    activeProfileId,
    activeProfile,
    enabled,
    activeFilter,
    loaded,
    bindContext,
    hasStoredChoice,
    refresh,
    setActive,
    create,
    rename,
    remove,
  };
});
