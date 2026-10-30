import { describe, it, expect } from "vitest";
import {
  bytesToBase64,
  formatImageSize,
  guessImageMimeFromPath,
  normalizeMime,
  stripDataUrlPrefix,
} from "./imageData";

describe("stripDataUrlPrefix", () => {
  it("drops the data URL header", () => {
    expect(stripDataUrlPrefix("data:image/jpeg;base64,QUJD")).toBe("QUJD");
  });

  it("passes bare base64 through unchanged", () => {
    // Callers hand it either shape, so this has to be idempotent rather than
    // lopping off the first few characters of a plain payload.
    expect(stripDataUrlPrefix("QUJD")).toBe("QUJD");
  });

  it("keeps commas that are inside the payload", () => {
    expect(stripDataUrlPrefix("data:text/plain,a,b")).toBe("a,b");
  });
});

describe("guessImageMimeFromPath", () => {
  it("maps the extensions a cover file actually has", () => {
    expect(guessImageMimeFromPath("/art/cover.png")).toBe("image/png");
    expect(guessImageMimeFromPath("/art/cover.webp")).toBe("image/webp");
    expect(guessImageMimeFromPath("/art/cover.gif")).toBe("image/gif");
    expect(guessImageMimeFromPath("/art/cover.bmp")).toBe("image/bmp");
    expect(guessImageMimeFromPath("/art/cover.jpg")).toBe("image/jpeg");
  });

  it("ignores the case of the extension", () => {
    expect(guessImageMimeFromPath("/art/COVER.PNG")).toBe("image/png");
  });

  it("falls back to JPEG for anything unrecognised", () => {
    // The fallback matters: the value picks the re-encode path, and guessing
    // JPEG for an unknown file is the harmless option.
    expect(guessImageMimeFromPath("/art/cover")).toBe("image/jpeg");
    expect(guessImageMimeFromPath("/art/cover.tiff")).toBe("image/jpeg");
  });

  it("is not fooled by a dot in a directory name", () => {
    expect(guessImageMimeFromPath("/my.music/art/cover.png")).toBe("image/png");
  });
});

describe("normalizeMime", () => {
  it("strips parameters and lowercases", () => {
    expect(normalizeMime("IMAGE/PNG; charset=binary")).toBe("image/png");
    expect(normalizeMime("  image/jpeg  ")).toBe("image/jpeg");
  });
});

describe("bytesToBase64", () => {
  it("round-trips through atob", () => {
    const bytes = new Uint8Array([72, 101, 108, 108, 111]);
    expect(atob(bytesToBase64(bytes))).toBe("Hello");
  });

  it("handles an empty array", () => {
    expect(bytesToBase64(new Uint8Array())).toBe("");
  });

  it("handles the full byte range, including the high half", () => {
    const bytes = new Uint8Array(256);
    for (let i = 0; i < 256; i++) bytes[i] = i;
    const decoded = atob(bytesToBase64(bytes));
    expect(decoded.length).toBe(256);
    expect(decoded.charCodeAt(255)).toBe(255);
  });

  it("handles a payload larger than the chunk size", () => {
    // The whole reason it chunks: `String.fromCharCode(...bytes)` on a real
    // cover exceeds the argument limit and throws.
    const bytes = new Uint8Array(0x8000 * 2 + 17).fill(65);
    const encoded = bytesToBase64(bytes);
    expect(atob(encoded).length).toBe(bytes.length);
  });
});

describe("formatImageSize", () => {
  it("shows KB below a megabyte and MB above", () => {
    expect(formatImageSize(400 * 1024)).toBe("400 KB");
    expect(formatImageSize(1.5 * 1024 * 1024)).toBe("1.5 MB");
  });

  it("switches at exactly one megabyte", () => {
    expect(formatImageSize(1024 * 1024 - 1)).toBe("1024 KB");
    expect(formatImageSize(1024 * 1024)).toBe("1.0 MB");
  });
});
