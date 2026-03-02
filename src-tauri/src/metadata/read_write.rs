use lofty::config::WriteOptions;
use lofty::file::{AudioFile, TaggedFileExt};
use lofty::picture::PictureType;
use lofty::probe::Probe;
use lofty::tag::{Accessor, Tag, TagExt, TagType};
use serde::Serialize;
use std::path::Path;

#[derive(Debug, Clone, Default, Serialize)]
pub struct TrackMetadata {
    pub title: Option<String>,
    pub artist: Option<String>,
    pub album: Option<String>,
    pub album_artist: Option<String>,
    pub year: Option<u32>,
    pub genre: Option<String>,
    pub track_number: Option<u32>,
    pub disc_number: Option<u32>,
    pub duration_secs: Option<u64>,
    pub picture_base64: Option<String>,
}

/// Detect format from path (extension) and read metadata. Returns None if unsupported or error.
pub fn read_metadata(path: &Path) -> Result<TrackMetadata, String> {
    let ext = path
        .extension()
        .and_then(|e| e.to_str())
        .map(|s| s.to_lowercase());
    if ext.as_deref() != Some("mp3") && ext.as_deref() != Some("flac") {
        return Err("Unsupported format".to_string());
    }

    let tagged_file = Probe::open(path)
        .map_err(|e| e.to_string())?
        .guess_file_type()
        .map_err(|e| e.to_string())?
        .read()
        .map_err(|e| e.to_string())?;

    let tag = tagged_file.primary_tag().or_else(|| tagged_file.first_tag());
    let mut meta = TrackMetadata::default();

    if let Some(tag) = tag {
        meta.title = tag.title().map(|s| s.to_string());
        meta.artist = tag.artist().map(|s| s.to_string());
        meta.album = tag.album().map(|s| s.to_string());
        meta.album_artist = tag
            .get_string(&lofty::tag::ItemKey::AlbumArtist)
            .map(|s| s.to_string());
        meta.year = tag.year();
        meta.genre = tag.genre().map(|s| s.to_string());
        meta.track_number = tag.track();
        meta.disc_number = tag.disk();
        if let Some(pic) = tag.get_picture_type(PictureType::CoverFront) {
            meta.picture_base64 = Some(base64::Engine::encode(
                &base64::engine::general_purpose::STANDARD,
                pic.data(),
            ));
        }
    }

    meta.duration_secs = Some(tagged_file.properties().duration().as_secs());

    Ok(meta)
}

#[derive(serde::Deserialize)]
pub struct MetadataUpdate {
    pub title: Option<String>,
    pub artist: Option<String>,
    pub album: Option<String>,
    pub album_artist: Option<String>,
    pub year: Option<u32>,
    pub genre: Option<String>,
    pub track_number: Option<u32>,
    pub disc_number: Option<u32>,
    /// Base64-encoded image data for album cover; empty or null to clear
    pub picture_base64: Option<String>,
}

/// Write metadata to file. Creates tag if missing. Supports MP3 and FLAC.
pub fn write_metadata(path: &Path, update: &MetadataUpdate) -> Result<(), String> {
    let ext = path
        .extension()
        .and_then(|e| e.to_str())
        .map(|s| s.to_lowercase());
    let tag_type = match ext.as_deref() {
        Some("mp3") => TagType::Id3v2,
        Some("flac") => TagType::VorbisComments,
        _ => return Err("Unsupported format".to_string()),
    };

    let tagged_file = Probe::open(path)
        .map_err(|e| e.to_string())?
        .guess_file_type()
        .map_err(|e| e.to_string())?
        .read()
        .map_err(|e| e.to_string())?;

    let mut tag: Tag = tagged_file
        .primary_tag()
        .or_else(|| tagged_file.first_tag())
        .cloned()
        .unwrap_or_else(|| Tag::new(tag_type));

    if let Some(ref t) = update.title {
        tag.set_title(t.clone());
    }
    if let Some(ref a) = update.artist {
        tag.set_artist(a.clone());
    }
    if let Some(ref a) = update.album {
        tag.set_album(a.clone());
    }
    if let Some(ref a) = update.album_artist {
        tag.insert_text(lofty::tag::ItemKey::AlbumArtist, a.clone());
    }
    if let Some(y) = update.year {
        tag.set_year(y);
    }
    if let Some(ref g) = update.genre {
        tag.set_genre(g.clone());
    }
    if let Some(t) = update.track_number {
        tag.set_track(t);
    }
    if let Some(d) = update.disc_number {
        tag.set_disk(d);
    }
    if let Some(ref b64) = update.picture_base64 {
        tag.remove_picture_type(PictureType::CoverFront);
        if !b64.is_empty() {
            match base64::Engine::decode(&base64::engine::general_purpose::STANDARD, b64) {
                Ok(data) => {
                    let picture = lofty::picture::Picture::new_unchecked(
                        PictureType::CoverFront,
                        Some(lofty::picture::MimeType::Jpeg),
                        None,
                        data,
                    );
                    tag.push_picture(picture);
                }
                Err(_) => return Err("Invalid base64 for picture".to_string()),
            }
        }
    }

    tag.save_to_path(path, WriteOptions::default()).map_err(|e| e.to_string())?;

    Ok(())
}
