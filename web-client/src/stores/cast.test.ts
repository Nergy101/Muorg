import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { createPinia, setActivePinia } from "pinia";
import type { CastDevice, CastSessionStatus } from "@shared/api";

/**
 * The cast store's job is a polling state machine: start a session, follow it,
 * and — importantly — stop following once it ends, in every way it can end.
 * A session that never stops polling leaves the UI claiming to cast forever.
 */
const castStatus = vi.fn();
const castPlay = vi.fn();
const castStop = vi.fn();
const castPause = vi.fn();
const castResume = vi.fn();
const castSeek = vi.fn();
const castSetVolume = vi.fn();
const castDevices = vi.fn();
const castStartDiscovery = vi.fn();
const castStopDiscovery = vi.fn();

vi.mock("../api/client", () => ({
  api: {
    castStatus: () => castStatus(),
    castPlay: (...a: unknown[]) => castPlay(...a),
    castStop: () => castStop(),
    castPause: () => castPause(),
    castResume: () => castResume(),
    castSeek: (...a: unknown[]) => castSeek(...a),
    castSetVolume: (...a: unknown[]) => castSetVolume(...a),
    castDevices: () => castDevices(),
    castStartDiscovery: () => castStartDiscovery(),
    castStopDiscovery: () => castStopDiscovery(),
  },
}));

const { useCastStore } = await import("./cast");

const device: CastDevice = {
  id: "d1",
  name: "Living Room",
  address: "10.0.0.5",
  port: 8009,
};

const session = (s: CastSessionStatus, volume = 1) => ({ session: s, volume });

/** Advance the poll timer and let the awaited request resolve. */
async function tick(ms = 1000) {
  await vi.advanceTimersByTimeAsync(ms);
}

beforeEach(() => {
  setActivePinia(createPinia());
  vi.clearAllMocks();
  vi.useFakeTimers();
  for (const fn of [castPlay, castStop, castPause, castResume, castSeek, castSetVolume, castStartDiscovery, castStopDiscovery]) {
    fn.mockResolvedValue(undefined);
  }
  castDevices.mockResolvedValue([device]);
  castStatus.mockResolvedValue(session({ status: "playing", position_secs: 1 }));
});

afterEach(() => {
  vi.useRealTimers();
});

describe("discovery", () => {
  it("starts a sweep and lists what it finds", async () => {
    const cast = useCastStore();
    await cast.startDiscovery();

    expect(castStartDiscovery).toHaveBeenCalled();
    expect(cast.devices).toEqual([device]);
    expect(cast.discovering).toBe(true);
    cast.dispose();
  });

  it("keeps refreshing the list while the picker is open", async () => {
    const cast = useCastStore();
    await cast.startDiscovery();
    castDevices.mockClear();

    await tick(2000);
    expect(castDevices).toHaveBeenCalled();
    cast.dispose();
  });

  it("stops sweeping when the picker closes", async () => {
    const cast = useCastStore();
    await cast.startDiscovery();
    await cast.stopDiscovery();
    castDevices.mockClear();

    await tick(6000);
    expect(castDevices).not.toHaveBeenCalled();
    expect(castStopDiscovery).toHaveBeenCalled();
    expect(cast.discovering).toBe(false);
  });

  it("surfaces a discovery failure instead of hanging on 'looking'", async () => {
    castStartDiscovery.mockRejectedValueOnce(new Error("mDNS unavailable"));
    const cast = useCastStore();
    await cast.startDiscovery();

    expect(cast.error).toBe("mDNS unavailable");
    expect(cast.discovering).toBe(false);
  });
});

describe("starting a session", () => {
  it("sends the device address, port and track", async () => {
    const cast = useCastStore();
    await cast.play(device, 42);

    expect(castPlay).toHaveBeenCalledWith("10.0.0.5", 8009, 42);
    expect(cast.device).toEqual(device);
    cast.dispose();
  });

  it("reports casting while connecting, before the first status lands", async () => {
    const cast = useCastStore();
    await cast.play(device, 42);
    expect(cast.isCasting).toBe(true);
    cast.dispose();
  });

  it("rolls back cleanly when the device cannot be reached", async () => {
    castPlay.mockRejectedValueOnce(new Error("no route to host"));
    const cast = useCastStore();
    await cast.play(device, 42);

    expect(cast.error).toBe("no route to host");
    expect(cast.isCasting).toBe(false);
    expect(cast.device).toBeNull();
  });

  it("follows the session once it is playing", async () => {
    const cast = useCastStore();
    await cast.play(device, 42);
    await tick();

    expect(cast.isPlaying).toBe(true);
    expect(cast.positionSecs).toBe(1);
    cast.dispose();
  });
});

