import { ref, watch } from "vue";
import { usePlayerStore } from "../stores/player";

/**
 * YouTube-style popout mini player via the Picture-in-Picture API.
 *
 * Zen/Firefox has no Document Picture-in-Picture, so this renders the cover
 * art + title + progress onto a canvas, combines that video track with the
 * live audio track of the player's <audio> element, and opens the result in
 * a PiP window on a hidden <video>. While the popout is open the original
 * audio element is muted so the track is not heard twice.
 *
 * Module-level state: MiniPlayer and PlayerView share one popout session.
 */

const pipActive = ref(false);
const supported =
  typeof document !== "undefined" &&
  "pictureInPictureEnabled" in document &&
  document.pictureInPictureEnabled;

let video: HTMLVideoElement | null = null;
let canvas: HTMLCanvasElement | null = null;
let coverImg: HTMLImageElement | null = null;
let drawTimer = 0;
// The popout video's stream and its audio track, kept so the audio track can
// be swapped when the store replaces the active <audio> element mid-session.
let popoutStream: MediaStream | null = null;
let popoutAudioTrack: MediaStreamTrack | null = null;

function drawFrame(player: ReturnType<typeof usePlayerStore>): void {
  if (!canvas) return;
  const ctx = canvas.getContext("2d");
  if (!ctx) return;
  const W = canvas.width;
  const H = canvas.height;

  ctx.fillStyle = "#121212";
  ctx.fillRect(0, 0, W, H);

  // Cover art fills the whole square (object-fit: cover, centred crop).
  if (coverImg && coverImg.complete && coverImg.naturalWidth > 0) {
    const scale = Math.max(W / coverImg.naturalWidth, H / coverImg.naturalHeight);
    const drawW = coverImg.naturalWidth * scale;
    const drawH = coverImg.naturalHeight * scale;
    ctx.drawImage(coverImg, (W - drawW) / 2, (H - drawH) / 2, drawW, drawH);
  } else {
    ctx.fillStyle = "#2a2a2a";
    ctx.fillRect(0, 0, W, H);
  }

  // Dark scrim over the bottom part so the text always sits on a shadow.
  const scrimTop = Math.round(H * 0.55);
  const scrim = ctx.createLinearGradient(0, scrimTop, 0, H);
  scrim.addColorStop(0, "rgba(0,0,0,0)");
  scrim.addColorStop(1, "rgba(0,0,0,0.85)");
  ctx.fillStyle = scrim;
  ctx.fillRect(0, scrimTop, W, H - scrimTop);

  const pad = 20;
  const maxTextW = W - pad * 2;
  ctx.fillStyle = "#ffffff";
  ctx.font = "bold 26px system-ui, sans-serif";
  ctx.fillText(player.currentTrack?.title ?? "—", pad, H - 64, maxTextW);
  ctx.fillStyle = "rgba(255,255,255,0.7)";
  ctx.font = "20px system-ui, sans-serif";
  const artist = player.currentTrack?.artist ?? player.currentTrack?.album_artist ?? "";
  const album = player.currentTrack?.album ?? "";
  ctx.fillText([artist, album].filter(Boolean).join(" · "), pad, H - 34, maxTextW);

  ctx.fillStyle = "rgba(255,255,255,0.25)";
  ctx.fillRect(0, H - 6, W, 6);
  ctx.fillStyle = "#ffffff";
  ctx.fillRect(0, H - 6, W * player.progress, 6);
}

function loadCover(player: ReturnType<typeof usePlayerStore>): void {
  const url = player.currentCoverUrl;
  if (!url) {
    coverImg = null;
    return;
  }
  const img = new Image();
  // Needed so the canvas stays untainted (captureStream throws otherwise)
  // when the art is served from another origin than the web client.
  img.crossOrigin = "anonymous";
  img.onload = () => {
    coverImg = img;
    drawFrame(player);
  };
  img.onerror = () => {
    coverImg = null;
    drawFrame(player);
  };
  img.src = url;
}

function updateMediaSession(player: ReturnType<typeof usePlayerStore>): void {
  if (!("mediaSession" in navigator)) return;
  const t = player.currentTrack;
  navigator.mediaSession.metadata = new MediaMetadata({
    title: t?.title ?? "",
    artist: t?.artist ?? t?.album_artist ?? "",
    album: t?.album ?? "",
    artwork: player.currentCoverUrl
      ? [{ src: player.currentCoverUrl, sizes: "512x512" }]
      : [],
  });
}

