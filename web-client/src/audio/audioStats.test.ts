import { describe, expect, it, vi, afterEach } from "vitest";
import {
  parseFlacStreamInfo,
  parseMp3Frame,
  parseWavFmt,
  probeAudioStats,
  totalBytesFromContentRange,
} from "./audioStats";

/** MPEG1 Layer III frame header: 128 kbps, 44100 Hz, no padding. */
function mp3FrameHeader(bitrateIndex: number, srIndex: number): Uint8Array {
  return new Uint8Array([0xff, 0xfb, (bitrateIndex << 4) | (srIndex << 2), 0x00]);
}

describe("parseMp3Frame", () => {
  it("decodes a MPEG1 Layer III frame (128 kbps, 44100 Hz)", () => {
    // Frame length = floor(144 * 128000 / 44100) = 417; a second sync there
    // confirms the first header is real.
    const bytes = new Uint8Array(417 + 4);
    bytes.set(mp3FrameHeader(9, 0), 0);
    bytes.set(mp3FrameHeader(9, 0), 417);
    const frame = parseMp3Frame(bytes);
    expect(frame).toEqual({ bitrateBps: 128000, sampleRateHz: 44100 });
  });

  it("decodes a MPEG2 Layer III frame (64 kbps, 24000 Hz)", () => {
    // version bits 10 (MPEG2), layer 01 (III): byte1 = 1111 1011 -> 0xfb is
    // MPEG1; MPEG2 is 0xf3. Frame length = floor(144*64000/24000) = 384.
    const bytes = new Uint8Array(384 + 4);
    bytes.set(new Uint8Array([0xff, 0xf3, (8 << 4) | (1 << 2), 0x00]), 0);
    bytes.set(new Uint8Array([0xff, 0xf3, (8 << 4) | (1 << 2), 0x00]), 384);
    const frame = parseMp3Frame(bytes);
    expect(frame).toEqual({ bitrateBps: 64000, sampleRateHz: 24000 });
  });

  it("skips an ID3 tag and finds the first real frame", () => {
    const bytes = new Uint8Array(10 + 417 + 4);
    bytes.set([0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00], 0); // "ID3"
    bytes.set(mp3FrameHeader(9, 0), 10);
    bytes.set(mp3FrameHeader(9, 0), 10 + 417);
    expect(parseMp3Frame(bytes)?.sampleRateHz).toBe(44100);
  });

  it("returns null when no frame sync exists", () => {
    expect(parseMp3Frame(new Uint8Array([1, 2, 3, 4, 5]))).toBeNull();
  });
});

/** Minimal FLAC: "fLaC" + STREAMINFO block header + 34-byte STREAMINFO. */
function flacStreamInfo(sampleRateHz: number, channels: number, bitsPerSample: number): Uint8Array {
  const bytes = new Uint8Array(8 + 34);
  bytes.set([0x66, 0x4c, 0x61, 0x43], 0); // fLaC
  bytes[4] = 0x00; // not last block, type 0 (STREAMINFO)
  bytes[5] = 0x00; bytes[6] = 0x00; bytes[7] = 34; // block length
  const rate = sampleRateHz; // 20 bits across bytes 18-20
  bytes[18] = (rate >> 12) & 0xff;
  bytes[19] = (rate >> 4) & 0xff;
  bytes[20] = ((rate & 0x0f) << 4) | ((channels - 1) << 1) | ((bitsPerSample - 1) >> 4);
  bytes[21] = ((bitsPerSample - 1) & 0x0f) << 4;
  return bytes;
}

describe("parseFlacStreamInfo", () => {
  it("decodes 44.1 kHz / 2ch / 16-bit STREAMINFO", () => {
    const info = parseFlacStreamInfo(flacStreamInfo(44100, 2, 16));
    expect(info).toEqual({ sampleRateHz: 44100, channels: 2, bitsPerSample: 16 });
  });

  it("decodes 96 kHz / 2ch / 24-bit STREAMINFO", () => {
    const info = parseFlacStreamInfo(flacStreamInfo(96000, 2, 24));
    expect(info).toEqual({ sampleRateHz: 96000, channels: 2, bitsPerSample: 24 });
  });

  it("returns null without the fLaC magic", () => {
    expect(parseFlacStreamInfo(new Uint8Array(42))).toBeNull();
  });
});