describe("ending a session", () => {
  it("stops polling and clears the device when the track finishes", async () => {
    const cast = useCastStore();
    await cast.play(device, 42);
    await tick();

    castStatus.mockResolvedValue(session({ status: "stopped", finished: true }));
    await tick();

    expect(cast.isCasting).toBe(false);
    expect(cast.device).toBeNull();

    castStatus.mockClear();
    await tick(5000);
    expect(castStatus).not.toHaveBeenCalled();
  });

  it("advances the queue only when the track actually finished", async () => {
    const cast = useCastStore();
    const ended = vi.fn();
    cast.onTrackEnded(ended);

    await cast.play(device, 42);
    await tick();
    castStatus.mockResolvedValue(session({ status: "stopped", finished: true }));
    await tick();

    expect(ended).toHaveBeenCalledTimes(1);
  });

  it("does not advance the queue when the user stopped it", async () => {
    const cast = useCastStore();
    const ended = vi.fn();
    cast.onTrackEnded(ended);

    await cast.play(device, 42);
    await tick();
    castStatus.mockResolvedValue(session({ status: "stopped", finished: false }));
    await tick();

    expect(ended).not.toHaveBeenCalled();
  });

  it("stops polling when the device reports an error", async () => {
    const cast = useCastStore();
    await cast.play(device, 42);
    await tick();

    castStatus.mockResolvedValue(session({ status: "error", message: "device gone" }));
    await tick();

    expect(cast.error).toBe("device gone");
    expect(cast.device).toBeNull();

    castStatus.mockClear();
    await tick(5000);
    expect(castStatus).not.toHaveBeenCalled();
  });

  it("gives up rather than polling a server that stopped answering", async () => {
    const cast = useCastStore();
    await cast.play(device, 42);
    await tick();

    castStatus.mockRejectedValue(new Error("Failed to fetch"));
    await tick();

    expect(cast.isCasting).toBe(false);
    expect(cast.error).toBe("Failed to fetch");

    castStatus.mockClear();
    await tick(5000);
    expect(castStatus).not.toHaveBeenCalled();
  });

  it("stop() tears the session down immediately", async () => {
    const cast = useCastStore();
    await cast.play(device, 42);
    await tick();

    await cast.stop();

    expect(castStop).toHaveBeenCalled();
    expect(cast.isCasting).toBe(false);
    expect(cast.device).toBeNull();
  });

  it("still resets locally when the stop request fails", async () => {
    castStop.mockRejectedValueOnce(new Error("offline"));
    const cast = useCastStore();
    await cast.play(device, 42);
    await cast.stop();

    expect(cast.isCasting).toBe(false);
  });
});

describe("transport", () => {
  it("pauses and resumes on the device", async () => {
    const cast = useCastStore();
    await cast.play(device, 42);

    await cast.pause();
    expect(castPause).toHaveBeenCalled();
    await cast.resume();
    expect(castResume).toHaveBeenCalled();
    cast.dispose();
  });

  it("tells the server whether it was playing when seeking", async () => {
    // The server needs this to decide whether to resume after the seek.
    const cast = useCastStore();
    await cast.play(device, 42);
    await tick();

    await cast.seek(30);
    expect(castSeek).toHaveBeenCalledWith(30, true);
    cast.dispose();
  });

  it("clamps volume into 0..1 and applies it optimistically", async () => {
    const cast = useCastStore();
    await cast.play(device, 42);

    await cast.setVolume(1.5);
    expect(castSetVolume).toHaveBeenCalledWith(1);
    await cast.setVolume(-1);
    expect(castSetVolume).toHaveBeenLastCalledWith(0);
    cast.dispose();
  });

  it("has no position to report before the device says where it is", async () => {
    castStatus.mockResolvedValue(session({ status: "playing" }));
    const cast = useCastStore();
    await cast.play(device, 42);
    await tick();

    expect(cast.positionSecs).toBeNull();
    cast.dispose();
  });
});
