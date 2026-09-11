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
        let window_secs = inner.window_secs;
        let max_requests = inner.max_requests;

        // Garbage-collect expired entries periodically
        if inner.entries.len() > 1000 {
            inner.entries.retain(|_, w| w.reset_at > now);
        }

        let entry = inner.entries.entry(addr).or_insert(Window {
            count: 0,
            reset_at: now + Duration::from_secs(window_secs),
        });

        if now >= entry.reset_at {
            entry.count = 0;
            entry.reset_at = now + Duration::from_secs(window_secs);
        }

        if entry.count >= max_requests {
            return false;
        }

        entry.count += 1;
        true
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn addr(s: &str) -> SocketAddr {
        s.parse().unwrap()
    }

    #[test]
    fn requests_are_allowed_up_to_the_limit_and_then_refused() {
        let limiter = RateLimiter::new(3, 60);
        let ip = addr("10.0.0.1:1234");
        assert!(limiter.check(ip));
        assert!(limiter.check(ip));
        assert!(limiter.check(ip));
        assert!(!limiter.check(ip), "the fourth request is over the limit");
        assert!(!limiter.check(ip), "and it stays over");
    }

    #[test]
    fn each_client_gets_its_own_budget() {
        // One noisy client must not lock everyone else out of a shared server.
        let limiter = RateLimiter::new(1, 60);
        assert!(limiter.check(addr("10.0.0.1:1234")));
        assert!(!limiter.check(addr("10.0.0.1:1234")));
        assert!(limiter.check(addr("10.0.0.2:1234")), "a different client is unaffected");
    }

    #[test]
    fn the_window_reopens_once_it_expires() {
        // A zero-second window is always already expired, which is the same
        // code path as a window that has elapsed — without a sleep in a test.
        let limiter = RateLimiter::new(1, 0);
        let ip = addr("10.0.0.1:1234");
        assert!(limiter.check(ip));
        assert!(limiter.check(ip), "the window had expired, so the count reset");
    }

    #[test]
    fn a_zero_limit_refuses_everything() {
        let limiter = RateLimiter::new(0, 60);
        assert!(!limiter.check(addr("10.0.0.1:1234")));
    }

    #[test]
    fn the_table_does_not_grow_without_bound() {
        // Entries are garbage-collected past 1000, which is what stops a scan
        // across a /16 from being a memory leak. With an expired window every
        // entry is collectable, so the table should not run away.
        let limiter = RateLimiter::new(5, 0);
        for i in 0..2000u32 {
            limiter.check(addr(&format!("10.0.{}.{}:1234", i / 256, i % 256)));
        }
        let entries = limiter.inner.lock().unwrap().entries.len();
        assert!(entries <= 1100, "expected the table to be swept, got {entries} entries");
    }
}