/** Minimal RIFF/WAVE with a 16-byte PCM fmt chunk. */
function wavFile(sampleRateHz: number, channels: number, bitsPerSample: number): Uint8Array {
  const byteRate = sampleRateHz * channels * (bitsPerSample / 8);
  const bytes = new Uint8Array(44);
  const view = new DataView(bytes.buffer);
  bytes.set([0x52, 0x49, 0x46, 0x46], 0); // RIFF
  view.setUint32(4, 36, true);
  bytes.set([0x57, 0x41, 0x56, 0x45], 8); // WAVE
  bytes.set([0x66, 0x6d, 0x74, 0x20], 12); // fmt 
  view.setUint32(16, 16, true);
  view.setUint16(20, 1, true); // PCM
  view.setUint16(22, channels, true);
  view.setUint32(24, sampleRateHz, true);
  view.setUint32(28, byteRate, true);
  view.setUint16(32, channels * (bitsPerSample / 8), true);
  view.setUint16(34, bitsPerSample, true);
  return bytes;
}

describe("parseWavFmt", () => {
  it("decodes a 48 kHz / 2ch / 16-bit PCM fmt chunk", () => {
    const fmt = parseWavFmt(wavFile(48000, 2, 16));
    expect(fmt).toEqual({ sampleRateHz: 48000, channels: 2, bitsPerSample: 16, byteRateBps: 192000 });
  });
});

describe("totalBytesFromContentRange", () => {
  it("reads the total from a 206 Content-Range", () => {
    expect(totalBytesFromContentRange("bytes 0-65535/4123456")).toBe(4123456);
  });
  it("returns null for a 200 response (no header)", () => {
    expect(totalBytesFromContentRange(null)).toBeNull();
  });
});

describe("probeAudioStats", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function stubFetch(body: Uint8Array, contentRange: string | null) {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(body as BodyInit, {
      status: contentRange ? 206 : 200,
      headers: contentRange ? { "Content-Range": contentRange } : {},
    })));
  }

  it("reads bitrate and sample rate from an MP3 stream header", async () => {
    const bytes = new Uint8Array(417 + 4);
    bytes.set(mp3FrameHeader(11, 1), 0); // 192 kbps, 48000 Hz
    bytes.set(mp3FrameHeader(11, 1), 417);
    stubFetch(bytes, "bytes 0-420/1000000");
    const stats = await probeAudioStats("http://x/stream/t/1", "mp3", 300);
    expect(stats).toEqual({ bitrateKbps: 192, sampleRateHz: 48000 });
  });

  it("derives FLAC file bitrate from total size and duration", async () => {
    stubFetch(flacStreamInfo(44100, 2, 16), "bytes 0-41/8820000"); // 8820000 B / 200 s = 352.8 kbps
    const stats = await probeAudioStats("http://x/stream/flac/t/1", "flac", 200);
    expect(stats).toEqual({ bitrateKbps: 353, sampleRateHz: 44100 });
  });

  it("falls back to the PCM rate for FLAC when no total size is known", async () => {
    stubFetch(flacStreamInfo(44100, 2, 16), null); // 200 response, no Content-Range
    const stats = await probeAudioStats("http://x/stream/flac/t/1", "flac", null);
    expect(stats).toEqual({ bitrateKbps: 1411, sampleRateHz: 44100 });
  });

  it("falls back to size/duration for unknown containers", async () => {
    stubFetch(new Uint8Array([0x4f, 0x67, 0x67, 0x53]), "bytes 0-3/2500000"); // "OggS"
    const stats = await probeAudioStats("http://x/stream/t/1", "ogg", 100);
    expect(stats).toEqual({ bitrateKbps: 200, sampleRateHz: null });
  });
});
