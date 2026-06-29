use bytes::Bytes;
use mp3lame_encoder::{Builder, DualPcm, FlushNoGap};
use symphonia::core::codecs::audio::{AudioDecoderOptions, CODEC_ID_NULL_AUDIO};
use symphonia::core::errors::Error as SymphoniaError;
use symphonia::core::formats::{FormatOptions, SeekMode, SeekTo};
use symphonia::core::formats::probe::Hint;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::units::Time;

type StreamTx = tokio::sync::mpsc::Sender<Result<Bytes, Box<dyn std::error::Error + Send + Sync>>>;

pub fn transcode_to_mp3(path: &str, start_secs: f32, tx: StreamTx) {
    if let Err(e) = do_transcode(path, start_secs, &tx) {
        let _ = tx.blocking_send(Err(e));
    }
}

/// Helper: pull the next packet, skipping the `Option` wrapper.
fn next_packet(
    format: &mut dyn symphonia::core::formats::FormatReader,
) -> Result<Option<symphonia::core::packet::Packet>, symphonia::core::errors::Error> {
    match format.next_packet() {
        Ok(Some(p)) => Ok(Some(p)),
        Ok(None) => Ok(None),
        Err(e) => Err(e),
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
    let time_base = track.time_base;
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
        .min(2);

    let mut decoder = symphonia::default::get_codecs()
        .make_audio_decoder(audio_params, &AudioDecoderOptions::default())?;

    // Seek to start_secs and return the first packet that covers the target timestamp.
    let first_seek_packet = if start_secs > 0.0 {
        let target_secs = start_secs as f64;

        tracing::info!(path, start_secs, "seek requested");

        let _seek_result = format.seek(
            SeekMode::Coarse,
            SeekTo::Time {
                time: Time::try_from_secs_f64(target_secs).unwrap_or(Time::ZERO),
                track_id: Some(track_id),
            },
        );

        // Use a timestamp-based target for packet scanning
        let sample_rate_f = sample_rate as f64;
        let target_ts_val = (target_secs * sample_rate_f) as i64;
        let target_ts = symphonia::core::units::Timestamp::from(target_ts_val);

        let mut skipped = 0u64;
        let mut found = None;
        loop {
            let packet = match next_packet(&mut *format) {
                Ok(Some(p)) => p,
                Ok(None) => break,
                Err(SymphoniaError::ResetRequired) => {
                    decoder.reset();
                    continue;
                }
                Err(_) => break,
            };
            if packet.track_id != track_id {
                continue;
            }
            if packet.pts.saturating_add(packet.dur) >= target_ts {
                found = Some(packet);
                break;
            }
            skipped += 1;
        }
        tracing::info!(path, skipped_packets = skipped, "fine-skip done");
        found
    } else {
        None
    };

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

    let mut pending = first_seek_packet;
    loop {
        if tx.is_closed() {
            break;
        }

        let packet = if let Some(p) = pending.take() {
            p
        } else {
            match next_packet(&mut *format) {
                Ok(Some(p)) => p,
                Ok(None) => break,
                Err(SymphoniaError::ResetRequired) => {
                    decoder.reset();
                    continue;
                }
                Err(SymphoniaError::IoError(_)) => break,
                Err(_) => break,
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

    let mut flush_buf: Vec<u8> = Vec::with_capacity(7200);
    let n = encoder
        .flush_to_vec::<FlushNoGap>(&mut flush_buf)
        .map_err(|e| format!("{e:?}"))?;
    if n > 0 {
        let _ = tx.blocking_send(Ok(Bytes::copy_from_slice(&flush_buf)));
    }

    Ok(())
}
