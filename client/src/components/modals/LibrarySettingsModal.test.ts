import { beforeEach, describe, expect, it, vi } from "vitest";
import { mount, type VueWrapper } from "@vue/test-utils";
import { createPinia, setActivePinia } from "pinia";

// The panels reach for Tauri APIs; none of them run at mount time except
// GeneralPanel's settings-path lookup, which is wrapped in try/catch.
vi.mock("@tauri-apps/api/core", () => ({ invoke: vi.fn(async () => null) }));
vi.mock("@tauri-apps/api/path", () => ({
  appConfigDir: vi.fn(async () => "/tmp"),
  join: vi.fn(async (...parts: string[]) => parts.join("/")),
}));
vi.mock("@tauri-apps/plugin-updater", () => ({ check: vi.fn(async () => null) }));
vi.mock("@tauri-apps/plugin-process", () => ({ relaunch: vi.fn(async () => null) }));
vi.mock("@tauri-apps/plugin-shell", () => ({ open: vi.fn(async () => null) }));

import LibrarySettingsModal from "./LibrarySettingsModal.vue";
import ConnectionPanel from "./settings/ConnectionPanel.vue";
import GeneralPanel from "./settings/GeneralPanel.vue";
import ThemePanel from "./settings/ThemePanel.vue";
import PlaybackPanel from "./settings/PlaybackPanel.vue";
import TablePanel from "./settings/TablePanel.vue";
import KeyboardPanel from "./settings/KeyboardPanel.vue";
import ReportsPanel from "./settings/ReportsPanel.vue";
import ExportsPanel from "./settings/ExportsPanel.vue";
import SmartSuggestionsPanel from "./settings/SmartSuggestionsPanel.vue";
import SmartTransformPanel from "./settings/SmartTransformPanel.vue";
import StatisticsPanel from "./settings/StatisticsPanel.vue";

/**
 * Every settings panel is rendered in one scroll container and switched with
 * v-show. A panel whose template has several root nodes (a fragment — e.g. a
 * root div plus a Teleport sibling) cannot be hidden by v-show, so its content
 * stays on screen underneath the active tab. These tests pin that contract:
 * exactly one panel is visible for the active tab.
 */

const PANELS = [
  { tab: "connection", label: "Connection", component: ConnectionPanel, marker: "Server Connection" },
  { tab: "general", label: "General", component: GeneralPanel, marker: "Check for updates" },
  { tab: "theme", label: "Theme", component: ThemePanel, marker: "Choose your palette" },
  { tab: "playback", label: "Playback", component: PlaybackPanel, marker: "Autoplay on track selection" },
  { tab: "table", label: "Layout", component: TablePanel, marker: "Library grouping" },
  { tab: "smart_suggestions", label: "Smart Suggestions", component: SmartSuggestionsPanel, marker: "Smart Suggestions" },
  { tab: "smart_transform", label: "Smart Transform", component: SmartTransformPanel, marker: "Origin pattern" },
  { tab: "reports", label: "Reports", component: ReportsPanel, marker: "Hide reports section" },
  { tab: "exports", label: "Exports", component: ExportsPanel, marker: "Music Root Folder" },
  { tab: "statistics", label: "Statistics", component: StatisticsPanel, marker: "Insights based on" },
  { tab: "keyboard", label: "Keyboard", component: KeyboardPanel, marker: "Keyboard shortcuts" },
] as const;

function isVisible(wrapper: VueWrapper, component: unknown): boolean {
  const found = wrapper.findComponent(component as never);
  if (!found.exists()) return false;
  return (found.element as HTMLElement).style.display !== "none";
}

async function openModal(): Promise<VueWrapper> {
  const wrapper = mount(LibrarySettingsModal, {
    props: { open: true },
    attachTo: document.body,
  });
  await new Promise((r) => setTimeout(r, 0));
  return wrapper;
}

async function clickTab(label: string) {
  const btn = Array.from(document.body.querySelectorAll(".settings-tab-btn")).find(
    (b) => b.textContent?.trim() === label,
  ) as HTMLElement | undefined;
  expect(btn, `tab button "${label}" exists`).toBeTruthy();
  btn!.click();
  await new Promise((r) => setTimeout(r, 0));
}

describe("LibrarySettingsModal tabs", () => {
  beforeEach(() => {
    setActivePinia(createPinia());
    document.body.innerHTML = "";
  });

  it("shows only the General panel by default", async () => {
    const wrapper = await openModal();
    for (const { component, marker } of PANELS) {
      const visible = isVisible(wrapper, component);
      if (marker === "Check for updates") {
        expect(visible, "General visible by default").toBe(true);
      } else {
        expect(visible, `${marker} hidden by default`).toBe(false);
      }
    }
  });

  it("shows only the Connection panel when the Connection tab is active", async () => {
    const wrapper = await openModal();
    await clickTab("Connection");

    for (const { component, marker } of PANELS) {
      const visible = isVisible(wrapper, component);
      if (marker === "Server Connection") {
        expect(visible, "Connection visible when its tab is active").toBe(true);
      } else {
        expect(visible, `${marker} hidden when the Connection tab is active`).toBe(false);
      }
    }
  });

  it("every tab shows exactly its own panel", async () => {
    const wrapper = await openModal();
    for (const { label, marker } of PANELS) {
      await clickTab(label);
      for (const other of PANELS) {
        const visible = isVisible(wrapper, other.component);
        expect(
          visible,
          `tab "${label}": panel "${other.marker}" should be ${other.marker === marker ? "visible" : "hidden"}`,
        ).toBe(other.marker === marker);
      }
    }
  });
});
