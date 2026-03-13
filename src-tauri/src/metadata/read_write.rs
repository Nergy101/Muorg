use lofty::config::WriteOptions;
use lofty::file::{AudioFile, FileType, TaggedFileExt};
use lofty::picture::PictureType;
use lofty::probe::Probe;
use lofty::tag::{Accessor, Tag, TagType};
use serde::Serialize;
use std::io::BufReader;
use std::path::Path;

#[derive(Debug, Clone, Default, Serialize)]
pub struct TrackMetadata {
    pub title: Option<String>,
    pub artist: Option<String>,
    pub album: Option<String>,
    pub album_artist: Option<String>,
    /// Featuring / guest artist. FLAC: FEATURING; MP3: TPE2.
    pub featuring: Option<String>,
    pub year: Option<u32>,
    pub genre: Option<String>,
    pub track_number: Option<u32>,
    pub disc_number: Option<u32>,
    pub duration_secs: Option<u64>,
    pub picture_base64: Option<String>,
    /// MIME type for the picture (e.g. "image/jpeg", "image/png") so the frontend can use the correct data URL.
    pub picture_mime: Option<String>,
    /// Size in bytes of the picture data.
    pub picture_size_bytes: Option<u32>,
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

    // Explicitly set format from extension to avoid "no format could be determined" when
    // content-based detection fails (e.g. non-standard MP3 headers).
    let file_type = match ext.as_deref() {
        Some("mp3") => FileType::Mpeg,
        Some("flac") => FileType::Flac,
        _ => return Err("Unsupported format".to_string()),
    };
    let file = std::fs::File::open(path).map_err(|e| e.to_string())?;
    let reader = BufReader::new(file);
    let tagged_file = Probe::with_file_type(reader, file_type)
        .read()
        .map_err(|e| e.to_string())?;

    let tag = tagged_file
        .primary_tag()
        .or_else(|| tagged_file.first_tag());
    let mut meta = TrackMetadata::default();

    if let Some(tag) = tag {
        meta.title = tag.title().map(|s| s.to_string());
        meta.artist = tag.artist().map(|s| s.to_string());
        meta.album = tag.album().map(|s| s.to_string());
        meta.album_artist = tag
            .get_string(&lofty::tag::ItemKey::AlbumArtist)
            .map(|s| s.to_string());
        // FLAC: custom key FEATURING; MP3: TPE2 (AlbumArtist in lofty)
        if ext.as_deref() == Some("flac") {
            meta.featuring = tag
                .get_string(&lofty::tag::ItemKey::Unknown("FEATURING".to_string()))
                .map(|s| s.to_string());
        } else if ext.as_deref() == Some("mp3") {
            meta.featuring = tag
                .get_string(&lofty::tag::ItemKey::AlbumArtist)
                .map(|s| s.to_string());
        }
        meta.year = tag.year();
        meta.genre = tag.genre().map(|s| s.to_string());
        meta.track_number = tag.track();
        meta.disc_number = tag.disk();
        if let Some(pic) = tag.get_picture_type(PictureType::CoverFront) {
            meta.picture_base64 = Some(base64::Engine::encode(
                &base64::engine::general_purpose::STANDARD,
                pic.data(),
            ));
            meta.picture_mime = pic.mime_type().map(|m| m.as_str().to_string());
            meta.picture_size_bytes = Some(pic.data().len() as u32);
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
    /// Featuring / guest artist. FLAC: FEATURING; MP3: TPE2.
    pub featuring: Option<String>,
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
    match ext.as_deref() {
        Some("mp3") => write_metadata_mp3(path, update),
        Some("flac") => write_metadata_flac(path, update),
        _ => Err("Unsupported format".to_string()),
    }
}

/// MP3 write via id3 crate – avoids lofty's guess_file_type which fails on non-standard headers.
fn write_metadata_mp3(path: &Path, update: &MetadataUpdate) -> Result<(), String> {
    use id3::frame::{Picture, PictureType};
    use id3::{TagLike, Version};

    let mut tag = match id3::Tag::read_from_path(path) {
        Ok(t) => t,
        Err(id3::Error {
            kind: id3::ErrorKind::NoTag,
            ..
        }) => id3::Tag::new(),
        Err(e) => return Err(e.to_string()),
    };

    if let Some(ref t) = update.title {
        tag.set_title(t.clone());
    }
    if let Some(ref a) = update.artist {
        tag.set_artist(a.clone());
    }
    if let Some(ref a) = update.album {
        tag.set_album(a.clone());
    }
    if let Some(ref f) = update.featuring {
        tag.set_album_artist(f.clone());
    } else if let Some(ref a) = update.album_artist {
        tag.set_album_artist(a.clone());
    }
    if let Some(y) = update.year {
        tag.set_year(y as i32);
    }
    if let Some(ref g) = update.genre {
        tag.set_genre(g.clone());
    }
    if let Some(t) = update.track_number {
        tag.set_track(t);
    }
    if let Some(d) = update.disc_number {
        tag.set_disc(d);
    }
    if let Some(ref b64) = update.picture_base64 {
        tag.remove_picture_by_type(PictureType::CoverFront);
        if !b64.is_empty() {
            match base64::Engine::decode(&base64::engine::general_purpose::STANDARD, b64) {
                Ok(data) => {
                    tag.add_frame(Picture {
                        mime_type: "image/jpeg".to_string(),
                        picture_type: PictureType::CoverFront,
                        description: String::new(),
                        data,
                    });
                }
                Err(_) => return Err("Invalid base64 for picture".to_string()),
            }
        }
    }

    tag.write_to_path(path, Version::Id3v24).map_err(|e| e.to_string())?;
    Ok(())
}

/// FLAC write via lofty.
fn write_metadata_flac(path: &Path, update: &MetadataUpdate) -> Result<(), String> {
    let file = std::fs::File::open(path).map_err(|e| e.to_string())?;
    let reader = BufReader::new(file);
    let mut tagged_file = Probe::with_file_type(reader, FileType::Flac)
        .read()
        .map_err(|e| e.to_string())?;

    let tag = if let Some(t) = tagged_file.primary_tag_mut() {
        t
    } else if let Some(t) = tagged_file.first_tag_mut() {
        t
    } else {
        tagged_file.insert_tag(Tag::new(TagType::VorbisComments));
        tagged_file.primary_tag_mut().unwrap()
    };

    if let Some(ref t) = update.title {
        tag.set_title(t.clone());
    }
    if let Some(ref a) = update.artist {
        tag.set_artist(a.clone());
    }
    if let Some(ref a) = update.album {
        tag.set_album(a.clone());
    }
    if let Some(ref f) = update.featuring {
        tag.insert_text(lofty::tag::ItemKey::Unknown("FEATURING".to_string()), f.clone());
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

    tagged_file
        .save_to_path(path, WriteOptions::default())
        .map_err(|e| e.to_string())?;

    Ok(())
}
