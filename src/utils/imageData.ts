/**
 * Conversions between the several shapes cover art arrives in.
 *
 * A cover reaches the metadata editor as a `data:` URL from a file input, as
 * raw bytes from the Tauri filesystem plugin, or as base64 from the server's
 * image proxy — and always leaves as bare base64, because that is what the tag
 * writer takes. These were inline in `MetadataEditor.vue`, where one of them
 * (the PNG re-encode) had been copy-pasted into a second function rather than
 * called.
 */

const ONE_MB = 1024 * 1024;

/**
 * Longest edge of a stored cover, in px. Rockbox — the reference for what a
 * "default" device cover size is — expects embedded album art around 500×500
 * at most, and larger art just wastes flash and RAM on players. Keeping every
 * written cover at this size also keeps its base64 far below the server's
 * request-body limit, so the tag write is never rejected as too large.
 */
export const MAX_COVER_EDGE_PX = 500;

/**
 * The base64 payload of a `data:` URL, without the `data:<mime>;base64,`
 * prefix. A string that is already bare base64 passes through unchanged.
 */
export function stripDataUrlPrefix(dataUrl: string): string {
  const comma = dataUrl.indexOf(",");
  return comma >= 0 ? dataUrl.slice(comma + 1) : dataUrl;
}

/** `image/jpeg` unless the extension says otherwise. */
export function guessImageMimeFromPath(path: string): string {
  const ext = path.split(".").pop()?.toLowerCase() ?? "";
  if (ext === "png") return "image/png";
  if (ext === "webp") return "image/webp";
  if (ext === "gif") return "image/gif";
  if (ext === "bmp") return "image/bmp";
  return "image/jpeg";
}

/** A content type without its parameters, lowercased — `image/png; charset=x` → `image/png`. */
export function normalizeMime(mime: string): string {
  return mime.toLowerCase().split(";")[0].trim();
}

/**
 * Base64 for a byte array.
 *
 * Chunked because `String.fromCharCode(...bytes)` on a whole cover blows the
 * argument limit and throws — a 500 KB PNG is 500,000 arguments.
 */
export function bytesToBase64(bytes: Uint8Array): string {
  let binary = "";
  const chunk = 0x8000;
  for (let i = 0; i < bytes.length; i += chunk) {
    binary += String.fromCharCode(...bytes.subarray(i, i + chunk));
  }
  return btoa(binary);
}

/**
 * Re-encode a PNG `data:` URL as JPEG base64, via a canvas.
 *
 * Covers are stored as JPEG so the tag stays a sane size; a lossless PNG album
 * cover is routinely several megabytes.
 */
export async function pngDataUrlToJpegBase64(dataUrl: string): Promise<string> {
  return await new Promise<string>((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const canvas = document.createElement("canvas");
      canvas.width = img.naturalWidth;
      canvas.height = img.naturalHeight;
      const ctx = canvas.getContext("2d");
      if (!ctx) {
        reject(new Error("Canvas not supported"));
        return;
      }
      ctx.drawImage(img, 0, 0);
      try {
        resolve(stripDataUrlPrefix(canvas.toDataURL("image/jpeg", 0.92)));
      } catch (e) {
        reject(e);
      }
    };
    img.onerror = () => reject(new Error("Failed to decode image"));
    img.src = dataUrl;
  });
}

/**
 * Re-encode an image `data:` URL as JPEG base64, downscaled so its longest
 * side is at most `maxSize` px (never upscaled). Covers are stored as JPEG and
 * kept small: a full-resolution photo is routinely several MB, whose base64
 * then exceeds the server's request-body limit and the tag write is rejected.
 */
export async function downscaleImageToJpegBase64(dataUrl: string, maxSize = MAX_COVER_EDGE_PX): Promise<string> {
  return await new Promise<string>((resolve, reject) => {
    const img = new Image();
    img.onload = () => {
      const scale = Math.min(1, maxSize / Math.max(img.naturalWidth, img.naturalHeight));
      const w = Math.max(1, Math.round(img.naturalWidth * scale));
      const h = Math.max(1, Math.round(img.naturalHeight * scale));
      const canvas = document.createElement("canvas");
      canvas.width = w;
      canvas.height = h;
      const ctx = canvas.getContext("2d");
      if (!ctx) {
        reject(new Error("Canvas not supported"));
        return;
      }
      ctx.drawImage(img, 0, 0, w, h);
      try {
        resolve(stripDataUrlPrefix(canvas.toDataURL("image/jpeg", 0.92)));
      } catch (e) {
        reject(e);
      }
    };
    img.onerror = () => reject(new Error("Failed to decode image"));
    img.src = dataUrl;
  });
}

/** `"412 KB"` / `"1.4 MB"` — what the cover popup shows under the artwork. */
export function formatImageSize(bytes: number): string {
  if (bytes >= ONE_MB) return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
  return `${(bytes / 1024).toFixed(0)} KB`;
}
