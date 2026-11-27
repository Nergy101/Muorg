import { defineStore } from "pinia";
import { computed, ref } from "vue";
import {
  listProfiles,
  createProfile as apiCreate,
  renameProfile as apiRename,
  deleteProfile as apiDelete,
} from "../api/catalog";
import type { Profile } from "../types";
import { loadPref, savePref } from "./prefs";

/** localStorage key for the active profile id. */
const ACTIVE_PROFILE_KEY = "muorg.activeProfileId";

/**
 * NER-326: listening profiles.
 *
 * The feature is invisible until it exists: a fresh server has exactly one
 * profile ("You"), and with a single profile nothing in the UI even mentions
 * profiles. Creating a second one — here or on any client — is what switches
 * the feature on, and from then on the active profile is always visible.
 *
 * Play history and listening statistics are scoped to profiles; the library,
 * playlists and ratings stay shared. Stats views additionally let the user
 * pick which, any or all profiles feed the charts.
 */
export const useProfilesStore = defineStore("profiles", () => {
  const profiles = ref<Profile[]>([]);
  const activeProfileId = ref<number>(
    loadPref(ACTIVE_PROFILE_KEY, 1 /* the default profile */),
  );
  const loaded = ref(false);

  /** The profile plays are recorded under right now. */
  const activeProfile = computed(
    () => profiles.value.find((p) => p.id === activeProfileId.value) ?? profiles.value[0] ?? null,
  );

  /**
   * True once a second profile exists — the signal the rest of the UI uses to
   * decide whether profiles are worth mentioning at all.
   */
  const enabled = computed(() => loaded.value && profiles.value.length > 1);

  /** Ids to scope "just my stuff" queries to: the active profile, or none. */
  const activeFilter = computed<number[] | undefined>(() =>
    enabled.value && activeProfile.value ? [activeProfile.value.id] : undefined,
  );

  async function refresh(): Promise<void> {
    try {
      profiles.value = await listProfiles();
      loaded.value = true;
      if (!profiles.value.some((p) => p.id === activeProfileId.value)) {
        // Deleted server-side (or a stale localStorage id) — fall back home.
        activeProfileId.value = profiles.value[0]?.id ?? 1;
        savePref(ACTIVE_PROFILE_KEY, activeProfileId.value);
      }
    } catch {
      // Offline or old server without the endpoints: keep a single-profile view.
      profiles.value = [];
      loaded.value = false;
    }
  }

  function setActive(id: number): void {
    if (!profiles.value.some((p) => p.id === id)) return;
    activeProfileId.value = id;
    savePref(ACTIVE_PROFILE_KEY, id);
  }

  async function create(name: string): Promise<Profile> {
    const profile = await apiCreate(name);
    await refresh();
    setActive(profile.id);
    return profile;
  }

  async function rename(id: number, name: string): Promise<void> {
    await apiRename(id, name);
    await refresh();
  }

  async function remove(id: number): Promise<void> {
    await apiDelete(id);
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
    refresh,
    setActive,
    create,
    rename,
    remove,
  };
});
