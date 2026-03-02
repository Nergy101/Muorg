use catalog::Catalog;
use std::sync::Arc;
use tauri::Manager;

mod catalog;
mod commands;
mod metadata;

/// Install a panic hook so when run from terminal (e.g. `pnpm tauri dev`) the error is visible.
fn init_panic_log() {
    let default_hook = std::panic::take_hook();
    std::panic::set_hook(Box::new(move |info| {
        eprintln!("Muorg panic: {}", info);
        default_hook(info);
    }));
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    init_panic_log();

    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .setup(|app| {
            let app_data = app
                .path()
                .app_data_dir()
                .map_err(|e| format!("Could not determine app data directory: {}", e))?;
            std::fs::create_dir_all(&app_data).ok();
            let db_path = app_data.join("muorg.db");
            let catalog = Catalog::new(&db_path).map_err(|e| e.to_string())?;
            app.manage(Arc::new(catalog));
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::add_folder,
            commands::get_roots,
            commands::get_tracks,
            commands::rescan,
            commands::remove_folder,
            commands::write_track_metadata,
            commands::path_to_folder,
            commands::get_track_cover,
        ])
        .run(tauri::generate_context!())
        .unwrap_or_else(|e| {
            eprintln!("Muorg failed to start: {}", e);
            std::process::exit(1);
        });
}