use mp3lame_encoder::{Bitrate, Builder, DualPcm, FlushNoGap};
use symphonia::core::codecs::audio::{AudioDecoderOptions, CODEC_ID_NULL_AUDIO};
use symphonia::core::errors::Error as SymphoniaError;
use symphonia::core::formats::FormatOptions;
use symphonia::core::formats::probe::Hint;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use crate::config::TranscodingConfig;

/// Where a transcode reads its input. Object-storage tracks arrive as a
/// `MediaSource` so `symphonia` pulls only the bytes it decodes instead of the
/// server downloading whole files.
pub enum TranscodeSource {
    LocalPath(String),
    Remote(Box<dyn symphonia::core::io::MediaSource>),
}

/// Decodes the whole source from the start and re-encodes it to a single MP3
/// byte buffer. LAME is ~10–20× realtime, so a cold pass is cheap; callers cache
/// the result (see `stream.rs`) and serve it with `Content-Length` + ranges so
/// the browser treats a FLAC track as a seekable file rather than a live stream.
pub fn transcode_to_mp3_bytes(
    source: TranscodeSource,
    config: &TranscodingConfig,
) -> Result<Vec<u8>, Box<dyn std::error::Error + Send + Sync>> {
    let (ms, _source_label): (Box<dyn symphonia::core::io::MediaSource>, String) = match source {
        TranscodeSource::LocalPath(p) => (Box::new(std::fs::File::open(&p)?), p),
        TranscodeSource::Remote(m) => (m, "<remote object>".to_string()),
    };
    let mss = MediaSourceStream::new(ms, Default::default());

    let mut format = symphonia::default::get_probe().probe(
        &Hint::new(),
        mss,
        FormatOptions::default(),
        MetadataOptions::default(),
    )?;

    let track = format
        .tracks()
        .iter()
        .find(|t| {
            t.codec_params
                .as_ref()
                .and_then(|p| p.audio())
                .map(|a| a.codec != CODEC_ID_NULL_AUDIO)
                .unwrap_or(false)
        })
        .ok_or("No audio track found")?
        .clone();

    // Record the source's declared duration so we can reject a transcode that
    // came out suspiciously short (a mid-stream read failure used to be treated
    // as clean EOF and cached as a truncated MP3 — songs "ending" early).
    let source_duration_secs = track
        .time_base
        .zip(track.num_frames)
        .map(|(tb, n)| tb.numer.get() as f64 * n as f64 / tb.denom.get() as f64);

    let track_id = track.id;
    let audio_params = track
        .codec_params
        .as_ref()
        .and_then(|p| p.audio())
        .ok_or("No audio codec parameters")?;
    let sample_rate = config.sample_rate;
    // The decoded samples arrive at the source's native rate (symphonia does
    // not resample). LAME must know that true input rate and be asked for the
    // desired output rate, or it reinterprets hi-res (88.2/96 kHz) FLAC as
    // 44.1 kHz and plays back slow. Fall back to the configured rate when the
    // codec doesn't declare one.
    let source_rate = audio_params.sample_rate.unwrap_or(sample_rate);
    let channels = audio_params
        .channels
        .clone()
        .map(|c| c.count() as u8)
        .unwrap_or(2)
        .min(2);

    let mut decoder = symphonia::default::get_codecs()
        .make_audio_decoder(audio_params, &AudioDecoderOptions::default())?;

    let mut builder = Builder::new().ok_or("Failed to create LAME builder")?;
    builder
        .set_num_channels(channels)
        .map_err(|e| format!("{e:?}"))?;
    builder
        .set_sample_rate(source_rate)
        .map_err(|e| format!("{e:?}"))?;
    builder
        .set_output_sample_rate(std::num::NonZeroU32::new(sample_rate))
        .map_err(|e| format!("{e:?}"))?;
    builder
        .set_brate(match config.bitrate {
            128 => Bitrate::Kbps128,
            160 => Bitrate::Kbps160,
            192 => Bitrate::Kbps192,
            256 => Bitrate::Kbps256,
            320 => Bitrate::Kbps320,
            _ => Bitrate::Kbps128,
        })
        .map_err(|e| format!("{e:?}"))?;
    builder
        .set_quality(mp3lame_encoder::Quality::Good)
        .map_err(|e| format!("{e:?}"))?;
    let mut encoder = builder.build().map_err(|e| format!("{e:?}"))?;

    let mut out: Vec<u8> = Vec::new();
    let mut hit_midstream_error: Option<String> = None;
    loop {
        let packet = match format.next_packet() {
            Ok(Some(p)) => p,
            Ok(None) => break,
            Err(SymphoniaError::ResetRequired) => {
                decoder.reset();
                continue;
            }
            // A mid-stream I/O failure (dropped remote read, truncated transfer)
            // is NOT clean EOF: returning the partial MP3 as Ok made the caller
            // cache a truncated file that then "ended" early on every request.
            Err(SymphoniaError::IoError(e)) if e.kind() == std::io::ErrorKind::UnexpectedEof => {
                break
            }
            Err(SymphoniaError::IoError(e)) => {
                hit_midstream_error = Some(format!("read error: {e}"));
                break;
            }
            Err(e) => {
                hit_midstream_error = Some(format!("format error: {e}"));
                break;
            }
        };

        if packet.track_id != track_id {
            continue;
        }

        let decoded = match decoder.decode(&packet) {
            Ok(d) => d,
            Err(SymphoniaError::DecodeError(_)) => continue,
            Err(SymphoniaError::ResetRequired) => {
                decoder.reset();
                continue;
            }
            Err(e) => {
                hit_midstream_error = Some(format!("decode error: {e}"));
                break;
            }
        };

        let mut samples: Vec<f32> = Vec::new();
        decoded.copy_to_vec_interleaved(&mut samples);

        let mut mp3_buf: Vec<u8> = Vec::new();
        let n_per_channel = samples.len() / channels.max(1) as usize;
        mp3_buf.reserve((n_per_channel * 5 / 4) + 7200);

        let n = if channels == 2 {
            let left: Vec<f32> = samples.iter().step_by(2).copied().collect();
            let right: Vec<f32> = samples.iter().skip(1).step_by(2).copied().collect();
            encoder
                .encode_to_vec(DualPcm { left: &left, right: &right }, &mut mp3_buf)
                .map_err(|e| format!("{e:?}"))?
        } else {
            let mono: Vec<f32> = samples.to_vec();
            encoder
                .encode_to_vec(DualPcm { left: &mono, right: &mono }, &mut mp3_buf)
                .map_err(|e| format!("{e:?}"))?
        };

        if n > 0 {
            out.extend_from_slice(&mp3_buf);
        }
    }

    let mut flush_buf: Vec<u8> = Vec::with_capacity(7200);
    let n = encoder
        .flush_to_vec::<FlushNoGap>(&mut flush_buf)
        .map_err(|e| format!("{e:?}"))?;
    if n > 0 {
        out.extend_from_slice(&flush_buf);
    }

    // Guard 1: an explicit mid-stream failure must not come back as a
    // (truncated) success.
    if let Some(err) = hit_midstream_error {
        return Err(format!(
            "transcode aborted mid-stream ({err}); refusing to return a truncated file"
        )
        .into());
    }

    // Guard 2: belt-and-braces — if the source declares a duration and the
    // output decodes far short of it, treat the transcode as failed rather than
    // cache a track that "ends" a minute early. Symphonia's n_frames estimate
    // can be absent or slightly off, so allow a generous 20% slack.
    if let Some(expected) = source_duration_secs {
        if expected > 10.0 {
            let actual = decoded_mp3_duration(&out).unwrap_or(0.0);
            if actual > 0.0 && actual < expected * 0.8 {
                return Err(format!(
                    "transcoded duration {actual:.1}s is far short of the source's {expected:.1}s — source likely truncated"
                )
                .into());
            }
        }
    }

    Ok(out)
}

