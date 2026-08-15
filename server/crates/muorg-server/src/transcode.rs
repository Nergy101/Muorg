use mp3lame_encoder::{Bitrate, Builder, DualPcm, FlushNoGap};
use symphonia::core::codecs::audio::{AudioDecoderOptions, CODEC_ID_NULL_AUDIO};
use symphonia::core::errors::Error as SymphoniaError;
use symphonia::core::formats::{FormatOptions, SeekMode};
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

    let track_id = track.id;
    let audio_params = track
        .codec_params
        .as_ref()
        .and_then(|p| p.audio())
        .ok_or("No audio codec parameters")?;
    let sample_rate = config.sample_rate;
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
        .set_sample_rate(sample_rate)
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
    loop {
        let packet = match format.next_packet() {
            Ok(Some(p)) => p,
            Ok(None) => break,
            Err(SymphoniaError::ResetRequired) => {
                decoder.reset();
                continue;
            }
            Err(SymphoniaError::IoError(_)) => break,
            Err(_) => break,
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

    Ok(out)
}
