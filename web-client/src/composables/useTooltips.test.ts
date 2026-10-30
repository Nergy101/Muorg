import { describe, expect, it } from "vitest";
import { tipFor } from "./useTooltips";

function el(html: string): Element {
  const host = document.createElement("div");
  host.innerHTML = html;
  return host.firstElementChild as Element;
}

describe("tipFor", () => {
  it("describes an icon-only button by its aria-label", () => {
    expect(tipFor(el('<button aria-label="Clear search"><svg></svg></button>'))).toBe("Clear search");
  });

  it("expands a terse label to its fuller description", () => {
    expect(tipFor(el('<button aria-label="Next track"></button>'))).toBe("Next track (Ctrl+→)");
  });

  it("needs no popover when the label only repeats visible text", () => {
    expect(tipFor(el('<button aria-label="Export">Export</button>'))).toBeNull();
  });

  it("an explicit data-tip shows even on a button with text", () => {
    expect(tipFor(el('<button data-tip="Adds to the end of the queue">Queue</button>'))).toBe("Adds to the end of the queue");
  });

  it("takes a title over from the browser's native tooltip", () => {
    const button = el('<button title="Play the selected tracks next">Play next</button>');
    expect(tipFor(button)).toBe("Play the selected tracks next");
    expect(button.hasAttribute("title")).toBe(false);
    // Still described on the next hover, once the title is gone.
    expect(tipFor(button)).toBe("Play the selected tracks next");
  });

  it("has nothing to say for an unlabelled control", () => {
    expect(tipFor(el("<button><svg></svg></button>"))).toBeNull();
  });
});
