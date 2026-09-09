//! Chromecast support.
//!
//! The protocol handling lives in `muorg-core` behind its `cast` feature,
//! shared verbatim with the desktop app — the two used to carry parallel
//! copies that drifted. The server polls its own state rather than being
//! pushed to, so it passes the no-op observers.

pub use muorg_core::cast::{
    CastCommand, CastDevice, CastSessionStatus, CastState, DiscoveryState, NoDiscoveryObserver,
    NoObserver,
};
