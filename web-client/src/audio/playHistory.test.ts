import { beforeEach, describe, expect, it } from "vitest";
import { peekPlayFuture, playFuture, playHistory, recordPlayHistory, stepBackPlayHistory } from "./playHistory";

/**
 * Ported from the desktop app's `client/src/state/playback.test.ts` (Q31):
 * Previous steps back through what was actually played, not the queue
 * neighbour — which is simply wrong while shuffling.
 */
describe("play history", () => {
  beforeEach(() => {
    playHistory.value = [];
    playFuture.value = [];
  });

  const known = (id: number) => id >= 1 && id <= 10;

  it("records played tracks in order", () => {
    recordPlayHistory(null, 1);
    recordPlayHistory(1, 2);
    recordPlayHistory(2, 3);
    expect(playHistory.value).toEqual([1, 2]);
  });

  it("previous follows the actual play order while shuffling", () => {
    // Shuffle played 5, then 2, then 8, then 1 (table order would be 1, 2, 5, 8).
    recordPlayHistory(null, 5);
    recordPlayHistory(5, 2);
    recordPlayHistory(2, 8);
    recordPlayHistory(8, 1);

    expect(stepBackPlayHistory(1, known)).toBe(8);
    recordPlayHistory(1, 8);
    expect(stepBackPlayHistory(8, known)).toBe(2);
    recordPlayHistory(8, 2);
    expect(stepBackPlayHistory(2, known)).toBe(5);
    expect(playHistory.value).toEqual([]);
  });

  it("treats the Previous selection as a step back, not a fresh forward move", () => {
    recordPlayHistory(null, 1);
    recordPlayHistory(1, 2);
    recordPlayHistory(2, 3);

    const back = stepBackPlayHistory(3, known);
    expect(back).toBe(2);
    // The player selects the back target; the recorder must not move the stacks again.
    recordPlayHistory(3, back);
    expect(playHistory.value).toEqual([1]);
    expect(playFuture.value).toEqual([3]);
  });

  it("returns forward through the back-stack after stepping back", () => {
    recordPlayHistory(null, 1);
    recordPlayHistory(1, 2);
    recordPlayHistory(2, 3);
    stepBackPlayHistory(3, known); // back to 2

    // Next selects the track we came from; the recorder consumes the back-stack entry.
    expect(peekPlayFuture(known)).toBe(3);
    recordPlayHistory(2, 3);
    expect(playFuture.value).toEqual([]);
    expect(playHistory.value).toEqual([1, 2]);
    expect(peekPlayFuture(known)).toBeNull();
  });

  it("a fresh forward move abandons the back-stack", () => {
    recordPlayHistory(null, 1);
    recordPlayHistory(1, 2);
    stepBackPlayHistory(2, known); // back to 1, future = [2]

    recordPlayHistory(1, 5); // the user picks a new track instead of returning
    expect(playFuture.value).toEqual([]);
    expect(playHistory.value).toEqual([1]);
    expect(peekPlayFuture(known)).toBeNull();
  });

  it("skips history entries whose track left the catalog", () => {
    recordPlayHistory(null, 1);
    recordPlayHistory(1, 2);
    recordPlayHistory(2, 3);

    // Track 2 left the catalog; Previous from 3 should land on 1.
    expect(stepBackPlayHistory(3, (id) => id !== 2)).toBe(1);
    expect(playHistory.value).toEqual([]);
  });

  it("keeps the history when playback stops", () => {
    recordPlayHistory(null, 1);
    recordPlayHistory(1, 2);
    recordPlayHistory(2, null);
    expect(playHistory.value).toEqual([1]);
  });
});
