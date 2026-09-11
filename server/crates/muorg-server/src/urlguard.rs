//! SSRF guards for the one route that fetches a URL chosen by the caller.
//!
//! `POST /api/fetch-image` exists so the clients can pull album art from the
//! web without tripping CORS. That makes the server an HTTP client pointed at
//! whatever the request body says, and it hands the bytes back base64-encoded —
//! so without a guard it is a readable proxy into whatever the server can
//! reach: another container, the router's admin page, a cloud metadata
//! endpoint. The API key does not help, because the threat is a caller who has
//! one (or an XSS on an origin that does).
//!
//! Two layers, because either alone has a hole:
//!
//! - **A host allowlist**, which is the real control. The route only ever needs
//!   the handful of art sources the clients search, so anything else is a
//!   mistake or an attack.
//! - **An address check** on what the host resolves to, which catches an
//!   allowlisted name that happens to point somewhere internal — a split-horizon
//!   DNS entry, or a deliberate rebind.

use std::net::{IpAddr, Ipv4Addr, Ipv6Addr};

/// Art sources the clients actually search today.
///
/// Wikipedia's image search returns `upload.wikimedia.org` URLs; the Cover Art
/// Archive redirects into `*.us.archive.org`. Self-hosters can replace the list
/// via `[images] allowed_hosts` in the config.
pub const DEFAULT_ALLOWED_HOSTS: &[&str] = &[
    "wikipedia.org",
    "wikimedia.org",
    "coverartarchive.org",
    "archive.org",
    "musicbrainz.org",
];

/// Redirect hops a fetch may follow. Each hop is re-checked against the
/// allowlist, so this only bounds a redirect loop between allowed hosts.
pub const MAX_REDIRECTS: usize = 5;

/// Does `host` match `allowed`?
///
/// An entry matches the host itself and any subdomain of it, so `archive.org`
/// covers `ia800207.us.archive.org` — which is where the Cover Art Archive
/// redirects to, and there is no stable list of those hostnames to enumerate.
/// Matching is case-insensitive and ignores a trailing root dot, both of which
/// are ways to write the same name.
pub fn host_allowed(host: &str, allowed: &[String]) -> bool {
    let host = host.trim().trim_end_matches('.').to_ascii_lowercase();
    if host.is_empty() {
        return false;
    }
    allowed.iter().any(|entry| {
        let entry = entry
            .trim()
            .trim_start_matches('.')
            .trim_end_matches('.')
            .to_ascii_lowercase();
        !entry.is_empty() && (host == entry || host.ends_with(&format!(".{entry}")))
    })
}

/// Check a caller-supplied URL against the scheme rules and the host allowlist.
///
/// Does not resolve DNS — that is [`assert_public_host`], which is async and so
/// cannot run inside reqwest's redirect policy. This function is what both the
/// initial request and every redirect hop go through.
pub fn validate_url(raw: &str, allowed: &[String]) -> Result<reqwest::Url, String> {
    let url = reqwest::Url::parse(raw).map_err(|e| format!("Not a valid URL: {e}"))?;

    match url.scheme() {
        "http" | "https" => {}
        other => {
            return Err(format!(
                "Unsupported URL scheme `{other}`: only http and https are fetched"
            ))
        }
    }

    let host = url.host_str().ok_or_else(|| "URL has no host".to_string())?;

    // A literal address never matches a name in the allowlist, but reject it
    // with the reason that actually applies so the log says what happened.
    if let Ok(ip) = host.trim_matches(['[', ']']).parse::<IpAddr>() {
        if !is_public_ip(ip) {
            return Err(format!("Refusing to fetch from non-public address {ip}"));
        }
    }

    if !host_allowed(host, allowed) {
        return Err(format!("Host `{host}` is not in the image fetch allowlist"));
    }

    Ok(url)
}