/// Decoded duration of an MP3 buffer in seconds (None if it can't be probed).
fn decoded_mp3_duration(mp3: &[u8]) -> Option<f64> {
    let mss = MediaSourceStream::new(
        Box::new(std::io::Cursor::new(mp3.to_vec())),
        Default::default(),
    );
    let mut format = symphonia::default::get_probe()
        .probe(
            &Hint::new(),
            mss,
            FormatOptions::default(),
            MetadataOptions::default(),
        )
        .ok()?;
    let track = format.tracks().first()?.clone();
    let params = track.codec_params.as_ref()?.audio()?;
    let rate = params.sample_rate? as f64;
    let mut decoder = symphonia::default::get_codecs()
        .make_audio_decoder(params, &AudioDecoderOptions::default())
        .ok()?;
    let mut total_frames = 0u64;
    loop {
        let packet = match format.next_packet() {
            Ok(Some(p)) => p,
            _ => break,
        };
        if packet.track_id != track.id {
            continue;
        }
        if let Ok(decoded) = decoder.decode(&packet) {
            total_frames += decoded.frames() as u64;
        }
    }
    Some(total_frames as f64 / rate.max(1.0))
}

#[cfg(test)]
mod tests {
    use super::*;

    /// A 96 kHz FLAC, transposed through the real transcode path, must come out
    /// as a ~1s MP3 — not ~2.18s. This is the regression the half-speed bug
    /// caused: symphonia decoded at 96 kHz but LAME was told the input was
    /// 44.1 kHz, so every second of source became ~2.18s of encoded audio.
    #[test]
    fn transcode_high_res_flac_preserves_duration() {
        let flac = match generate_test_flac(96000) {
            Some(f) => f,
            None => {
                eprintln!("skipping: ffmpeg not available");
                return;
            }
        };
        let dir = tempfile::tempdir().expect("tempdir");
        let path = dir.path().join("hi-res.flac");
        std::fs::write(&path, &flac).expect("write flac");

        let mp3 = transcode_to_mp3_bytes(
            TranscodeSource::LocalPath(path.to_string_lossy().into_owned()),
            &TranscodingConfig::default(),
        )
        .expect("transcode");

        let duration = mp3_duration_secs(&mp3).expect("probe mp3");
        // Source is 1s at 96 kHz; correct output is ≈1s. The bug produced ~2.18s.
        assert!(
            (0.7..=1.5).contains(&duration),
            "96 kHz FLAC transcoded to {duration:.2}s, expected ≈1s (half-speed bug)",
        );
    }

