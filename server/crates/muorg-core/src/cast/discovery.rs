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


/// Add a device, or refresh the entry already there.
///
/// A Chromecast re-announces itself periodically and can change address on a
/// DHCP renewal, so the same `id` arriving again is an update, not a new
/// device — matching on anything else would fill the picker with duplicates.
fn upsert_device(devices: &mut Vec<CastDevice>, device: CastDevice) {
    match devices.iter_mut().find(|d| d.id == device.id) {
        Some(existing) => {
            existing.name = device.name;
            existing.address = device.address;
            existing.port = device.port;
        }
        None => devices.push(device),
    }
}

/// Drop the device an mDNS goodbye names. Returns whether the list changed, so
/// the caller can skip notifying observers about a no-op.
fn remove_device(devices: &mut Vec<CastDevice>, fullname: &str) -> bool {
    let before = devices.len();
    devices.retain(|d| !fullname.contains(&d.id));
    devices.len() != before
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
                        upsert_device(&mut devs, CastDevice { id, name, address, port });
                        let snapshot = devs.clone();
                        drop(devs);
                        observer.on_devices(snapshot);
                    }
                    Ok(ServiceEvent::ServiceRemoved(_, fullname)) => {
                        let mut devs = devices.lock().unwrap();
                        let removed = remove_device(&mut devs, &fullname);
                        if removed {
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

    /// End the sweep and drop what it found.
    ///
    /// Clearing matters: `GET /api/cast/devices` and the desktop picker both
    /// read this list, and a device that was on the network during the sweep
    /// may not be by the next one. Keeping the list would offer devices that
    /// are no longer there.
    pub fn stop(&self) {
        if let Some(tx) = self.stop_tx.lock().unwrap().take() {
            let _ = tx.send(());
        }
        self.devices.lock().unwrap().clear();
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn device(id: &str, name: &str, address: &str) -> CastDevice {
        CastDevice {
            id: id.to_string(),
            name: name.to_string(),
            address: address.to_string(),
            port: 8009,
        }
    }

    #[test]
    fn a_new_device_is_added() {
        let mut devices = Vec::new();
        upsert_device(&mut devices, device("a", "Living Room", "10.0.0.5"));
        assert_eq!(devices.len(), 1);
        assert_eq!(devices[0].name, "Living Room");
    }

    #[test]
    fn re_announcing_updates_in_place_instead_of_duplicating() {
        // Chromecasts re-announce on a timer. Matching on anything but the id
        // would grow the picker by one entry every sweep.
        let mut devices = vec![device("a", "Living Room", "10.0.0.5")];
        upsert_device(&mut devices, device("a", "Living Room", "10.0.0.5"));
        assert_eq!(devices.len(), 1);
    }

    #[test]
    fn a_renamed_or_moved_device_keeps_its_slot() {
        // A DHCP renewal changes the address; renaming it in the Home app
        // changes the name. Neither is a new device.
        let mut devices = vec![device("a", "Living Room", "10.0.0.5")];
        upsert_device(&mut devices, device("a", "Kitchen", "10.0.0.9"));

        assert_eq!(devices.len(), 1);
        assert_eq!(devices[0].name, "Kitchen");
        assert_eq!(devices[0].address, "10.0.0.9");
    }

    #[test]
    fn different_devices_both_appear() {
        let mut devices = Vec::new();
        upsert_device(&mut devices, device("a", "Living Room", "10.0.0.5"));
        upsert_device(&mut devices, device("b", "Bedroom", "10.0.0.6"));
        assert_eq!(devices.len(), 2);
    }

    #[test]
    fn a_goodbye_removes_the_device_it_names() {
        let mut devices = vec![device("abc123", "Living Room", "10.0.0.5")];
        // mDNS reports the full service name, which embeds the id.
        assert!(remove_device(&mut devices, "abc123._googlecast._tcp.local."));
        assert!(devices.is_empty());
    }

    #[test]
    fn a_goodbye_for_an_unknown_device_changes_nothing() {
        // Returning false here is what stops the observer being told about a
        // list that did not move.
        let mut devices = vec![device("abc123", "Living Room", "10.0.0.5")];
        assert!(!remove_device(&mut devices, "other._googlecast._tcp.local."));
        assert_eq!(devices.len(), 1);
    }

    #[test]
    fn a_goodbye_leaves_the_other_devices_alone() {
        let mut devices = vec![
            device("aaa", "Living Room", "10.0.0.5"),
            device("bbb", "Bedroom", "10.0.0.6"),
        ];
        remove_device(&mut devices, "aaa._googlecast._tcp.local.");
        assert_eq!(devices.len(), 1);
        assert_eq!(devices[0].id, "bbb");
    }

    #[test]
    fn stopping_clears_the_device_list() {
        // A stale list outlives the sweep otherwise, and the picker offers
        // devices that are no longer there.
        let state = DiscoveryState::new();
        state.devices.lock().unwrap().push(device("a", "Living Room", "10.0.0.5"));
        state.stop();
        assert!(state.devices.lock().unwrap().is_empty());
    }
}
