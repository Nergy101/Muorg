import { beforeEach, describe, expect, it, vi } from "vitest";
import { MAX_UNDO_DEPTH, canRedo, canUndo, clearUndo, pushUndo, recordUndoable, redoLast, undoLast } from "./useUndo";
import { runToastAction } from "./useToast";

function entry(label: string, log: string[]) {
  return {
    label,
    undo: vi.fn(async () => void log.push(`undo ${label}`)),
    redo: vi.fn(async () => void log.push(`redo ${label}`)),
  };
}

describe("undo stack", () => {
  beforeEach(() => clearUndo());

  it("undoes newest first and can redo what was undone", async () => {
    const log: string[] = [];
    pushUndo(entry("a", log));
    pushUndo(entry("b", log));
    await undoLast();
    await undoLast();
    expect(log).toEqual(["undo b", "undo a"]);
    expect(canUndo.value).toBe(false);
    await redoLast();
    expect(log.at(-1)).toBe("redo a");
    expect(canRedo.value).toBe(true);
  });

  it("a new change discards the redo history", async () => {
    const log: string[] = [];
    pushUndo(entry("a", log));
    await undoLast();
    pushUndo(entry("b", log));
    expect(canRedo.value).toBe(false);
  });

  it("a failing undo keeps the entry so it can be retried", async () => {
    const e = entry("a", []);
    e.undo.mockRejectedValueOnce(new Error("server down"));
    pushUndo(e);
    expect(await undoLast()).toBe(false);
    expect(canUndo.value).toBe(true);
    expect(await undoLast()).toBe(true);
  });

  it("caps the history depth", () => {
    for (let i = 0; i < MAX_UNDO_DEPTH + 5; i++) pushUndo(entry(String(i), []));
    let n = 0;
    // Drain synchronously-checkable: depth is visible through repeated undo below.
    expect(canUndo.value).toBe(true);
    return (async () => {
      while (await undoLast()) n++;
      expect(n).toBe(MAX_UNDO_DEPTH);
    })();
  });
});

describe("recordUndoable", () => {
  beforeEach(() => clearUndo());

  it("the toast's Undo button undoes the entry it was shown for", async () => {
    const log: string[] = [];
    const e = entry("rated", log);
    recordUndoable(e);
    await runToastAction();
    expect(log).toEqual(["undo rated"]);
  });

  it("refuses to undo from a stale toast once a newer change exists", async () => {
    const log: string[] = [];
    recordUndoable(entry("first", log));
    pushUndo(entry("second", log));
    await runToastAction();
    expect(log).toEqual([]);
  });
});