/// Resolve the URL's host and reject it if any address is non-public.
///
/// Every address is checked, not just the first: a name that resolves to one
/// public and one loopback address is still a way in, since which one the
/// client connects to is not ours to choose.
pub async fn assert_public_host(url: &reqwest::Url) -> Result<(), String> {
    let host = url
        .host_str()
        .ok_or_else(|| "URL has no host".to_string())?
        .trim_matches(['[', ']'])
        .to_string();
    let port = url.port_or_known_default().unwrap_or(443);

    let addrs = tokio::net::lookup_host((host.as_str(), port))
        .await
        .map_err(|e| format!("Could not resolve `{host}`: {e}"))?;

    let mut resolved = 0usize;
    for addr in addrs {
        resolved += 1;
        if !is_public_ip(addr.ip()) {
            return Err(format!(
                "`{host}` resolves to non-public address {}",
                addr.ip()
            ));
        }
    }
    if resolved == 0 {
        return Err(format!("`{host}` resolved to no addresses"));
    }
    Ok(())
}

/// Is this an address on the public internet?
///
/// `IpAddr::is_global` is still unstable, so the ranges are spelled out. The
/// list errs towards rejecting: a false negative costs one unfetchable cover,
/// a false positive is the vulnerability.
pub fn is_public_ip(ip: IpAddr) -> bool {
    match ip {
        IpAddr::V4(v4) => is_public_v4(v4),
        IpAddr::V6(v6) => is_public_v6(v6),
    }
}

fn is_public_v4(ip: Ipv4Addr) -> bool {
    let [a, b, ..] = ip.octets();
    !(ip.is_private()          // 10/8, 172.16/12, 192.168/16
        || ip.is_loopback()    // 127/8
        || ip.is_link_local()  // 169.254/16 — cloud instance metadata lives here
        || ip.is_broadcast()
        || ip.is_documentation()
        || ip.is_unspecified()
        || ip.is_multicast()
        || a == 0                                  // 0.0.0.0/8 "this network"
        || (a == 100 && (64..128).contains(&b))    // 100.64/10 carrier NAT
        || (a == 192 && b == 0)                    // 192.0.0/24 protocol assignments
        || (a == 198 && (18..20).contains(&b))     // 198.18/15 benchmarking
        || a >= 240)                               // 240/4 reserved
}

fn is_public_v6(ip: Ipv6Addr) -> bool {
    // `::ffff:10.0.0.1` and `::10.0.0.1` are two more spellings of a private
    // v4 address, and a resolver will hand them over if asked.
    if let Some(mapped) = ip.to_ipv4_mapped() {
        return is_public_v4(mapped);
    }
    if let Some(compat) = ip.to_ipv4() {
        return is_public_v4(compat);
    }
    let head = ip.segments()[0];
    !(ip.is_loopback()
        || ip.is_unspecified()
        || ip.is_multicast()
        || (head & 0xfe00) == 0xfc00   // fc00::/7 unique local
        || (head & 0xffc0) == 0xfe80)  // fe80::/10 link local
}

#[cfg(test)]
mod tests {
    use super::*;

    fn allowlist() -> Vec<String> {
        DEFAULT_ALLOWED_HOSTS.iter().map(|s| s.to_string()).collect()
    }

    // -- host matching ------------------------------------------------------

    #[test]
    fn allowlist_matches_the_host_itself_and_its_subdomains() {
        let allowed = allowlist();
        assert!(host_allowed("archive.org", &allowed));
        assert!(host_allowed("upload.wikimedia.org", &allowed));
        // Where the Cover Art Archive actually redirects to.
        assert!(host_allowed("ia800207.us.archive.org", &allowed));
    }

    #[test]
    fn allowlist_ignores_case_and_a_trailing_root_dot() {
        let allowed = allowlist();
        assert!(host_allowed("Upload.WikiMedia.ORG", &allowed));
        assert!(host_allowed("upload.wikimedia.org.", &allowed));
    }

    #[test]
    fn allowlist_does_not_match_a_host_that_merely_ends_in_the_entry() {
        // The bug a naive `ends_with` would have: an attacker registers
        // `evil-archive.org` (or `notarchive.org`) and is let straight through.
        let allowed = allowlist();
        assert!(!host_allowed("evil-archive.org", &allowed));
        assert!(!host_allowed("notwikipedia.org", &allowed));
    }

    #[test]
    fn allowlist_does_not_match_the_entry_as_a_leading_label() {
        // `archive.org.evil.com` is controlled by evil.com, not by archive.org.
        assert!(!host_allowed("archive.org.evil.com", &allowlist()));
    }

    #[test]
    fn an_empty_allowlist_permits_nothing() {
        assert!(!host_allowed("archive.org", &[]));
    }

    // -- address classification ---------------------------------------------