    /// Generate a 1-second sine FLAC at `sample_rate` Hz via ffmpeg.
    fn generate_test_flac(sample_rate: u32) -> Option<Vec<u8>> {
        let dir = tempfile::tempdir().ok()?;
        let src = dir.path().join("in.wav");
        let out = dir.path().join("out.flac");
        let status = std::process::Command::new("ffmpeg")
            .args([
                "-y", "-f", "lavfi",
                "-i", &format!("sine=frequency=440:sample_rate={sample_rate}:duration=1"),
                &src.to_string_lossy().into_owned(),
            ])
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status()
            .ok()?;
        if !status.success() {
            return None;
        }
        let status = std::process::Command::new("ffmpeg")
            .args(["-y", "-i", &src.to_string_lossy().into_owned(), &out.to_string_lossy().into_owned()])
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status()
            .ok()?;
        if !status.success() {
            return None;
        }
        std::fs::read(&out).ok()
    }

    /// Probe an MP3's duration (seconds) by decoding it with symphonia and
    /// counting samples at the decoded sample rate.
    fn mp3_duration_secs(mp3: &[u8]) -> Option<f64> {
        let mss = MediaSourceStream::new(Box::new(std::io::Cursor::new(mp3.to_vec())), Default::default());
        let mut format = symphonia::default::get_probe()
            .probe(
                &Hint::new(),
                mss,
                FormatOptions::default(),
                MetadataOptions::default(),
            )
            .ok()?;
        let track = format.tracks().first()?.clone();
        let params = track.codec_params.as_ref()?.audio()?;
        let rate = params.sample_rate? as f64;
        let mut decoder = symphonia::default::get_codecs()
            .make_audio_decoder(&params, &AudioDecoderOptions::default())
            .ok()?;
        let mut total_frames = 0u64;
        loop {
            let packet = match format.next_packet() {
                Ok(Some(p)) => p,
                _ => break,
            };
            if packet.track_id != track.id {
                continue;
            }
            if let Ok(decoded) = decoder.decode(&packet) {
                total_frames += decoded.frames() as u64;
            }
        }
        Some(total_frames as f64 / rate.max(1.0))
    }
}
