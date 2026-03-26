use rust_cast::channels::heartbeat::HeartbeatResponse;
use rust_cast::channels::media::{IdleReason, Media, MediaResponse, PlayerState, StreamType};
use rust_cast::channels::receiver::{CastDeviceApp, ReceiverResponse};
use rust_cast::CastDevice as RustCastDevice;
use rust_cast::ChannelMessage;
use serde::Serialize;
use std::sync::{Arc, Mutex};
use tauri::Emitter;

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
    Seek(f32),
    SetVolume(f32),
}

pub struct CastState {
    pub status: Arc<Mutex<CastSessionStatus>>,
    cmd_tx: Arc<Mutex<Option<std::sync::mpsc::Sender<CastCommand>>>>,
}

impl CastState {
    pub fn new() -> Self {
        Self {
            status: Arc::new(Mutex::new(CastSessionStatus::Idle)),
            cmd_tx: Arc::new(Mutex::new(None)),
        }
    }

    pub fn send_command(&self, cmd: CastCommand) -> Result<(), String> {
        match &*self.cmd_tx.lock().unwrap() {
            Some(tx) => tx.send(cmd).map_err(|e| e.to_string()),
            None => Err("No active cast session".to_string()),
        }
    }

    /// Spawn a Cast session thread. Stops any existing session first.
    pub fn start_session(
        &self,
        address: String,
        port: u16,
        stream_url: String,
        is_flac: bool,
        app: tauri::AppHandle,
    ) {
        // Terminate any existing session
        if let Some(tx) = self.cmd_tx.lock().unwrap().take() {
            let _ = tx.send(CastCommand::Stop);
        }

        let (tx, rx) = std::sync::mpsc::channel::<CastCommand>();
        *self.cmd_tx.lock().unwrap() = Some(tx);

        let status_arc = Arc::clone(&self.status);

        std::thread::spawn(move || {
            let emit = |s: CastSessionStatus| {
                *status_arc.lock().unwrap() = s.clone();
                let _ = app.emit("cast://status-changed", s);
            };

            emit(CastSessionStatus::Connecting);

            // Connect to the Cast device
            let device = match RustCastDevice::connect_without_host_verification(&address, port) {
                Ok(d) => d,
                Err(e) => {
                    emit(CastSessionStatus::Error { message: e.to_string() });
                    return;
                }
            };

            // Connect the receiver (control) channel
            if let Err(e) = device.connection.connect("receiver-0") {
                emit(CastSessionStatus::Error { message: e.to_string() });
                return;
            }

            // Ping to confirm the connection is live
            if let Err(e) = device.heartbeat.ping() {
                emit(CastSessionStatus::Error { message: e.to_string() });
                return;
            }

            // Launch Default Media Receiver app
            let app_info = match device.receiver.launch_app(&CastDeviceApp::DefaultMediaReceiver) {
                Ok(info) => info,
                Err(e) => {
                    emit(CastSessionStatus::Error { message: e.to_string() });
                    return;
                }
            };

            let dest_id = app_info.transport_id.clone();
            let session_id = app_info.session_id.clone();

            // Connect the media channel to the launched app
            if let Err(e) = device.connection.connect(dest_id.as_str()) {
                emit(CastSessionStatus::Error { message: e.to_string() });
                return;
            }

            // Report the device's current volume so the UI can sync the volume bar.
            if let Ok(status) = device.receiver.get_status() {
                if let Some(level) = status.volume.level {
                    let _ = app.emit("cast://volume-changed", level);
                }
            }

            // For FLAC we emit Transcoding before loading since encoding takes time
            if is_flac {
                emit(CastSessionStatus::Transcoding);
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
                    emit(CastSessionStatus::Error { message: e.to_string() });
                    return;
                }
            };

            // Grab the initial media_session_id from the LOAD response
            let mut media_session_id = load_status
                .entries
                .first()
                .map(|e| e.media_session_id)
                .unwrap_or(1);

            // Track current seek offset for FLAC seek-by-reload
            let mut flac_base_secs: f32 = 0.0;

            // Don't emit Playing here — wait for the device to confirm actual playback
            // via the message loop. Emitting eagerly (before the device is really playing)
            // causes the frontend to resume local audio before it has the correct position,
            // leading to progress-bar desync on track changes.

            // Message loop:
            // receive() blocks until the Chromecast sends a message (~5s heartbeat cadence).
            // Commands are checked after each message arrives.
            loop {
                match device.receive() {
                    Ok(ChannelMessage::Heartbeat(HeartbeatResponse::Ping)) => {
                        let _ = device.heartbeat.pong();
                    }
                    Ok(ChannelMessage::Media(MediaResponse::Status(status))) => {
                        for entry in &status.entries {
                            media_session_id = entry.media_session_id;

                            // Emit position updates
                            let position = entry.current_time.map(|t| flac_base_secs + t);

                            if let PlayerState::Idle = entry.player_state {
                                if let Some(IdleReason::Finished) = entry.idle_reason {
                                    emit(CastSessionStatus::Stopped { finished: true });
                                    return;
                                }
                                // External stop (e.g. "hey Google, stop") — pause so Muorg UI syncs.
                                // The session loop stays alive; the user can restart from Muorg.
                                emit(CastSessionStatus::Paused { position_secs: position });
                            } else if let PlayerState::Buffering = entry.player_state {
                                emit(CastSessionStatus::Transcoding);
                            } else if let PlayerState::Playing = entry.player_state {
                                emit(CastSessionStatus::Playing { position_secs: position });
                            } else if let PlayerState::Paused = entry.player_state {
                                emit(CastSessionStatus::Paused { position_secs: position });
                            }
                        }
                    }
                    Ok(ChannelMessage::Receiver(ReceiverResponse::Status(status))) => {
                        if let Some(level) = status.volume.level {
                            let _ = app.emit("cast://volume-changed", level);
                        }
                    }
                    Ok(_) => {}
                    Err(e) => {
                        emit(CastSessionStatus::Error {
                            message: format!("Connection lost: {e}"),
                        });
                        return;
                    }
                }

                // Process any pending command after each received message
                match rx.try_recv() {
                    Ok(CastCommand::Pause) => {
                        let _ = device.media.pause(dest_id.as_str(), media_session_id);
                        emit(CastSessionStatus::Paused { position_secs: None });
                    }
                    Ok(CastCommand::Resume) => {
                        let _ = device.media.play(dest_id.as_str(), media_session_id);
                        emit(CastSessionStatus::Playing { position_secs: None });
                    }
                    Ok(CastCommand::Seek(secs)) => {
                        if is_flac {
                            // FLAC: seek-by-reload — rebuild the URL with ?start=<secs>
                            flac_base_secs = secs;
                            let seek_url = format!("{}&start={}", stream_url, secs);
                            let seek_media = Media {
                                content_id: seek_url,
                                stream_type: StreamType::Buffered,
                                content_type: "audio/mpeg".to_string(),
                                metadata: None,
                                duration: None,
                            };
                            emit(CastSessionStatus::Transcoding);
                            match device.media.load(dest_id.as_str(), session_id.as_str(), &seek_media) {
                                Ok(s) => {
                                    if let Some(e) = s.entries.first() {
                                        media_session_id = e.media_session_id;
                                    }
                                    emit(CastSessionStatus::Playing { position_secs: Some(secs) });
                                }
                                Err(e) => {
                                    emit(CastSessionStatus::Error { message: e.to_string() });
                                    return;
                                }
                            }
                        } else {
                            // MP3: native Cast seek
                            let _ = device.media.seek(dest_id.as_str(), media_session_id, Some(secs), None);
                            emit(CastSessionStatus::Playing { position_secs: Some(secs) });
                        }
                    }
                    Ok(CastCommand::SetVolume(level)) => {
                        let _ = device.receiver.set_volume(level);
                    }
                    Ok(CastCommand::Stop) | Err(std::sync::mpsc::TryRecvError::Disconnected) => {
                        let _ = device.media.stop(dest_id.as_str(), media_session_id);
                        emit(CastSessionStatus::Stopped { finished: false });
                        return;
                    }
                    Err(std::sync::mpsc::TryRecvError::Empty) => {}
                }
            }
        });
    }
}
