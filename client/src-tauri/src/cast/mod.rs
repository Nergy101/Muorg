//! Chromecast support for the desktop app.
//!
//! Discovery and the session protocol live in `muorg-core` behind its `cast`
//! feature, shared verbatim with the server — the two used to carry parallel
//! copies that drifted apart. What stays here is what is genuinely local: the
//! HTTP server that hands the device a file off this machine, the FLAC
//! transcoder that feeds it, and the [`TauriObserver`] that forwards state
//! changes to the webview.

pub mod server;
pub mod transcode;

pub use muorg_core::cast::{
    CastCommand, CastDevice, CastSessionStatus, CastState, DiscoveryState,
};
pub use server::AudioServerState;

use muorg_core::cast::{CastObserver, DiscoveryObserver};
use tauri::Emitter;

/// Forwards session state to the webview.
///
/// The web client polls `/api/cast/status`; the desktop app is a native shell
/// around the same server, so it can be told instead — the frontend listens for
/// these events rather than polling.
pub struct TauriObserver {
    app: tauri::AppHandle,
}

impl TauriObserver {
    pub fn new(app: tauri::AppHandle) -> Self {
        Self { app }
    }
}

impl CastObserver for TauriObserver {
    fn on_status(&self, status: CastSessionStatus) {
        let _ = self.app.emit("cast://status-changed", status);
    }

    fn on_volume(&self, level: f32) {
        let _ = self.app.emit("cast://volume-changed", level);
    }
}

impl DiscoveryObserver for TauriObserver {
    fn on_devices(&self, devices: Vec<CastDevice>) {
        let _ = self.app.emit("cast://device-list-changed", devices);
    }
}
