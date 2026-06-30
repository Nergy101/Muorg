use serde::{Deserialize, Serialize};
use std::cmp::Ordering;
use std::collections::HashMap;
use std::sync::Mutex;
use std::time::Instant;

/// Query parameters for searching MusicBrainz.
#[derive(Debug, Deserialize)]
pub struct SearchQuery {
    pub artist: Option<String>,
    pub title: Option<String>,
    pub album: Option<String>,
    #[serde(default)]
    pub duration_secs: Option<u32>,
}

/// A single candidate match from MusicBrainz.
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MatchCandidate {
    /// Confidence score 0.0–1.0 computed by comparing query against results.
    pub confidence: f64,
    /// MusicBrainz recording MBID.
    pub mbid: String,
    pub title: String,
    pub artist: String,
    pub album: Option<String>,
    pub year: Option<i32>,
    pub track_number: Option<i32>,
    pub album_artist: Option<String>,
}

impl MatchCandidate {
    fn sort_key(&self) -> u64 {
        // Sort by confidence descending, then by presence of album (prefer releases with album)
        let album_bonus = if self.album.is_some() { 1_000 } else { 0 };
        let year_bonus = if self.year.is_some() { 100 } else { 0 };
        ((self.confidence * 1_000_000.0) as u64) + album_bonus + year_bonus
    }
}

/// Thread-safe MusicBrainz lookup service with rate limiting and caching.
pub struct AutoTagService {
    /// Cache: key=sha256(query_json) -> (expires_at, results)
    cache: Mutex<HashMap<String, (Instant, Vec<MatchCandidate>)>>,
    /// Last API request timestamp for rate limiting.
    last_request: Mutex<Instant>,
}

impl AutoTagService {
    pub fn new() -> Self {
        Self {
            cache: Mutex::new(HashMap::new()),
            last_request: Mutex::new(Instant::now() - std::time::Duration::from_secs(10)),
        }
    }

    /// Search MusicBrainz for matching recordings.
    /// Enforces 1 req/s rate limit and caches results with a 1-hour TTL.
    pub async fn search(&self, query: &SearchQuery) -> Result<Vec<MatchCandidate>, String> {
        // Build cache key from serialised query
        let cache_key = Self::cache_key(query);

        // Check cache first
        {
            let mut cache = self.cache.lock().map_err(|e| e.to_string())?;
            if let Some((expires, results)) = cache.get(&cache_key) {
                if *expires > Instant::now() {
                    return Ok(results.clone());
                }
            }
        }

        // Rate limit: ensure at least 1 second since last request
        {
            let mut last = self.last_request.lock().map_err(|e| e.to_string())?;
            let elapsed = last.elapsed();
            if elapsed < std::time::Duration::from_secs(1) {
                tokio::time::sleep(std::time::Duration::from_secs(1) - elapsed).await;
            }
            *last = Instant::now();
        }

        // Build MusicBrainz query
        let query_str = build_mb_query(query);
        let url = format!(
            "https://musicbrainz.org/ws/2/recording?query={}&fmt=json&limit=10",
            urlencoding::encode(&query_str)
        );

        let client = reqwest::Client::builder()
            .user_agent("Muorg/2.18.0 ( https://github.com/Nergy101/Muorg )")
            .build()
            .map_err(|e| format!("Failed to build HTTP client: {e}"))?;

        let resp = client.get(&url).send().await.map_err(|e| {
            // Check for 503 (rate limit exceeded despite our enforcement)
            format!("MusicBrainz request failed: {e}")
        })?;

        if resp.status().as_u16() == 503 {
            // Rate limited — back off for 5 seconds and return empty
            tokio::time::sleep(std::time::Duration::from_secs(5)).await;
            return Ok(Vec::new());
        }

        let text = resp.text().await.map_err(|e| format!("Failed to read response: {e}"))?;
        let candidates = parse_mb_response(&text, query)?;

        // Store in cache (1 hour TTL)
        {
            let mut cache = self.cache.lock().map_err(|e| e.to_string())?;
            cache.insert(
                cache_key,
                (Instant::now() + std::time::Duration::from_secs(3600), candidates.clone()),
            );
        }

        Ok(candidates)
    }

