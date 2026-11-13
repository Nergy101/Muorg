/**
 * Bitrate / sample-rate probing for the currently playing file or stream.
 *
 * The catalog only stores the container `format`, so the numbers are read
 * straight from the audio bytes: a `Range: bytes=0-…` fetch against the same
 * stream URL the `<audio>` element plays (tokened online, or the SW-cached
 * offline URL), then the container header is parsed — MP3 frame header,
 * FLAC STREAMINFO, or RIFF/WAVE `fmt ` chunk. Only the first 64 KiB are ever
 * read, even when the server ignores the Range header and answers 200.
 */

export interface AudioStats {
  /** Stream/file bitrate in kbps (rounded), null when it can't be determined. */
  bitrateKbps: number | null;
  /** Sample rate in Hz, null when the container doesn't expose one. */
  sampleRateHz: number | null;
}

/** How many leading bytes are enough for every supported header. */
const PROBE_BYTES = 64 * 1024;

// MPEG audio bitrate tables, indexed by the 4-bit header field (kbps).
// Index 0 ("free") and 15 (invalid) are excluded by the caller.
const BITRATES_KBPS: Record<string, number[]> = {
  // version bits 11 = MPEG1
  "1:1": [0, 32, 64, 96, 128, 160, 192, 224, 256, 288, 320, 352, 384, 416, 448], // Layer I
  "1:2": [0, 32, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320, 384], // Layer II
  "1:3": [0, 32, 40, 48, 56, 64, 80, 96, 112, 128, 160, 192, 224, 256, 320], // Layer III
  // version bits 10 = MPEG2, 00 = MPEG2.5 (same tables)
  "2:1": [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160],
  "2:2": [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160],
  "2:3": [0, 8, 16, 24, 32, 40, 48, 56, 64, 80, 96, 112, 128, 144, 160],
};

const SAMPLE_RATES_HZ: Record<string, number[]> = {
  "3": [44100, 48000, 32000], // MPEG1
  "2": [22050, 24000, 16000], // MPEG2
  "0": [11025, 12000, 8000], // MPEG2.5
};

interface Mp3Frame {
  bitrateBps: number;
  sampleRateHz: number;
}

/** Scan for the first valid MPEG frame sync and decode its header. */
export function parseMp3Frame(bytes: Uint8Array): Mp3Frame | null {
  for (let i = 0; i + 4 <= bytes.length; i++) {
    if (bytes[i] !== 0xff || (bytes[i + 1] & 0xe0) !== 0xe0) continue;
    const versionBits = (bytes[i + 1] >> 3) & 0x03; // 3=MPEG1, 2=MPEG2, 0=MPEG2.5
    const layerBits = (bytes[i + 1] >> 1) & 0x03; // 3=Layer I, 2=Layer II, 1=Layer III
    if (versionBits === 1 || layerBits === 0) continue; // reserved
    const bitrateIndex = bytes[i + 2] >> 4;
    const srIndex = (bytes[i + 2] >> 2) & 0x03;
    if (bitrateIndex === 0 || bitrateIndex === 15 || srIndex === 3) continue;
    const versionKey = versionBits === 3 ? "1" : "2";
    const layerKey = layerBits === 3 ? "1" : layerBits === 2 ? "2" : "3";
    const bitrateKbps = BITRATES_KBPS[`${versionKey}:${layerKey}`]?.[bitrateIndex];
    const sampleRateHz = SAMPLE_RATES_HZ[String(versionBits)]?.[srIndex];
    if (!bitrateKbps || !sampleRateHz) continue;
    // Frame length in bytes; if a second sync sits exactly there, this is a
    // real frame, not a false positive inside tag/payload bytes.
    const padding = (bytes[i + 2] >> 1) & 1;
    const frameBytes =
      layerBits === 3
        ? Math.floor((12 * (bitrateKbps * 1000) / sampleRateHz + padding) * 4)
        : Math.floor(144 * (bitrateKbps * 1000) / sampleRateHz + padding);
    if (i + frameBytes + 2 <= bytes.length) {
      const next = i + frameBytes;
      if (bytes[next] !== 0xff || (bytes[next + 1] & 0xe0) !== 0xe0) continue;
    }
    return { bitrateBps: bitrateKbps * 1000, sampleRateHz };
  }
  return null;
}

interface FlacStreamInfo {
  sampleRateHz: number;
  channels: number;
  bitsPerSample: number;
}

/** Decode the STREAMINFO block of a FLAC file ("fLaC" magic + block header). */
export function parseFlacStreamInfo(bytes: Uint8Array): FlacStreamInfo | null {
  if (bytes.length < 8 + 34) return null;
  if (bytes[0] !== 0x66 || bytes[1] !== 0x4c || bytes[2] !== 0x61 || bytes[3] !== 0x43) return null;
  if ((bytes[4] & 0x7f) !== 0) return null; // first metadata block must be STREAMINFO
  const b = bytes;
  const sampleRateHz = (b[18] << 12) | (b[19] << 4) | (b[20] >> 4);
  const channels = ((b[20] >> 1) & 0x07) + 1;
  const bitsPerSample = (((b[20] & 0x01) << 4) | (b[21] >> 4)) + 1;
  if (sampleRateHz === 0) return null;
  return { sampleRateHz, channels, bitsPerSample };
}

