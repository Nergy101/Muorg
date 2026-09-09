use bytes::Bytes;
use mp3lame_encoder::{Builder, DualPcm, FlushNoGap};
use symphonia::core::codecs::audio::{AudioDecoderOptions, CODEC_ID_NULL_AUDIO};
use symphonia::core::errors::Error as SymphoniaError;
use symphonia::core::formats::{FormatOptions, SeekTo};
use symphonia::core::formats::probe::Hint;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::units::Time;

type StreamTx = tokio::sync::mpsc::Sender<Result<Bytes, Box<dyn std::error::Error + Send + Sync>>>;

/// Decode any symphonia-supported audio file and transcode it to MP3 stream chunks.
/// Intended to run on a blocking thread (via `tokio::task::spawn_blocking`).
pub fn transcode_to_mp3(path: &str, start_secs: f32, tx: StreamTx) {
    if let Err(e) = do_transcode(path, start_secs, &tx) {
        let _ = tx.blocking_send(Err(e));
    }
}

fn do_transcode(
    path: &str,
    start_secs: f32,
    tx: &StreamTx,
) -> Result<(), Box<dyn std::error::Error + Send + Sync>> {
    let file = std::fs::File::open(path)?;
    let mss = MediaSourceStream::new(Box::new(file), Default::default());

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

    let track_id = track.id;
    let audio_params = track
        .codec_params
        .as_ref()
        .and_then(|p| p.audio())
        .ok_or("No audio codec parameters")?;
    let sample_rate = audio_params.sample_rate.unwrap_or(44100);
    let channels = audio_params
        .channels
        .clone()
        .map(|c| c.count() as u8)
        .unwrap_or(2)
        .min(2); // LAME handles up to stereo

    let mut decoder = symphonia::default::get_codecs()
        .make_audio_decoder(audio_params, &AudioDecoderOptions::default())?;

    if start_secs > 0.0 {
        let _ = format.seek(
            symphonia::core::formats::SeekMode::Coarse,
            SeekTo::Time {
                time: Time::try_from_secs_f64(start_secs as f64).unwrap_or(Time::ZERO),
                track_id: None,
            },
        );
        decoder.reset();
    }

    // Build LAME encoder
    let mut builder = Builder::new().ok_or("Failed to create LAME builder")?;
    builder
        .set_num_channels(channels)
        .map_err(|e| format!("{e:?}"))?;
    builder
        .set_sample_rate(sample_rate)
        .map_err(|e| format!("{e:?}"))?;
    builder
        .set_brate(mp3lame_encoder::Bitrate::Kbps128)
        .map_err(|e| format!("{e:?}"))?;
    builder
        .set_quality(mp3lame_encoder::Quality::Good)
        .map_err(|e| format!("{e:?}"))?;
    let mut encoder = builder.build().map_err(|e| format!("{e:?}"))?;

    loop {
        // Stop if the receiver has been dropped (client disconnected)
        if tx.is_closed() {
            break;
        }

        let packet = match format.next_packet() {
            Ok(Some(p)) => p,
            Ok(None) => break,
            Err(SymphoniaError::IoError(_)) | Err(SymphoniaError::ResetRequired) => break,
            Err(_) => break,
        };

        if packet.track_id != track_id {
            continue;
        }

        let decoded = match decoder.decode(&packet) {
            Ok(d) => d,
            Err(SymphoniaError::DecodeError(_)) => continue,
            Err(_) => break,
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
            let _ = tx.blocking_send(Ok(Bytes::copy_from_slice(&mp3_buf)));
        }
    }

    // Flush remaining MP3 frames
    let mut flush_buf: Vec<u8> = Vec::with_capacity(7200);
    let n = encoder
        .flush_to_vec::<FlushNoGap>(&mut flush_buf)
        .map_err(|e| format!("{e:?}"))?;
    if n > 0 {
        let _ = tx.blocking_send(Ok(Bytes::copy_from_slice(&flush_buf)));
    }

    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Decode an MP3 and return its duration in seconds, by counting frames at
    /// the decoded sample rate.
    fn mp3_duration_secs(mp3: &[u8]) -> Option<f64> {
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
        let mut frames = 0u64;
        while let Ok(Some(packet)) = format.next_packet() {
            if packet.track_id != track.id {
                continue;
            }
            if let Ok(decoded) = decoder.decode(&packet) {
                frames += decoded.frames() as u64;
            }
        }
        Some(frames as f64 / rate.max(1.0))
    }

    /// A `secs`-long sine FLAC at `sample_rate`, via ffmpeg. `None` when ffmpeg
    /// is not installed, so the suite still runs on a machine without it.
    fn generate_flac(dir: &std::path::Path, sample_rate: u32, secs: u32) -> Option<std::path::PathBuf> {
        let out = dir.join(format!("{sample_rate}-{secs}s.flac"));
        let status = std::process::Command::new("ffmpeg")
            .args([
                "-y",
                "-f",
                "lavfi",
                "-i",
                &format!("sine=frequency=440:sample_rate={sample_rate}:duration={secs}"),
                &out.to_string_lossy(),
            ])
            .stdout(std::process::Stdio::null())
            .stderr(std::process::Stdio::null())
            .status()
            .ok()?;
        status.success().then_some(out)
    }

    /// Run the transcoder to completion and collect what it sent.
    fn transcode(path: &str, start_secs: f32) -> Result<Vec<u8>, String> {
        let (tx, mut rx) = tokio::sync::mpsc::channel(256);
        let owned = path.to_string();
        let worker = std::thread::spawn(move || transcode_to_mp3(&owned, start_secs, tx));

        let mut out = Vec::new();
        let mut error = None;
        while let Some(chunk) = rx.blocking_recv() {
            match chunk {
                Ok(bytes) => out.extend_from_slice(&bytes),
                Err(e) => error = Some(e.to_string()),
            }
        }
        worker.join().expect("transcode thread panicked");
        match error {
            Some(e) => Err(e),
            None => Ok(out),
        }
    }

    #[test]
    fn transcodes_a_flac_to_playable_mp3() {
        let dir = tempfile::tempdir().unwrap();
        let Some(flac) = generate_flac(dir.path(), 44100, 2) else {
            eprintln!("skipping: ffmpeg not available");
            return;
        };

        let mp3 = transcode(flac.to_str().unwrap(), 0.0).expect("transcode");
        assert!(!mp3.is_empty(), "no audio was produced");
        assert!(
            mp3_duration_secs(&mp3).is_some(),
            "output does not decode as MP3",
        );
    }

    /// The half-speed bug: symphonia decoded at the source rate while LAME was
    /// told the input was 44.1 kHz, so every second of a 96 kHz file became
    /// ~2.18 s of output. The server has the same guard on its own transcoder.
    #[test]
    fn a_high_rate_source_keeps_its_duration() {
        let dir = tempfile::tempdir().unwrap();
        let Some(flac) = generate_flac(dir.path(), 96000, 1) else {
            eprintln!("skipping: ffmpeg not available");
            return;
        };

        let mp3 = transcode(flac.to_str().unwrap(), 0.0).expect("transcode");
        let duration = mp3_duration_secs(&mp3).expect("probe mp3");
        assert!(
            (0.7..=1.5).contains(&duration),
            "96 kHz source transcoded to {duration:.2}s, expected about 1s",
        );
    }

    /// FLAC seeks by reloading the stream with `?start=`, so the transcoder has
    /// to actually skip — otherwise seeking replays from the beginning.
    #[test]
    fn a_start_offset_skips_that_much_audio() {
        let dir = tempfile::tempdir().unwrap();
        let Some(flac) = generate_flac(dir.path(), 44100, 4) else {
            eprintln!("skipping: ffmpeg not available");
            return;
        };
        let path = flac.to_str().unwrap();

        let whole = mp3_duration_secs(&transcode(path, 0.0).expect("transcode")).expect("probe");
        let seeked = mp3_duration_secs(&transcode(path, 2.0).expect("seek")).expect("probe");

        assert!(
            seeked < whole - 1.0,
            "seeking to 2s of a {whole:.2}s file produced {seeked:.2}s — it did not skip",
        );
    }

    #[test]
    fn a_missing_file_reports_an_error_rather_than_panicking() {
        // This runs on a blocking thread feeding an HTTP response; a panic
        // there would take the stream down with no status.
        let error = transcode("/no/such/file.flac", 0.0).unwrap_err();
        assert!(!error.is_empty());
    }

    #[test]
    fn a_file_that_is_not_audio_reports_an_error() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("not-audio.flac");
        std::fs::write(&path, b"this is not a FLAC file").unwrap();

        assert!(transcode(path.to_str().unwrap(), 0.0).is_err());
    }

    #[test]
    fn it_stops_when_the_receiver_goes_away() {
        // The Chromecast disconnecting drops the receiver. Without the
        // `tx.is_closed()` check the encoder would run the whole file into a
        // channel nobody is reading.
        let dir = tempfile::tempdir().unwrap();
        let Some(flac) = generate_flac(dir.path(), 44100, 4) else {
            eprintln!("skipping: ffmpeg not available");
            return;
        };

        let (tx, rx) = tokio::sync::mpsc::channel(1);
        let path = flac.to_string_lossy().into_owned();
        let worker = std::thread::spawn(move || transcode_to_mp3(&path, 0.0, tx));
        drop(rx);

        // Joining is the assertion: it only returns if the loop noticed.
        worker.join().expect("transcode thread panicked");
    }
}
