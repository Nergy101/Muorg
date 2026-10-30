import { beforeEach, describe, expect, it } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import { useSelectionStore } from "./selection";

const ORDER = [10, 20, 30, 40, 50];

describe("selection store", () => {
  beforeEach(() => setActivePinia(createPinia()));

  it("a plain click outside selection mode is not consumed", () => {
    const s = useSelectionStore();
    expect(s.click(20, ORDER)).toBe(false);
    expect(s.count).toBe(0);
  });

  it("ctrl-click starts selection mode and toggles", () => {
    const s = useSelectionStore();
    expect(s.click(20, ORDER, { ctrl: true })).toBe(true);
    expect(s.selecting).toBe(true);
    // In selection mode a plain click toggles too.
    expect(s.click(40, ORDER)).toBe(true);
    expect(s.ids.sort()).toEqual([20, 40]);
    s.click(20, ORDER);
    expect(s.ids).toEqual([40]);
  });

  it("shift-click selects the range in the DISPLAYED order, either direction", () => {
    const s = useSelectionStore();
    s.click(40, ORDER, { ctrl: true });
    s.click(20, ORDER, { shift: true });
    expect(s.ids.sort()).toEqual([20, 30, 40]);
  });

  it("ends selection mode when the last item is deselected", () => {
    const s = useSelectionStore();
    s.begin(10);
    expect(s.selecting).toBe(true);
    s.toggle(10);
    expect(s.selecting).toBe(false);
  });

  it("prune drops ids that no longer exist", () => {
    const s = useSelectionStore();
    s.selectAll(ORDER);
    s.prune((id) => id !== 30);
    expect(s.ids).not.toContain(30);
    expect(s.count).toBe(4);
  });
});