function ensureVideo(player: ReturnType<typeof usePlayerStore>): HTMLVideoElement {
  if (video) return video;
  canvas = document.createElement("canvas");
  canvas.width = 480;
  canvas.height = 480;

  video = document.createElement("video");
  video.playsInline = true;
  video.style.display = "none";
  video.addEventListener("enterpictureinpicture", () => {
    pipActive.value = true;
    // Mirror the audio element's *effective* volume (user volume + ReplayGain)
    // onto the popout, so opening/closing never changes how loud it is.
    if (player.audioEl) {
      video!.volume = player.audioEl.volume;
      player.audioEl.muted = true;
    }
  });
  video.addEventListener("leavepictureinpicture", () => {
    pipActive.value = false;
    if (player.audioEl) player.audioEl.muted = false;
    window.clearInterval(drawTimer);
    drawTimer = 0;
    popoutStream = null;
    popoutAudioTrack = null;
  });
  document.body.appendChild(video);
  return video;
}

export function usePopoutPlayer() {
  const player = usePlayerStore();

  async function togglePopout(): Promise<void> {
    if (!supported) return;
    if (document.pictureInPictureElement) {
      await document.exitPictureInPicture();
      return;
    }
    const v = ensureVideo(player);
    if (!canvas) return;

    const stream = new MediaStream();
    popoutStream = stream;
    popoutAudioTrack = null;
    const videoTrack = canvas.captureStream(30).getVideoTracks()[0];
    if (videoTrack) stream.addTrack(videoTrack);
    // captureStream() on media elements is missing from this TS DOM lib.
    const el = player.audioEl as (HTMLAudioElement & { captureStream?: () => MediaStream }) | null;
    if (el?.captureStream) {
      try {
        for (const t of el.captureStream().getAudioTracks()) {
          stream.addTrack(t);
          popoutAudioTrack = t;
        }
      } catch {
        // Audio capture unsupported: fall back to a silent video-only popout.
      }
    }

    v.srcObject = stream;
    loadCover(player);
    drawFrame(player);
    drawTimer = window.setInterval(() => {
      drawFrame(player);
      // The store swaps the active <audio> element on track handoff and applies
      // ReplayGain per track, so re-sync the popout's volume every tick.
      if (player.audioEl) v.volume = player.audioEl.volume;
    }, 500);
    await v.play();
    try {
      await v.requestPictureInPicture();
    } catch {
      // Rejected (e.g. no user gesture): clean up so a retry starts fresh.
      window.clearInterval(drawTimer);
      drawTimer = 0;
      v.pause();
      v.srcObject = null;
    }
  }

  watch(
    () => [player.currentTrack?.id, player.currentCoverUrl] as const,
    () => {
      loadCover(player);
      drawFrame(player);
      updateMediaSession(player);
    },
    { immediate: true },
  );

  // The store swaps the active <audio> element on every track handoff, so the
  // popout's mute and captured audio track must follow the SESSION, not the
  // element that happened to be active when PiP opened. Otherwise the muted
  // flag sticks to a swapped-out element (page goes silent) and the popout's
  // captured track dies with the detached element's removed src.
  watch(
    () => player.audioEl,
    (el) => {
      if (!el) return;
      el.muted = pipActive.value;
      if (!pipActive.value || !video) return;
      video.volume = el.volume;
      const cap = (el as HTMLAudioElement & { captureStream?: () => MediaStream }).captureStream;
      if (!cap || !popoutStream) return;
      try {
        const next = cap.call(el).getAudioTracks()[0];
        if (!next) return;
        if (popoutAudioTrack) popoutStream.removeTrack(popoutAudioTrack);
        popoutStream.addTrack(next);
        popoutAudioTrack = next;
      } catch {
        // Audio capture unsupported on the new element: keep the old track.
      }
    },
  );

  if ("mediaSession" in navigator) {
    navigator.mediaSession.setActionHandler("play", () => player.playPause());
    navigator.mediaSession.setActionHandler("pause", () => player.playPause());
    navigator.mediaSession.setActionHandler("previoustrack", () => player.skipPrevious());
    navigator.mediaSession.setActionHandler("nexttrack", () => player.skipNext());
  }

  return { pipActive, supported, togglePopout };
}
