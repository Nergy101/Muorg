//! Keeps the bundled `muorg-server` sidecar in step with the app.
//!
//! The sidecar is a separate process that can outlive the app that started it
//! (an auto-update relaunch, a crash). The next launch then finds port 7700
//! taken, the fresh sidecar fails to bind, and the app silently talks to the
//! old server — which, for instance, had no LAN stream listener, so casting
//! looked broken. On startup we therefore ask whoever holds the port for its
//! version (`X-Muorg-Version` on `/api/health`) and, if it is not this app's
//! version, stop it and start the bundled one.
//!
//! Only a process named `muorg-server*` is ever stopped. Anything else on the
//! port is left alone.

use std::net::{Ipv4Addr, SocketAddr, TcpStream};
use std::process::Command;
use std::time::{Duration, Instant};
use tauri::Manager;
use tauri_plugin_shell::ShellExt;

pub const PORT: u16 = 7700;

const VERSION_HEADER: &str = "x-muorg-version";
const PROBE_TIMEOUT: Duration = Duration::from_millis(1500);
/// How long a polite shutdown gets before the process is killed. Short because
/// a server with open keep-alive connections can wait on them indefinitely.
const GRACEFUL_WAIT: Duration = Duration::from_secs(2);
const KILL_WAIT: Duration = Duration::from_secs(3);

#[derive(Debug, PartialEq, Eq)]
enum Probe {
    /// Nothing is listening.
    Free,
    /// A server of this app's version is already running.
    Current,
    /// Something answered, but not with this version. `reported` is `None`
    /// for servers that predate the version header (or did not answer at all).
    Outdated { reported: Option<String> },
}

/// Decide what an answering server's reported version means.
fn classify(reported: Option<&str>, expected: &str) -> Probe {
    match reported {
        Some(v) if v == expected => Probe::Current,
        other => Probe::Outdated {
            reported: other.map(str::to_owned),
        },
    }
}

async fn probe(port: u16, expected: &str) -> Probe {
    let client = match reqwest::Client::builder().timeout(PROBE_TIMEOUT).build() {
        Ok(c) => c,
        Err(e) => {
            eprintln!("[sidecar] could not build probe client: {e}");
            return Probe::Free;
        }
    };
    match client
        .get(format!("http://127.0.0.1:{port}/api/health"))
        .send()
        .await
    {
        Ok(resp) => {
            let reported = resp
                .headers()
                .get(VERSION_HEADER)
                .and_then(|v| v.to_str().ok());
            classify(reported, expected)
        }
        // Refused: nobody is listening. Any other failure (timeout, reset)
        // means a socket accepted us, so something holds the port.
        Err(e) if e.is_connect() => Probe::Free,
        Err(_) => Probe::Outdated { reported: None },
    }
}

// ── Finding and stopping the process that holds the port ────────────────────

fn run(program: &str, args: &[&str]) -> Option<String> {
    let mut cmd = Command::new(program);
    cmd.args(args);
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x0800_0000); // CREATE_NO_WINDOW
    }
    let out = cmd.output().ok()?;
    Some(String::from_utf8_lossy(&out.stdout).into_owned())
}

/// `lsof -t` output: one pid per line.
fn parse_lsof_pids(out: &str) -> Vec<u32> {
    let mut pids: Vec<u32> = out.lines().filter_map(|l| l.trim().parse().ok()).collect();
    pids.dedup();
    pids
}

/// `ss -ltnp` output: `users:(("muorg-server",pid=123,fd=15))`.
#[cfg_attr(windows, allow(dead_code))]
fn parse_ss_pids(out: &str) -> Vec<u32> {
    let mut pids = Vec::new();
    for part in out.split("pid=").skip(1) {
        let digits: String = part.chars().take_while(char::is_ascii_digit).collect();
        if let Ok(pid) = digits.parse() {
            if !pids.contains(&pid) {
                pids.push(pid);
            }
        }
    }
    pids
}

