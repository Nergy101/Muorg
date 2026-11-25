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
import PlaybackPanel from "./settings/PlaybackPanel.vue";
import TablePanel from "./settings/TablePanel.vue";
import KeyboardPanel from "./settings/KeyboardPanel.vue";
import ReportsPanel from "./settings/ReportsPanel.vue";
import ExportsPanel from "./settings/ExportsPanel.vue";
import SmartSuggestionsPanel from "./settings/SmartSuggestionsPanel.vue";
import SmartTransformPanel from "./settings/SmartTransformPanel.vue";
import StatisticsPanel from "./settings/StatisticsPanel.vue";

/**
 * Settings panels render inside collapsible section cards (web SettingsView
 * pattern): each section header toggles its panel with v-show. These tests pin
 * that contract: General is open by default, and opening a section shows its
 * own panel.
 */

const PANELS = [
  { tab: "connection", label: "Connection", component: ConnectionPanel, marker: "Server Connection" },
  { tab: "general", label: "General", component: GeneralPanel, marker: "Check for updates" },
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
  // Panels sit inside v-show section wrappers, so visibility is decided on an
  // ancestor: walk up until an element carries an inline display style.
  let el: HTMLElement | null = found.element as HTMLElement;
  while (el) {
    if (el.style.display === "none") return false;
    el = el.parentElement;
  }
  return true;
}

async function openModal(): Promise<VueWrapper> {
  const wrapper = mount(LibrarySettingsModal, {
    props: { open: true },
    attachTo: document.body,
  });
  await new Promise((r) => setTimeout(r, 0));
  return wrapper;
}

async function clickSection(label: string) {
  const btn = Array.from(document.body.querySelectorAll("button[aria-expanded]")).find(
    (b) => b.textContent?.trim() === label,
  ) as HTMLElement | undefined;
  expect(btn, `section header "${label}" exists`).toBeTruthy();
  btn!.click();
  await new Promise((r) => setTimeout(r, 0));
}

describe("LibrarySettingsModal sections", () => {
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

  it("shows the Connection panel when its section is opened", async () => {
    const wrapper = await openModal();
    await clickSection("Connection");
    expect(isVisible(wrapper, ConnectionPanel), "Connection visible when its section is open").toBe(true);
  });

  it("every section shows its own panel when opened", async () => {
    const wrapper = await openModal();
    for (const { label, component, marker } of PANELS) {
      // Sections toggle: skip the click when the section is already open.
      const btn = Array.from(document.body.querySelectorAll("button[aria-expanded]")).find(
        (b) => b.textContent?.trim() === label,
      ) as HTMLElement | undefined;
      expect(btn, `section header "${label}" exists`).toBeTruthy();
      if (btn!.getAttribute("aria-expanded") !== "true") {
        btn!.click();
        await new Promise((r) => setTimeout(r, 0));
      }
      expect(
        isVisible(wrapper, component),
        `section "${label}": panel "${marker}" should be visible`,
      ).toBe(true);
    }
  });
});
