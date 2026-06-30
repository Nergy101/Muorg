use std::collections::HashMap;
use std::net::SocketAddr;
use std::sync::Mutex;
use std::time::{Duration, Instant};

/// Simple sliding-window per-IP rate limiter.
/// Tracks request count per IP in a configurable window.
pub struct RateLimiter {
    inner: Mutex<Inner>,
}

struct Inner {
    entries: HashMap<SocketAddr, Window>,
    max_requests: u32,
    window_secs: u64,
}

struct Window {
    count: u32,
    reset_at: Instant,
}

impl RateLimiter {
    pub fn new(max_requests: u32, window_secs: u64) -> Self {
        Self {
            inner: Mutex::new(Inner {
                entries: HashMap::new(),
                max_requests,
                window_secs,
            }),
        }
    }

    /// Returns true if the request from `addr` is allowed, false if rate-limited.
    pub fn check(&self, addr: SocketAddr) -> bool {
        let mut inner = self.inner.lock().unwrap();
        let now = Instant::now();

        // Garbage-collect expired entries periodically
        if inner.entries.len() > 1000 {
            inner.entries.retain(|_, w| w.reset_at > now);
        }

        let entry = inner.entries.entry(addr).or_insert(Window {
            count: 0,
            reset_at: now + Duration::from_secs(inner.window_secs),
        });

        if now >= entry.reset_at {
            entry.count = 0;
            entry.reset_at = now + Duration::from_secs(inner.window_secs);
        }

        if entry.count >= inner.max_requests {
            return false;
        }

        entry.count += 1;
        true
    }
}