/// `netstat -ano -p tcp` output: `TCP 127.0.0.1:7700 0.0.0.0:0 LISTENING 1234`.
#[cfg_attr(not(windows), allow(dead_code))]
fn parse_netstat_pids(out: &str, port: u16) -> Vec<u32> {
    let suffix = format!(":{port}");
    let mut pids = Vec::new();
    for line in out.lines() {
        let f: Vec<&str> = line.split_whitespace().collect();
        if f.len() >= 5
            && f[0].eq_ignore_ascii_case("TCP")
            && f[1].ends_with(&suffix)
            && f[3].eq_ignore_ascii_case("LISTENING")
        {
            if let Ok(pid) = f[4].parse() {
                if !pids.contains(&pid) {
                    pids.push(pid);
                }
            }
        }
    }
    pids
}

#[cfg(unix)]
fn listener_pids(port: u16) -> Vec<u32> {
    let spec = format!("-iTCP:{port}");
    if let Some(out) = run("lsof", &["-nP", &spec, "-sTCP:LISTEN", "-t"]) {
        return parse_lsof_pids(&out);
    }
    // No lsof (some minimal Linux installs): fall back to ss.
    let filter = format!("sport = :{port}");
    run("ss", &["-H", "-ltnp", &filter])
        .map(|o| parse_ss_pids(&o))
        .unwrap_or_default()
}

#[cfg(windows)]
fn listener_pids(port: u16) -> Vec<u32> {
    run("netstat", &["-ano", "-p", "tcp"])
        .map(|o| parse_netstat_pids(&o, port))
        .unwrap_or_default()
}

/// Executable name of `pid`, without directory.
#[cfg(unix)]
fn process_name(pid: u32) -> Option<String> {
    let out = run("ps", &["-p", &pid.to_string(), "-o", "comm="])?;
    let comm = out.trim();
    (!comm.is_empty()).then(|| comm.rsplit('/').next().unwrap_or(comm).to_owned())
}

#[cfg(windows)]
fn process_name(pid: u32) -> Option<String> {
    let filter = format!("PID eq {pid}");
    let out = run("tasklist", &["/FI", &filter, "/FO", "CSV", "/NH"])?;
    // "muorg-server.exe","1234","Console","1","10,000 K"
    let first = out.lines().next()?.split(',').next()?;
    let name = first.trim().trim_matches('"');
    (!name.is_empty()).then(|| name.to_owned())
}

/// Is this process one of ours? Matches `muorg-server`, `muorg-server.exe` and
/// the target-triple-suffixed names a dev build of the sidecar carries.
fn is_sidecar(name: &str) -> bool {
    name.to_ascii_lowercase().starts_with("muorg-server")
}

#[cfg(unix)]
fn terminate(pid: u32, force: bool) {
    let sig = if force { "-KILL" } else { "-TERM" };
    run("kill", &[sig, &pid.to_string()]);
}

#[cfg(windows)]
fn terminate(pid: u32, _force: bool) {
    // taskkill without /F only asks windowless processes to close; the
    // sidecar has no window, so it is always forced.
    run("taskkill", &["/PID", &pid.to_string(), "/F"]);
}

fn port_open(port: u16) -> bool {
    TcpStream::connect_timeout(
        &SocketAddr::from((Ipv4Addr::LOCALHOST, port)),
        Duration::from_millis(200),
    )
    .is_ok()
}

fn wait_until_closed(port: u16, limit: Duration) -> bool {
    let deadline = Instant::now() + limit;
    while Instant::now() < deadline {
        if !port_open(port) {
            return true;
        }
        std::thread::sleep(Duration::from_millis(100));
    }
    !port_open(port)
}

/// Stop the muorg-server holding `port` and wait for the port to free up.
fn stop_outdated(port: u16) -> Result<(), String> {
    let pids: Vec<u32> = listener_pids(port)
        .into_iter()
        .filter(|pid| process_name(*pid).is_some_and(|n| is_sidecar(&n)))
        .collect();
    if pids.is_empty() {
        return Err(format!(
            "port {port} is held by a process that is not a muorg-server; leaving it alone"
        ));
    }
    for pid in &pids {
        terminate(*pid, false);
    }
    if wait_until_closed(port, GRACEFUL_WAIT) {
        return Ok(());
    }
    for pid in &pids {
        terminate(*pid, true);
    }
    if wait_until_closed(port, KILL_WAIT) {
        Ok(())
    } else {
        Err(format!("port {port} is still in use after stopping {pids:?}"))
    }
}

