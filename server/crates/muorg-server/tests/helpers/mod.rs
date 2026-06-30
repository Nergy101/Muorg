use muorg_core::catalog::Catalog;
use muorg_server::config::TranscodingConfig;
use muorg_server::state::AppState;
use muorg_server::build_router;
use std::net::SocketAddr;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::Duration;
use tempfile::TempDir;

/// A running test server bound to a random port with a temporary database.
pub struct TestServer {
    pub addr: SocketAddr,
    pub api_key: String,
    pub client: reqwest::Client,
    /// Kept alive so the temp dir isn't deleted until Drop.
    _temp_dir: TempDir,
    /// Sender to trigger graceful shutdown.
    shutdown_tx: tokio::sync::oneshot::Sender<()>,
}

impl TestServer {
    /// Start the server in the background.
    ///
    /// - A temporary SQLite database is created via `Catalog::new`.
    /// - The API key is set to `"test-key-123"`.
    /// - CORS is permissive (`["*"]`).
    /// - The server binds to `127.0.0.1:0` (random port).
    pub async fn start() -> Self {
        let temp_dir = TempDir::new().expect("failed to create temp dir");
        let db_path = temp_dir.path().join("test.db");
        let backup_dir = temp_dir.path().join("backups");
        std::fs::create_dir_all(&backup_dir).ok();

        let catalog = Arc::new(Catalog::new(&db_path).expect("failed to open temp catalog"));
        let api_key = "test-key-123".to_string();

        let state = Arc::new(AppState::new(
            catalog,
            backup_dir,
            5,
            api_key.clone(),
            0, // server_port placeholder; real port will be from listener
            TranscodingConfig::default(),
        ));

        let app = build_router(state, &["*".to_string()]);

        let listener = tokio::net::TcpListener::bind("127.0.0.1:0")
            .await
            .expect("failed to bind random port");
        let addr = listener.local_addr().expect("no local addr");

        let (shutdown_tx, shutdown_rx) = tokio::sync::oneshot::channel::<()>();

        // Spawn the server in the background.
        tokio::spawn(async move {
            axum::serve(listener, app.into_make_service_with_connect_info::<SocketAddr>())
                .with_graceful_shutdown(async { shutdown_rx.await.ok(); })
                .await
                .ok();
        });

        // Brief pause to let the server start listening.
        tokio::time::sleep(Duration::from_millis(100)).await;

        let client = reqwest::Client::builder()
            .timeout(Duration::from_secs(5))
            .build()
            .expect("failed to build reqwest client");

        Self {
            addr,
            api_key,
            client,
            _temp_dir: temp_dir,
            shutdown_tx,
        }
    }

    /// Build a full URL for the given path.  Example: `server.url("/api/health")`.
    pub fn url(&self, path: &str) -> String {
        format!("http://{}{}", self.addr, path)
    }

    /// Perform a GET request.
    pub async fn get(&self, path: &str) -> reqwest::Response {
        self.client.get(&self.url(path)).send().await.unwrap()
    }

    /// Perform an authenticated GET request.
    pub async fn get_auth(&self, path: &str) -> reqwest::Response {
        self.client
            .get(&self.url(path))
            .header("Authorization", format!("Bearer {}", self.api_key))
            .send()
            .await
            .unwrap()
    }

    /// Perform an OPTIONS request with the given `Origin` header.
    pub async fn options(&self, path: &str, origin: &str) -> reqwest::Response {
        self.client
            .request(reqwest::Method::OPTIONS, &self.url(path))
            .header("Origin", origin)
            .header("Access-Control-Request-Method", "GET")
            .send()
            .await
            .unwrap()
    }
}

impl Drop for TestServer {
    fn drop(&mut self) {
        let _ = self.shutdown_tx.send(());
    }
}