    #[test]
    fn loopback_private_and_link_local_v4_are_not_public() {
        for addr in [
            "127.0.0.1",
            "10.0.0.5",
            "172.16.3.4",
            "192.168.1.1",
            "169.254.169.254", // the one that matters: cloud metadata
            "0.0.0.0",
            "100.64.0.1",
            "255.255.255.255",
        ] {
            let ip: IpAddr = addr.parse().unwrap();
            assert!(!is_public_ip(ip), "{addr} should not be public");
        }
    }

    #[test]
    fn loopback_and_unique_local_v6_are_not_public() {
        for addr in ["::1", "::", "fc00::1", "fd12:3456::1", "fe80::1"] {
            let ip: IpAddr = addr.parse().unwrap();
            assert!(!is_public_ip(ip), "{addr} should not be public");
        }
    }

    #[test]
    fn v4_mapped_and_v4_compatible_v6_inherit_the_v4_verdict() {
        // `::ffff:169.254.169.254` reaches the same metadata service as the
        // bare v4 address, so it has to be classified the same way.
        assert!(!is_public_ip("::ffff:169.254.169.254".parse().unwrap()));
        assert!(!is_public_ip("::ffff:127.0.0.1".parse().unwrap()));
        assert!(!is_public_ip("::127.0.0.1".parse().unwrap()));
        assert!(is_public_ip("::ffff:1.1.1.1".parse().unwrap()));
    }

    #[test]
    fn routable_addresses_are_public() {
        for addr in ["1.1.1.1", "8.8.8.8", "208.80.154.224", "2606:4700::1111"] {
            let ip: IpAddr = addr.parse().unwrap();
            assert!(is_public_ip(ip), "{addr} should be public");
        }
    }

    // -- URL validation -----------------------------------------------------

    #[test]
    fn an_allowlisted_https_url_passes() {
        let url = validate_url(
            "https://upload.wikimedia.org/wikipedia/commons/a/b/cover.jpg",
            &allowlist(),
        )
        .expect("allowlisted host should pass");
        assert_eq!(url.host_str(), Some("upload.wikimedia.org"));
    }

    #[test]
    fn a_host_outside_the_allowlist_is_rejected() {
        let err = validate_url("https://example.com/a.jpg", &allowlist()).unwrap_err();
        assert!(err.contains("allowlist"), "{err}");
    }

    #[test]
    fn the_metadata_endpoint_is_rejected_by_address_not_by_name() {
        // The case this whole module exists for.
        let err = validate_url("http://169.254.169.254/latest/meta-data/", &allowlist())
            .unwrap_err();
        assert!(err.contains("non-public address"), "{err}");
    }

    #[test]
    fn localhost_by_address_is_rejected() {
        for raw in [
            "http://127.0.0.1:8080/admin",
            "http://[::1]:8080/admin",
            "http://192.168.1.1/",
        ] {
            let err = validate_url(raw, &allowlist()).unwrap_err();
            assert!(err.contains("non-public address"), "{raw}: {err}");
        }
    }

    #[test]
    fn non_http_schemes_are_rejected() {
        for raw in [
            "file:///etc/passwd",
            "gopher://example.com/",
            "data:image/png;base64,AAAA",
        ] {
            let err = validate_url(raw, &allowlist()).unwrap_err();
            assert!(err.contains("scheme"), "{raw}: {err}");
        }
    }

    #[test]
    fn a_public_address_still_has_to_be_on_the_allowlist() {
        // Being routable is not permission — otherwise the allowlist could be
        // sidestepped by using any host's IP literally.
        let err = validate_url("https://1.1.1.1/cover.jpg", &allowlist()).unwrap_err();
        assert!(err.contains("allowlist"), "{err}");
    }

    #[test]
    fn garbage_is_rejected_rather_than_panicking() {
        assert!(validate_url("not a url", &allowlist()).is_err());
        assert!(validate_url("", &allowlist()).is_err());
    }

    // -- resolution ---------------------------------------------------------

    #[tokio::test]
    async fn a_name_resolving_to_loopback_is_rejected() {
        // `localhost` is the one name every machine resolves inward, so it
        // exercises the resolver path without needing the network.
        let url = reqwest::Url::parse("http://localhost:9/").unwrap();
        let err = assert_public_host(&url).await.unwrap_err();
        assert!(err.contains("non-public address"), "{err}");
    }
}
