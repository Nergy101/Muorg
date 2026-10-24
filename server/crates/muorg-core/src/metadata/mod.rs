mod read_write;

pub use read_write::{
    format_from_ext, needs_transcode, read_metadata, read_metadata_from_reader, write_metadata,
    AudioFormat, MetadataUpdate, TrackMetadata,
};
