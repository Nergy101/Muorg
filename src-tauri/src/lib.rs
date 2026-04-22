use catalog::Catalog;
use std::sync::Arc;
use tauri::Manager;

mod cast;
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
        .plugin(tauri_plugin_shell::init())
        .plugin(tauri_plugin_process::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_fs::init())
        .setup(|app| {
            let app_data = app
                .path()
                .app_data_dir()
                .map_err(|e| format!("Could not determine app data directory: {}", e))?;
            std::fs::create_dir_all(&app_data).ok();
            // Ensure app config dir exists for settings.yml and path display in Settings.
            if let Ok(app_config) = app.path().app_config_dir() {
                std::fs::create_dir_all(&app_config).ok();
            }
            let db_path = app_data.join("muorg.db");
            let catalog = Catalog::new(&db_path).map_err(|e| e.to_string())?;
            // Purge soft-deleted tracks and roots older than 30 days.
            {
                let conn = catalog.db.lock().map_err(|e| e.to_string())?;
                let thirty_days = 30 * 24 * 60 * 60;
                let _ = catalog::gc_deleted_tracks(&conn, thirty_days);
            }
            app.manage(Arc::new(catalog));
            app.manage(cast::DiscoveryState::new());
            app.manage(cast::AudioServerState::new());
            app.manage(cast::CastState::new());
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            commands::add_folder,
            commands::get_roots,
            commands::get_tracks,
            commands::rescan,
            commands::remove_folder,
            commands::clear_cache,
            commands::write_track_metadata,
            commands::get_track_metadata,
            commands::get_latest_track_backup,
            commands::restore_track_from_latest_backup,
            commands::set_track_rating,
            commands::path_to_folder,
            commands::write_text_file,
            commands::get_track_cover,
            commands::read_audio_file,
            commands::fetch_image_url,
            commands::get_playlists,
            commands::create_playlist,
            commands::rename_playlist,
            commands::set_playlist_icon,
            commands::delete_playlist,
            commands::get_playlist_tracks,
            commands::get_playlist_entries,
            commands::add_tracks_to_playlist,
            commands::remove_tracks_from_playlist,
            commands::remove_playlist_entry,
            commands::cast_start_discovery,
            commands::cast_stop_discovery,
            commands::cast_get_devices,
            commands::cast_play,
            commands::cast_pause,
            commands::cast_resume,
            commands::cast_stop,
            commands::cast_seek,
            commands::cast_set_volume,
            commands::rename_track_file,
            commands::record_play,
            commands::search_tracks,
            commands::get_library_stats,
            commands::create_smart_playlist,
            commands::update_smart_playlist_rules,
            commands::get_smart_playlist_track_ids,
            commands::reorder_playlists,
        ])
        .run(tauri::generate_context!())
        .unwrap_or_else(|e| {
            eprintln!("Muorg failed to start: {}", e);
            std::process::exit(1);
        });
}