    fn cache_key(query: &SearchQuery) -> String {
        let json = serde_json::json!(query);
        let bytes = serde_json::to_vec(&json).unwrap_or_default();
        let hash = sha2::Sha256::digest(&bytes);
        hash.iter().map(|b| format!("{:02x}", b)).collect()
    }
}

/// Build a Lucene-style MusicBrainz search query string.
fn build_mb_query(query: &SearchQuery) -> String {
    let mut parts: Vec<String> = Vec::new();

    if let Some(ref artist) = query.artist {
        let trimmed = artist.trim().replace('"', "");
        if !trimmed.is_empty() {
            if trimmed.contains(' ') {
                parts.push(format!("artist:\"{}\"", trimmed));
            } else {
                parts.push(format!("artist:{}", trimmed));
            }
        }
    }
    if let Some(ref title) = query.title {
        let trimmed = title.trim().replace('"', "");
        if !trimmed.is_empty() {
            if trimmed.contains(' ') {
                parts.push(format!("recording:\"{}\"", trimmed));
            } else {
                parts.push(format!("recording:{}", trimmed));
            }
        }
    }
    if let Some(ref album) = query.album {
        let trimmed = album.trim().replace('"', "");
        if !trimmed.is_empty() {
            if trimmed.contains(' ') {
                parts.push(format!("release:\"{}\"", trimmed));
            } else {
                parts.push(format!("release:{}", trimmed));
            }
        }
    }

    if parts.is_empty() {
        return String::new();
    }
    parts.join(" AND ")
}

/// Parse the MusicBrainz JSON search response into MatchCandidates.
fn parse_mb_response(json_text: &str, query: &SearchQuery) -> Result<Vec<MatchCandidate>, String> {
    #[derive(Deserialize)]
    struct MbResponse {
        recordings: Option<Vec<MbRecording>>,
    }

    #[derive(Deserialize)]
    struct MbRecording {
        id: String,
        title: String,
        score: Option<u32>,
        #[serde(default)]
        length: Option<u64>, // milliseconds
        #[serde(default)]
        releases: Option<Vec<MbRelease>>,
        #[serde(default)]
        artist_credit: Option<Vec<MbArtistCredit>>,
    }

    #[derive(Deserialize)]
    struct MbRelease {
        id: String,
        title: String,
        #[serde(default)]
        date: Option<String>,
        #[serde(default)]
        track_count: Option<i32>,
        #[serde(default)]
        track_offset: Option<i32>,
        #[serde(default)]
        artist_credit: Option<Vec<MbArtistCredit>>,
    }

    #[derive(Deserialize)]
    struct MbArtistCredit {
        name: String,
        #[serde(default)]
        joinphrase: String,
    }

    let resp: MbResponse =
        serde_json::from_str(json_text).map_err(|e| format!("Failed to parse MusicBrainz response: {e}"))?;

    let recordings = resp.recordings.unwrap_or_default();
    let mut candidates: Vec<MatchCandidate> = Vec::new();

    for rec in recordings {
        // Build artist string from artist-credit
        let artist = rec
            .artist_credit
            .as_ref()
            .map(|credits| {
                credits
                    .iter()
                    .map(|c| c.name.clone())
                    .collect::<Vec<_>>()
                    .join(", ")
            })
            .unwrap_or_default();

        // Score from MusicBrainz is 0–100
        let mb_score = rec.score.unwrap_or(0) as f64 / 100.0;

        // Compute confidence based on how well the query matches
        let confidence = compute_confidence(query, &rec.title, &artist, rec.length, mb_score);

        // Build candidates from releases (one per release)
        if let Some(ref releases) = rec.releases {
            for release in releases {
                let album_artist = release
                    .artist_credit
                    .as_ref()
                    .map(|credits| {
                        credits
                            .iter()
                            .map(|c| c.name.clone())
                            .collect::<Vec<_>>()
                            .join("")
                    })
                    .unwrap_or_default();

                // Parse year from date string (YYYY-MM-DD or YYYY)
                let year = release.date.as_ref().and_then(|d| {
                    d.split('-').next().and_then(|y| y.parse::<i32>().ok())
                });

                candidates.push(MatchCandidate {
                    confidence,
                    mbid: rec.id.clone(),
                    title: rec.title.clone(),
                    artist: artist.clone(),
                    album: Some(release.title.clone()),
                    year,
                    track_number: release.track_offset,
                    album_artist: if album_artist.is_empty() { None } else { Some(album_artist) },
                });
            }
        } else {
            // No release info — just the recording
            candidates.push(MatchCandidate {
                confidence,
                mbid: rec.id.clone(),
                title: rec.title.clone(),
                artist: artist.clone(),
                album: None,
                year: None,
                track_number: None,
                album_artist: None,
            });
        }
    }

    // Sort by confidence descending (with album/year bonuses)
    candidates.sort_by(|a, b| b.sort_key().cmp(&a.sort_key()));

    // Deduplicate by MBID + album — keep highest confidence
    let mut seen = std::collections::HashSet::new();
    candidates.retain(|c| {
        let key = format!("{}|{}", c.mbid, c.album.as_deref().unwrap_or(""));
        if seen.contains(&key) {
            return false;
        }
        seen.insert(key);
        true
    });

    Ok(candidates)
}

