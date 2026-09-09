import { describe, expect, it } from "vitest";
import { activeLrcIndex, parseLrc, type LrcLine } from "./lyrics";

describe("parseLrc", () => {
  it("reads mm:ss timestamps", () => {
    expect(parseLrc("[00:12]first\n[01:05]second")).toEqual([
      { time: 12, text: "first" },
      { time: 65, text: "second" },
    ]);
  });

  it("treats the fraction as milliseconds regardless of how many digits it has", () => {
    // `.5`, `.50` and `.500` all mean half a second — a naive parseInt would
    // read them as 5 ms, 50 ms and 500 ms.
    const [a, b, c] = parseLrc("[00:01.5]a\n[00:02.50]b\n[00:03.500]c");
    expect(a.time).toBeCloseTo(1.5);
    expect(b.time).toBeCloseTo(2.5);
    expect(c.time).toBeCloseTo(3.5);
  });

  it("drops metadata headers and untimed lines", () => {
    const lines = parseLrc("[ar:Some Artist]\n[ti:A Title]\n\nplain text\n[00:10]real");
    expect(lines).toEqual([{ time: 10, text: "real" }]);
  });

  it("drops a timestamp with no text after it", () => {
    expect(parseLrc("[00:05]   \n[00:06]words")).toEqual([
      { time: 6, text: "words" },
    ]);
  });

  it("sorts by time even when the file is out of order", () => {
    expect(parseLrc("[00:30]late\n[00:10]early").map((l) => l.text)).toEqual([
      "early",
      "late",
    ]);
  });

  it("returns nothing for plain, untimestamped lyrics", () => {
    expect(parseLrc("just some words\nand more of them")).toEqual([]);
  });

  it("handles CRLF line endings", () => {
    expect(parseLrc("[00:01]a\r\n[00:02]b")).toHaveLength(2);
  });
});

describe("activeLrcIndex", () => {
  const lines: LrcLine[] = [
    { time: 0, text: "zero" },
    { time: 10, text: "ten" },
    { time: 20, text: "twenty" },
  ];

  it("is -1 before the first timestamp", () => {
    expect(activeLrcIndex([{ time: 5, text: "a" }], 4.9)).toBe(-1);
  });

  it("holds a line until the next one is due", () => {
    expect(activeLrcIndex(lines, 0)).toBe(0);
    expect(activeLrcIndex(lines, 9.99)).toBe(0);
    expect(activeLrcIndex(lines, 10)).toBe(1);
    expect(activeLrcIndex(lines, 19.99)).toBe(1);
  });

  it("stays on the last line past the end of the track", () => {
    expect(activeLrcIndex(lines, 9999)).toBe(2);
  });

  it("is -1 for an empty document", () => {
    expect(activeLrcIndex([], 42)).toBe(-1);
  });
});
