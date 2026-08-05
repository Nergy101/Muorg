//! Blocking `Read + Seek` view over an object-storage object.
//!
//! Lets `lofty` and `symphonia` — both of which want a seekable source — pull
//! only the bytes they touch out of a bucket, instead of the server downloading
//! whole tracks. Reads are served from a [`READ_WINDOW`]-sized buffer and refill
//! with a ranged GET whenever the cursor leaves it.

use bytes::Bytes;
use object_store::path::Path as ObjPath;
use object_store::{ObjectStore, ObjectStoreExt};
use std::io::{self, Read, Seek, SeekFrom};
use std::sync::Arc;

/// How much is fetched per refill. Big enough that a tag read or a decode of a
/// few frames is one request, small enough that seeking does not waste egress.
pub const READ_WINDOW: u64 = 1024 * 1024;

pub struct RemoteReader {
    store: Arc<dyn ObjectStore>,
    key: ObjPath,
    handle: tokio::runtime::Handle,
    len: u64,
    pos: u64,
    win: Bytes,
    win_start: u64,
}

impl RemoteReader {
    /// `handle` must belong to the runtime that owns `store`. Every `read` on
    /// the result blocks on it, so a `RemoteReader` may only be driven from a
    /// blocking thread (`tokio::task::spawn_blocking`), never a runtime worker.
    pub fn new(
        store: Arc<dyn ObjectStore>,
        key: ObjPath,
        len: u64,
        handle: tokio::runtime::Handle,
    ) -> Self {
        Self {
            store,
            key,
            handle,
            len,
            pos: 0,
            win: Bytes::new(),
            win_start: 0,
        }
    }

    fn refill(&mut self) -> io::Result<()> {
        let end = (self.pos + READ_WINDOW).min(self.len);
        let range = self.pos..end;
        let store = self.store.clone();
        let key = self.key.clone();
        let bytes = self
            .handle
            .block_on(async move { store.get_range(&key, range).await })
            .map_err(io::Error::other)?;
        self.win_start = self.pos;
        self.win = bytes;
        Ok(())
    }
}

impl Read for RemoteReader {
    fn read(&mut self, out: &mut [u8]) -> io::Result<usize> {
        if self.pos >= self.len || out.is_empty() {
            return Ok(0);
        }
        let in_window = self.pos >= self.win_start
            && self.pos < self.win_start + self.win.len() as u64;
        if !in_window {
            self.refill()?;
            if self.win.is_empty() {
                return Ok(0);
            }
        }
        let offset = (self.pos - self.win_start) as usize;
        let n = out.len().min(self.win.len() - offset);
        out[..n].copy_from_slice(&self.win[offset..offset + n]);
        self.pos += n as u64;
        Ok(n)
    }
}

impl Seek for RemoteReader {
    fn seek(&mut self, from: SeekFrom) -> io::Result<u64> {
        // Pure arithmetic: a seek never costs a request, the next read refills
        // if it landed outside the current window.
        let target: i64 = match from {
            SeekFrom::Start(n) => n as i64,
            SeekFrom::End(d) => self.len as i64 + d,
            SeekFrom::Current(d) => self.pos as i64 + d,
        };
        if target < 0 {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "cannot seek before the start of the object",
            ));
        }
        self.pos = (target as u64).min(self.len);
        Ok(self.pos)
    }
}

impl symphonia::core::io::MediaSource for RemoteReader {
    fn is_seekable(&self) -> bool {
        true
    }

    fn byte_len(&self) -> Option<u64> {
        Some(self.len)
    }
}
