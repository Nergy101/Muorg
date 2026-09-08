//! Chromecast support, shared by the desktop app and the server.
//!
//! Behind the `cast` feature so a consumer that only needs the catalog does not
//! pull in `rust_cast`, `mdns-sd` and their transitive dependencies.

mod discovery;
mod session;

pub use discovery::{CastDevice, DiscoveryObserver, DiscoveryState, NoDiscoveryObserver};
pub use session::{CastCommand, CastObserver, CastSessionStatus, CastState, NoObserver};