interface WavFmt {
  sampleRateHz: number;
  channels: number;
  bitsPerSample: number;
  byteRateBps: number;
}

/** Walk the RIFF chunks of a WAV file and decode the `fmt ` chunk. */
export function parseWavFmt(bytes: Uint8Array): WavFmt | null {
  if (bytes.length < 12) return null;
  if (bytes[0] !== 0x52 || bytes[1] !== 0x49 || bytes[2] !== 0x46 || bytes[3] !== 0x46) return null; // RIFF
  if (bytes[8] !== 0x57 || bytes[9] !== 0x41 || bytes[10] !== 0x56 || bytes[11] !== 0x45) return null; // WAVE
  const view = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  let offset = 12;
  while (offset + 8 <= bytes.length) {
    const id = String.fromCharCode(bytes[offset], bytes[offset + 1], bytes[offset + 2], bytes[offset + 3]);
    const size = view.getUint32(offset + 4, true);
    if (id === "fmt " && offset + 8 + 16 <= bytes.length) {
      const channels = view.getUint16(offset + 10, true);
      const sampleRateHz = view.getUint32(offset + 12, true);
      const byteRateBps = view.getUint32(offset + 16, true);
      const bitsPerSample = view.getUint16(offset + 22, true);
      if (sampleRateHz === 0) return null;
      return { sampleRateHz, channels, bitsPerSample, byteRateBps };
    }
    offset += 8 + size + (size % 2); // chunks are word-aligned
  }
  return null;
}

/** Total size from a `Content-Range: bytes 0-N/total` response header. */
export function totalBytesFromContentRange(header: string | null): number | null {
  if (!header) return null;
  const m = /\/(\d+)\s*$/.exec(header);
  return m ? Number(m[1]) : null;
}

/** Read at most `PROBE_BYTES` from the response body, then cancel the rest. */
async function readHead(res: Response): Promise<Uint8Array> {
  if (!res.body) return new Uint8Array(await res.arrayBuffer());
  const reader = res.body.getReader();
  const chunks: Uint8Array[] = [];
  let received = 0;
  try {
    for (;;) {
      const { done, value } = await reader.read();
      if (done || !value) break;
      chunks.push(value);
      received += value.length;
      if (received >= PROBE_BYTES) break;
    }
  } finally {
    // Cancel the download — only the header was needed.
    await reader.cancel().catch(() => {});
  }
  const out = new Uint8Array(Math.min(received, PROBE_BYTES));
  let pos = 0;
  for (const c of chunks) {
    const n = Math.min(c.length, out.length - pos);
    out.set(c.subarray(0, n), pos);
    pos += n;
    if (pos >= out.length) break;
  }
  return out;
}

/**
 * Probe the bitrate and sample rate of the audio behind `url` (the stream URL
 * the player uses). `format` is the catalog's container format (mp3, flac,
 * wav, …) and `durationSecs` the catalog duration, used to derive the
 * bitrate from the total byte size when the header doesn't carry one.
 */
export async function probeAudioStats(
  url: string,
  format: string,
  durationSecs: number | null,
): Promise<AudioStats> {
  const res = await fetch(url, { headers: { Range: `bytes=0-${PROBE_BYTES - 1}` } });
  if (!res.ok && res.status !== 206) throw new Error(`HTTP ${res.status}`);
  const totalBytes = totalBytesFromContentRange(res.headers.get("Content-Range"));
  const head = await readHead(res);
  const fmt = format.toLowerCase();

  let headerBitrateBps: number | null = null;
  let sampleRateHz: number | null = null;

  if (fmt === "mp3" || fmt === "mp3pro" || fmt === "aac" || head[0] === 0xff) {
    const frame = parseMp3Frame(head);
    if (frame) {
      headerBitrateBps = frame.bitrateBps;
      sampleRateHz = frame.sampleRateHz;
    }
  }
  if (sampleRateHz === null && (fmt === "flac" || (head[0] === 0x66 && head[1] === 0x4c))) {
    const info = parseFlacStreamInfo(head);
    if (info) {
      sampleRateHz = info.sampleRateHz;
      headerBitrateBps = info.sampleRateHz * info.channels * info.bitsPerSample;
    }
  }
  if (sampleRateHz === null && (fmt === "wav" || (head[0] === 0x52 && head[1] === 0x49))) {
    const wav = parseWavFmt(head);
    if (wav) {
      sampleRateHz = wav.sampleRateHz;
      headerBitrateBps = wav.byteRateBps;
    }
  }
  // The true file bitrate is size/duration; for lossy containers the frame
  // header is the more accurate figure (a ?start= transcode has no reliable
  // total), so it wins there.
  const sizeBitrateBps =
    totalBytes !== null && durationSecs && durationSecs > 0 ? (totalBytes * 8) / durationSecs : null;
  const isLossy = fmt === "mp3" || fmt === "mp3pro" || fmt === "aac" || fmt === "ogg" || fmt === "m4a" || fmt === "opus";
  const bitrateBps = isLossy
    ? (headerBitrateBps ?? sizeBitrateBps)
    : (sizeBitrateBps ?? headerBitrateBps);

  return {
    bitrateKbps: bitrateBps === null ? null : Math.round(bitrateBps / 1000),
    sampleRateHz,
  };
}
