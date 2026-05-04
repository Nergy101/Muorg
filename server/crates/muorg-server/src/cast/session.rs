use rust_cast::channels::heartbeat::HeartbeatResponse;
use rust_cast::channels::media::{IdleReason, Media, MediaResponse, PlayerState, ResumeState, StreamType};
use rust_cast::channels::receiver::{CastDeviceApp, ReceiverResponse};
use rust_cast::errors::Error as CastError;
use rust_cast::CastDevice as RustCastDevice;
use rust_cast::ChannelMessage;
use serde::Serialize;
use std::sync::{Arc, Mutex};
use std::time::Duration;

#[derive(Debug, Clone, Serialize)]
#[serde(tag = "status", rename_all = "camelCase")]
pub enum CastSessionStatus {
    Idle,
    Connecting,
    Transcoding,
    Playing {
        #[serde(skip_serializing_if = "Option::is_none")]
        position_secs: Option<f32>,
    },
    Paused {
        #[serde(skip_serializing_if = "Option::is_none")]
        position_secs: Option<f32>,
    },
    Stopped {
        finished: bool,
    },
    Error {
        message: String,
    },
}

pub enum CastCommand {
    Pause,
    Resume,
    Stop,
    Seek { secs: f32, was_playing: bool },
    SetVolume(f32),
}

pub struct CastState {
    pub status: Arc<Mutex<CastSessionStatus>>,
    pub volume: Arc<Mutex<f32>>,
    cmd_tx: Arc<Mutex<Option<std::sync::mpsc::Sender<CastCommand>>>>,
}

impl CastState {
    pub fn new() -> Self {
        Self {
            status: Arc::new(Mutex::new(CastSessionStatus::Idle)),
            volume: Arc::new(Mutex::new(1.0)),
            cmd_tx: Arc::new(Mutex::new(None)),
        }
    }

    pub fn send_command(&self, cmd: CastCommand) -> Result<(), String> {
        match &*self.cmd_tx.lock().unwrap() {
            Some(tx) => tx.send(cmd).map_err(|e| e.to_string()),
            None => Err("No active cast session".to_string()),
        }
    }