// ── Entry point ─────────────────────────────────────────────────────────────

/// Make sure a sidecar of this app's version is serving on [`PORT`]: reuse a
/// current one, replace an outdated one, start one if none runs.
pub async fn ensure_running(app: tauri::AppHandle, config_path: String) {
    let expected = env!("CARGO_PKG_VERSION");
    match probe(PORT, expected).await {
        Probe::Current => return,
        Probe::Free => {}
        Probe::Outdated { reported } => {
            eprintln!(
                "[sidecar] server on port {PORT} reports {}, app is {expected}; replacing it",
                reported.as_deref().unwrap_or("no version (older release)")
            );
            let stopped = tauri::async_runtime::spawn_blocking(|| stop_outdated(PORT)).await;
            match stopped {
                Ok(Ok(())) => {}
                Ok(Err(e)) => {
                    eprintln!("[sidecar] could not replace outdated server: {e}");
                    return;
                }
                Err(e) => {
                    eprintln!("[sidecar] replacing outdated server failed: {e}");
                    return;
                }
            }
        }
    }
    spawn(&app, &config_path);
}

fn spawn(app: &tauri::AppHandle, config_path: &str) {
    let cmd = match app.shell().sidecar("muorg-server") {
        Ok(cmd) => cmd,
        Err(e) => {
            eprintln!("[sidecar] muorg-server sidecar not found: {e}");
            return;
        }
    };
    match cmd.args(["--config", config_path]).spawn() {
        Ok((mut rx, child)) => {
            tauri::async_runtime::spawn(async move { while rx.recv().await.is_some() {} });
            *app.state::<crate::ServerChild>().0.lock().unwrap() = Some(child);
        }
        Err(e) => eprintln!("[sidecar] muorg-server spawn failed: {e}"),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_matching_version_is_current() {
        assert_eq!(classify(Some("3.2.3"), "3.2.3"), Probe::Current);
    }

    #[test]
    fn a_different_version_is_outdated_and_remembered() {
        assert_eq!(
            classify(Some("3.2.0"), "3.2.3"),
            Probe::Outdated { reported: Some("3.2.0".into()) }
        );
    }

    /// Releases before the header existed are exactly the ones that need
    /// replacing, so "no header" must not be read as "fine".
    #[test]
    fn a_server_without_the_header_is_outdated() {
        assert_eq!(classify(None, "3.2.3"), Probe::Outdated { reported: None });
    }

    #[test]
    fn only_muorg_server_processes_are_ours() {
        assert!(is_sidecar("muorg-server"));
        assert!(is_sidecar("muorg-server.exe"));
        assert!(is_sidecar("muorg-server-aarch64-apple-darwin"));
        assert!(!is_sidecar("muorg"));
        assert!(!is_sidecar("node"));
        assert!(!is_sidecar("my-muorg-server"));
    }

    #[test]
    fn lsof_output_yields_each_pid_once() {
        assert_eq!(parse_lsof_pids("97950\n97950\n123\n"), vec![97950, 123]);
        assert_eq!(parse_lsof_pids(""), Vec::<u32>::new());
    }

    #[test]
    fn ss_output_yields_the_listening_pid() {
        let out = r#"LISTEN 0 1024 127.0.0.1:7700 0.0.0.0:* users:(("muorg-server",pid=4242,fd=15))"#;
        assert_eq!(parse_ss_pids(out), vec![4242]);
    }

    #[test]
    fn netstat_output_only_counts_listeners_on_the_port() {
        let out = "\
  Proto  Local Address          Foreign Address        State           PID
  TCP    127.0.0.1:7700         0.0.0.0:0              LISTENING       1234
  TCP    127.0.0.1:7700         127.0.0.1:50000        ESTABLISHED     1234
  TCP    0.0.0.0:77001          0.0.0.0:0              LISTENING       999
  TCP    0.0.0.0:445            0.0.0.0:0              LISTENING       4
";
        assert_eq!(parse_netstat_pids(out, 7700), vec![1234]);
    }
}
