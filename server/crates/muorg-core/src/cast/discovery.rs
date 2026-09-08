//! Chromecast discovery over mDNS, shared by the desktop app and the server.
//!
//! Identical on both hosts apart from how the refreshed list is surfaced — see
//! [`DiscoveryObserver`].

use mdns_sd::{ServiceDaemon, ServiceEvent};
use serde::Serialize;
use std::sync::{Arc, Mutex};

#[derive(Debug, Clone, Serialize)]
#[cfg_attr(feature = "openapi", derive(utoipa::ToSchema))]
pub struct CastDevice {
    pub id: String,
    pub name: String,
    pub address: String,
    pub port: u16,
}


/// How a host learns that the device list changed.
///
/// The list itself lives in [`DiscoveryState::devices`], so a host that polls
/// (the server, via `GET /api/cast/devices`) needs only [`NoDiscoveryObserver`].
pub trait DiscoveryObserver: Send + 'static {
    fn on_devices(&self, _devices: Vec<CastDevice>) {}
}

/// For hosts that read the device list directly instead of being told.
pub struct NoDiscoveryObserver;

impl DiscoveryObserver for NoDiscoveryObserver {}

pub struct DiscoveryState {
    pub devices: Arc<Mutex<Vec<CastDevice>>>,
    stop_tx: Arc<Mutex<Option<std::sync::mpsc::Sender<()>>>>,
}

impl Default for DiscoveryState {
    fn default() -> Self {
        Self::new()
    }
}

impl DiscoveryState {
    pub fn new() -> Self {
        Self {
            devices: Arc::new(Mutex::new(Vec::new())),
            stop_tx: Arc::new(Mutex::new(None)),
        }
    }

    /// Start mDNS browsing for Chromecast devices. Idempotent.
    /// Begin an mDNS sweep for `_googlecast._tcp`.
    ///
    /// The device list is updated in place; `observer` is only for hosts that
    /// need to be told rather than polling it (see
    /// [`crate::cast::CastObserver`]).
    pub fn start(&self, observer: impl DiscoveryObserver) {
        let mut guard = self.stop_tx.lock().unwrap();
        if guard.is_some() {
            return;
        }

        let (tx, rx) = std::sync::mpsc::channel::<()>();
        *guard = Some(tx);
        drop(guard);

        let devices = Arc::clone(&self.devices);

        std::thread::spawn(move || {
            let mdns = match ServiceDaemon::new() {
                Ok(m) => m,
                Err(e) => {
                    eprintln!("[Cast] mDNS daemon error: {e}");
                    return;
                }
            };

            let recv = match mdns.browse("_googlecast._tcp.local.") {
                Ok(r) => r,
                Err(e) => {
                    eprintln!("[Cast] mDNS browse error: {e}");
                    return;
                }
            };

            loop {
                if rx.try_recv().is_ok() {
                    let _ = mdns.stop_browse("_googlecast._tcp.local.");
                    break;
                }

                match recv.recv_timeout(std::time::Duration::from_millis(200)) {
                    Ok(ServiceEvent::ServiceResolved(info)) => {
                        let props = info.get_properties();

                        let id = props
                            .get("id")
                            .map(|p| p.val_str().to_string())
                            .unwrap_or_else(|| info.get_fullname().to_string());

                        let name = props
                            .get("fn")
                            .map(|p| p.val_str().to_string())
                            .unwrap_or_else(|| id.clone());

                        let address = info
                            .get_addresses_v4()
                            .into_iter()
                            .next()
                            .map(|a| a.to_string())
                            .unwrap_or_default();

                        let port = info.get_port();

                        let mut devs = devices.lock().unwrap();
                        if let Some(d) = devs.iter_mut().find(|d| d.id == id) {
                            d.name = name;
                            d.address = address;
                            d.port = port;
                        } else {
                            devs.push(CastDevice { id, name, address, port });
                        }
                        let snapshot = devs.clone();
                        drop(devs);
                        observer.on_devices(snapshot);
                    }
                    Ok(ServiceEvent::ServiceRemoved(_, fullname)) => {
                        let mut devs = devices.lock().unwrap();
                        let before = devs.len();
                        devs.retain(|d| !fullname.contains(&d.id));
                        if devs.len() != before {
                            let snapshot = devs.clone();
                            drop(devs);
                            observer.on_devices(snapshot);
                        }
                    }
                    Ok(_) => {}
                    // Timeout from flume or channel closed
                    Err(_) => {}
                }
            }
        });
    }

    pub fn stop(&self) {
        if let Some(tx) = self.stop_tx.lock().unwrap().take() {
            let _ = tx.send(());
        }
    }
}
