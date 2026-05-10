use bytes::Bytes;
use mp3lame_encoder::{Builder, DualPcm, FlushNoGap};
use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::{DecoderOptions, CODEC_TYPE_NULL};
use symphonia::core::errors::Error as SymphoniaError;
use symphonia::core::formats::{FormatOptions, SeekMode, SeekTo};
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;
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

    let probed = symphonia::default::get_probe().format(
        &Hint::new(),
        mss,
        &FormatOptions::default(),
        &MetadataOptions::default(),
    )?;

    let mut format = probed.format;

    let track = format
        .tracks()
        .iter()
        .find(|t| t.codec_params.codec != CODEC_TYPE_NULL)
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
        symphonia::default::get_codecs().make(&track.codec_params, &DecoderOptions::default())?;

    if start_secs > 0.0 {
        let target_secs = start_secs as f64;
        let sample_rate = track.codec_params.sample_rate.unwrap_or(44100) as f64;
        let target_ts = (target_secs * sample_rate) as u64;

        tracing::info!(path, start_secs, target_ts, "seek requested");

        // Attempt a fast coarse seek. If it overshoots or fails, rewind to the
        // beginning so the packet-level fine-skip below starts from a known position.
        let seek_actual = format
            .seek(
                SeekMode::Coarse,
                SeekTo::Time { time: Time::from(target_secs), track_id: Some(track_id) },
            )
            .map(|s| s.actual_ts)
            .unwrap_or(u64::MAX);

        if seek_actual == u64::MAX {
            tracing::warn!(path, target_ts, "coarse seek failed — scanning from start");
        } else if seek_actual > target_ts {
            tracing::warn!(path, seek_actual, target_ts, "coarse seek overshot — rewinding");
            let _ = format.seek(
                SeekMode::Coarse,
                SeekTo::TimeStamp { ts: 0, track_id },
            );
        } else {
            tracing::info!(path, seek_actual, target_ts, "coarse seek ok");
        }

        // Packet-level fine-skip: discard frames until we reach target_ts.
        // FLAC frames are independently decodable so no decoding is needed here.
        let mut skipped = 0u64;
        loop {
            let packet = match format.next_packet() {
                Ok(p) => p,
                Err(SymphoniaError::ResetRequired) => { decoder.reset(); continue; }
                Err(_) => break,
            };
            if packet.track_id() != track_id { continue; }
            if packet.ts().saturating_add(packet.dur()) >= target_ts { break; }
            skipped += 1;
        }
        tracing::info!(path, skipped_packets = skipped, target_ts, "fine-skip done");
    }

    let mut builder = Builder::new().ok_or("Failed to create LAME builder")?;
    builder.set_num_channels(channels).map_err(|e| format!("{e:?}"))?;
    builder.set_sample_rate(sample_rate).map_err(|e| format!("{e:?}"))?;
    builder.set_brate(mp3lame_encoder::Bitrate::Kbps128).map_err(|e| format!("{e:?}"))?;
    builder.set_quality(mp3lame_encoder::Quality::Good).map_err(|e| format!("{e:?}"))?;
    let mut encoder = builder.build().map_err(|e| format!("{e:?}"))?;

    loop {
        if tx.is_closed() { break; }

        let packet = match format.next_packet() {
            Ok(p) => p,
            Err(SymphoniaError::ResetRequired) => { decoder.reset(); continue; }
            Err(SymphoniaError::IoError(_)) => break,
            Err(_) => break,
        };

        if packet.track_id() != track_id { continue; }

        let decoded = match decoder.decode(&packet) {
            Ok(d) => d,
            Err(SymphoniaError::DecodeError(_)) => continue,
            Err(SymphoniaError::ResetRequired) => { decoder.reset(); continue; }
            Err(_) => break,
        };

        let spec = *decoded.spec();
        let mut buf = SampleBuffer::<f32>::new(decoded.capacity() as u64, spec);
        buf.copy_interleaved_ref(decoded);
        let samples = buf.samples();

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
