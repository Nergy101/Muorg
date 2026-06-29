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
        .find(|t| t.codec_params.codec != CODEC_ID_NULL_AUDIO)
        .ok_or("No audio track found")?
        .clone();

    let track_id = track.id;
    let sample_rate = track.codec_params.sample_rate.unwrap_or(44100);
    let channels = track
        .codec_params
        .channels
        .map(|c| c.count() as u8)
        .unwrap_or(2)
        .min(2);

    let mut decoder =
        symphonia::default::get_codecs().make_audio_decoder(&track.codec_params, &AudioDecoderOptions::default())?;

    // Seek to start_secs and return the first packet that covers the target timestamp.
    // We carry this packet into the encode loop rather than discarding it — for seeks
    // near the end of a file, the target packet may be the last one, so discarding it
    // would leave the encoder with nothing to send.
    let first_seek_packet = if start_secs > 0.0 {
        let target_secs = start_secs as f64;
        let sample_rate_f = track.codec_params.sample_rate.unwrap_or(44100) as f64;
        let target_ts = (target_secs * sample_rate_f) as u64;

        tracing::info!(path, start_secs, target_ts, "seek requested");

        let seek_actual = format
            .seek(
                SeekMode::Coarse,
                SeekTo::Time { time: Time::try_from_secs_f64(target_secs).unwrap_or(Time::ZERO), track_id: Some(track_id) },
            )
            .map(|s| s.actual_ts)
            .unwrap_or(u64::MAX);

        if seek_actual == u64::MAX {
            tracing::warn!(path, target_ts, "coarse seek failed — scanning from start");
        } else if seek_actual > target_ts {
            tracing::warn!(path, seek_actual, target_ts, "coarse seek overshot — rewinding");
            let _ = format.seek(SeekMode::Coarse, SeekTo::Time { time: Time::ZERO, track_id: Some(track_id) });
        } else {
            tracing::info!(path, seek_actual, target_ts, "coarse seek ok");
        }

        let mut skipped = 0u64;
        let mut found = None;
        loop {
            let packet = match format.next_packet() {
                Ok(p) => p,
                Err(SymphoniaError::ResetRequired) => { decoder.reset(); continue; }
                Err(_) => break,
            };
            if packet.track_id() != track_id { continue; }
            if packet.ts().saturating_add(packet.dur()) >= target_ts {
                found = Some(packet);
                break;
            }
            skipped += 1;
        }
        tracing::info!(path, skipped_packets = skipped, target_ts, "fine-skip done");
        found
    } else {
        None
    };

    let mut builder = Builder::new().ok_or("Failed to create LAME builder")?;
    builder.set_num_channels(channels).map_err(|e| format!("{e:?}"))?;
    builder.set_sample_rate(sample_rate).map_err(|e| format!("{e:?}"))?;
    builder.set_brate(mp3lame_encoder::Bitrate::Kbps128).map_err(|e| format!("{e:?}"))?;
    builder.set_quality(mp3lame_encoder::Quality::Good).map_err(|e| format!("{e:?}"))?;
    let mut encoder = builder.build().map_err(|e| format!("{e:?}"))?;

    let mut pending = first_seek_packet;
    loop {
        if tx.is_closed() { break; }

        let packet = if let Some(p) = pending.take() {
            p
        } else {
            match format.next_packet() {
                Ok(p) => p,
                Err(SymphoniaError::ResetRequired) => { decoder.reset(); continue; }
                Err(SymphoniaError::IoError(_)) => break,
                Err(_) => break,
            }
        };

        if packet.track_id() != track_id { continue; }

        let decoded = match decoder.decode(&packet) {
            Ok(d) => d,
            Err(SymphoniaError::DecodeError(_)) => continue,
            Err(SymphoniaError::ResetRequired) => { decoder.reset(); continue; }
            Err(_) => break,
        };

        let spec = *decoded.spec();
        let mut samples: Vec<f32> = Vec::new();
        decoded.copy_to_vec_interleaved(&mut samples);

        let mut mp3_buf: Vec<u8> = Vec::new();
        let n_per_channel = samples.len() / channels.max(1) as usize;
        mp3_buf.reserve((n_per_channel * 5 / 4) + 7200);

        let n = if channels == 2 {
            let left: Vec<f32> = samples.iter().step_by(2).copied().collect();
            let right: Vec<f32> = samples.iter().skip(1).step_by(2).copied().collect();
            encoder.encode_to_vec(DualPcm { left: &left, right: &right }, &mut mp3_buf)
                .map_err(|e| format!("{e:?}"))?
        } else {
            let mono: Vec<f32> = samples.to_vec();
            encoder.encode_to_vec(DualPcm { left: &mono, right: &mono }, &mut mp3_buf)
                .map_err(|e| format!("{e:?}"))?
        };

        if n > 0 {
            let _ = tx.blocking_send(Ok(Bytes::copy_from_slice(&mp3_buf)));
        }
    }

    let mut flush_buf: Vec<u8> = Vec::with_capacity(7200);
    let n = encoder.flush_to_vec::<FlushNoGap>(&mut flush_buf).map_err(|e| format!("{e:?}"))?;
    if n > 0 {
        let _ = tx.blocking_send(Ok(Bytes::copy_from_slice(&flush_buf)));
    }

    Ok(())
}