/// Compute a confidence score 0.0–1.0 for a candidate match.
fn compute_confidence(
    query: &SearchQuery,
    result_title: &str,
    result_artist: &str,
    result_length_ms: Option<u64>,
    mb_score: f64,
) -> f64 {
    let mut score = mb_score * 0.6; // MusicBrainz score is a strong signal

    // Title match bonus
    if let Some(ref q_title) = query.title {
        let q = q_title.trim().to_lowercase();
        let r = result_title.to_lowercase();
        if r == q {
            score += 0.3; // Exact title match
        } else if r.contains(&q) || q.contains(&r) {
            score += 0.15; // Partial title match
        } else {
            let dist = levenshtein_distance(&r, &q);
            if dist <= 2 {
                score += 0.1;
            }
        }
    }

    // Artist match bonus
    if let Some(ref q_artist) = query.artist {
        let q = q_artist.trim().to_lowercase();
        let r = result_artist.to_lowercase();
        if r == q {
            score += 0.2; // Exact artist match
        } else if r.contains(&q) || q.contains(&r) {
            score += 0.1;
        } else {
            let dist = levenshtein_distance(&r, &q);
            if dist <= 3 {
                score += 0.05;
            }
        }
    }

    // Duration match bonus (within 3 seconds)
    if let Some(q_dur) = query.duration_secs {
        if let Some(r_dur_ms) = result_length_ms {
            let r_dur = r_dur_ms / 1000;
            let diff = if q_dur > r_dur { q_dur - r_dur } else { r_dur - q_dur };
            if diff <= 3 {
                score += 0.1;
            }
        }
    }

    // Clamp to [0.0, 1.0]
    score.clamp(0.0, 1.0)
}

/// Simple Levenshtein distance for fuzzy title/artist matching.
fn levenshtein_distance(a: &str, b: &str) -> usize {
    let a_chars: Vec<char> = a.chars().collect();
    let b_chars: Vec<char> = b.chars().collect();
    let a_len = a_chars.len();
    let b_len = b_chars.len();

    // Early exit for large differences
    if a_len.abs_diff(b_len) > 5 {
        return 10; // penalize heavily
    }

    let mut prev_row: Vec<usize> = (0..=b_len).collect();
    for (i, ca) in a_chars.iter().enumerate() {
        let mut curr_row = vec![i + 1];
        for (j, cb) in b_chars.iter().enumerate() {
            let cost = if ca == cb { 0 } else { 1 };
            curr_row.push(
                std::cmp::min(
                    std::cmp::min(curr_row[j] + 1, prev_row[j + 1] + 1),
                    prev_row[j] + cost,
                )
            );
        }
        prev_row = curr_row;
    }
    prev_row[b_len]
}