    pub fn start_session(&self, address: String, port: u16, stream_url: String, is_flac: bool) {
        if let Some(tx) = self.cmd_tx.lock().unwrap().take() {
            let _ = tx.send(CastCommand::Stop);
        }

        let (tx, rx) = std::sync::mpsc::channel::<CastCommand>();
        *self.cmd_tx.lock().unwrap() = Some(tx);

        let status_arc = Arc::clone(&self.status);
        let volume_arc = Arc::clone(&self.volume);

        std::thread::spawn(move || {
            let set_status = |s: CastSessionStatus| {
                *status_arc.lock().unwrap() = s;
            };

            set_status(CastSessionStatus::Connecting);

            let device = match RustCastDevice::connect_without_host_verification(&address, port) {
                Ok(d) => d,
                Err(e) => {
                    set_status(CastSessionStatus::Error { message: e.to_string() });
                    return;
                }
            };

            if let Err(e) = device.connection.connect("receiver-0") {
                set_status(CastSessionStatus::Error { message: e.to_string() });
                return;
            }

            if let Err(e) = device.heartbeat.ping() {
                set_status(CastSessionStatus::Error { message: e.to_string() });
                return;
            }

            let app_info = match device.receiver.launch_app(&CastDeviceApp::DefaultMediaReceiver) {
                Ok(info) => info,
                Err(e) => {
                    set_status(CastSessionStatus::Error { message: e.to_string() });
                    return;
                }
            };

            let dest_id = app_info.transport_id.clone();
            let session_id = app_info.session_id.clone();

            if let Err(e) = device.connection.connect(dest_id.as_str()) {
                set_status(CastSessionStatus::Error { message: e.to_string() });
                return;
            }

            if let Ok(status) = device.receiver.get_status() {
                if let Some(level) = status.volume.level {
                    *volume_arc.lock().unwrap() = level;
                }
            }

            if is_flac {
                set_status(CastSessionStatus::Transcoding);
            }

            let content_url = stream_url.clone();
            let media = Media {
                content_id: content_url,
                stream_type: StreamType::Buffered,
                content_type: "audio/mpeg".to_string(),
                metadata: None,
                duration: None,
            };

            let load_status = match device.media.load(dest_id.as_str(), session_id.as_str(), &media) {
                Ok(s) => s,
                Err(e) => {
                    set_status(CastSessionStatus::Error { message: e.to_string() });
                    return;
                }
            };

            let mut media_session_id = load_status
                .entries
                .first()
                .map(|e| e.media_session_id)
                .unwrap_or(1);

            device.drain_message_buffer();

            let mut flac_base_secs: f32 = 0.0;

            let _ = device.set_read_timeout(Some(Duration::from_millis(500)));

            fn is_timeout(e: &CastError) -> bool {
                if let CastError::Io(io_err) = e {
                    matches!(
                        io_err.kind(),
                        std::io::ErrorKind::TimedOut | std::io::ErrorKind::WouldBlock
                    )
                } else {
                    false
                }
            }

            loop {
                match device.receive() {
                    Ok(ChannelMessage::Heartbeat(HeartbeatResponse::Ping)) => {
                        let _ = device.heartbeat.pong();
                    }
                    Ok(ChannelMessage::Media(MediaResponse::Status(status))) => {
                        for entry in &status.entries {
                            media_session_id = entry.media_session_id;
                            let position = entry.current_time.map(|t| flac_base_secs + t);

                            if let PlayerState::Idle = entry.player_state {
                                if let Some(IdleReason::Finished) = entry.idle_reason {
                                    set_status(CastSessionStatus::Stopped { finished: true });
                                    return;
                                }
                                set_status(CastSessionStatus::Paused { position_secs: position });
                            } else if let PlayerState::Buffering = entry.player_state {
                                set_status(CastSessionStatus::Transcoding);
                            } else if let PlayerState::Playing = entry.player_state {
                                set_status(CastSessionStatus::Playing { position_secs: position });
                            } else if let PlayerState::Paused = entry.player_state {
                                set_status(CastSessionStatus::Paused { position_secs: position });
                            }
                        }
                    }
                    Ok(ChannelMessage::Receiver(ReceiverResponse::Status(status))) => {
                        if let Some(level) = status.volume.level {
                            *volume_arc.lock().unwrap() = level;
                        }
                    }
                    Ok(_) => {}
                    Err(ref e) if is_timeout(e) => {}
                    Err(e) => {
                        set_status(CastSessionStatus::Error {
                            message: format!("Connection lost: {e}"),
                        });
                        return;
                    }
                }

                match rx.try_recv() {
                    Ok(CastCommand::Pause) => {
                        match device.media.pause(dest_id.as_str(), media_session_id) {
                            Ok(entry) => set_status(CastSessionStatus::Paused {
                                position_secs: entry.current_time,
                            }),
                            Err(_) => set_status(CastSessionStatus::Paused { position_secs: None }),
                        }
                        device.drain_message_buffer();
                    }
                    Ok(CastCommand::Resume) => {
                        match device.media.play(dest_id.as_str(), media_session_id) {
                            Ok(entry) => set_status(CastSessionStatus::Playing {
                                position_secs: entry.current_time,
                            }),
                            Err(_) => set_status(CastSessionStatus::Playing { position_secs: None }),
                        }
                        device.drain_message_buffer();
                    }
                    Ok(CastCommand::Seek { secs, was_playing }) => {
                        if is_flac {
                            flac_base_secs = secs;
                            let seek_url = format!("{}&start={}", stream_url, secs);
                            let seek_media = Media {
                                content_id: seek_url,
                                stream_type: StreamType::Buffered,
                                content_type: "audio/mpeg".to_string(),
                                metadata: None,
                                duration: None,
                            };
                            set_status(CastSessionStatus::Transcoding);
                            match device.media.load(dest_id.as_str(), session_id.as_str(), &seek_media) {
                                Ok(s) => {
                                    if let Some(e) = s.entries.first() {
                                        media_session_id = e.media_session_id;
                                    }
                                    device.drain_message_buffer();
                                    if was_playing {
                                        set_status(CastSessionStatus::Playing { position_secs: Some(secs) });
                                    } else {
                                        let _ = device.media.pause(dest_id.as_str(), media_session_id);
                                        device.drain_message_buffer();
                                        set_status(CastSessionStatus::Paused { position_secs: Some(secs) });
                                    }
                                }
                                Err(e) => {
                                    set_status(CastSessionStatus::Error { message: e.to_string() });
                                    return;
                                }
                            }
                        } else {
                            let resume_state = if was_playing {
                                Some(ResumeState::PlaybackStart)
                            } else {
                                Some(ResumeState::PlaybackPause)
                            };
                            match device.media.seek(dest_id.as_str(), media_session_id, Some(secs), resume_state) {
                                Ok(entry) => {
                                    let position = entry.current_time.map(|t| flac_base_secs + t);
                                    device.drain_message_buffer();
                                    match entry.player_state {
                                        PlayerState::Playing => set_status(CastSessionStatus::Playing { position_secs: position }),
                                        PlayerState::Paused => set_status(CastSessionStatus::Paused { position_secs: position }),
                                        PlayerState::Buffering => set_status(CastSessionStatus::Transcoding),
                                        _ => {}
                                    }
                                }
                                Err(e) => {
                                    set_status(CastSessionStatus::Error { message: e.to_string() });
                                    return;
                                }
                            }
                        }
                    }
                    Ok(CastCommand::SetVolume(level)) => {
                        let _ = device.receiver.set_volume(level);
                    }
                    Ok(CastCommand::Stop) | Err(std::sync::mpsc::TryRecvError::Disconnected) => {
                        let _ = device.media.stop(dest_id.as_str(), media_session_id);
                        set_status(CastSessionStatus::Stopped { finished: false });
                        return;
                    }
                    Err(std::sync::mpsc::TryRecvError::Empty) => {}
                }
            }
        });
    }
}
