use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::sync::Mutex;
use std::time::Instant;
use sha2::Digest;

/// Query parameters for searching MusicBrainz.
#[derive(Debug, Serialize, Deserialize, utoipa::ToSchema)]
pub struct SearchQuery {
    pub artist: Option<String>,
    pub title: Option<String>,
    pub album: Option<String>,
    #[serde(default)]
    pub duration_secs: Option<u32>,
}

/// A single candidate match from MusicBrainz.
#[derive(Debug, Clone, Serialize, Deserialize, utoipa::ToSchema)]
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

impl Default for AutoTagService {
    fn default() -> Self {
        Self::new()
    }
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
            let cache = self.cache.lock().map_err(|e| e.to_string())?;
            if let Some((expires, results)) = cache.get(&cache_key) {
                if *expires > Instant::now() {
                    return Ok(results.clone());
                }
            }
        }

        // Rate limit: ensure at least 1 second since last request
        {
            let wait = {
                let last = self.last_request.lock().map_err(|e| e.to_string())?;
                std::time::Duration::from_secs(1).checked_sub(last.elapsed())
            };
            if let Some(wait) = wait {
                tokio::time::sleep(wait).await;
            }
            *self.last_request.lock().map_err(|e| e.to_string())? = Instant::now();
        }

        // Build MusicBrainz query
        let query_str = build_mb_query(query);
        // A query with nothing in it produces `?query=`, which MusicBrainz
        // answers with a 400 — there is no point spending the round trip or
        // the rate-limit slot on it.
        if query_str.is_empty() {
            return Ok(Vec::new());
        }
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

    // MusicBrainz sends `artist-credit`, `track-count` and `track-offset`.
    // Without this rename those three fields silently stayed at their
    // defaults, so every candidate came back with an empty artist, no album
    // artist and no track number — the `Option` + `#[serde(default)]` on each
    // one is what kept it quiet.
    #[derive(Deserialize)]
    #[serde(rename_all = "kebab-case")]
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
    #[serde(rename_all = "kebab-case")]
    struct MbRelease {
        title: String,
        #[serde(default)]
        date: Option<String>,
        #[serde(default)]
        track_offset: Option<i32>,
        #[serde(default)]
        artist_credit: Option<Vec<MbArtistCredit>>,
    }

    // MusicBrainz also sends `joinphrase` (" & ", " feat. ") per credit. The
    // candidates join with ", " instead, so it is not read here.
    #[derive(Deserialize)]
    struct MbArtistCredit {
        name: String,
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
                    // `track-offset` is the 0-based position in the release's
                    // tracklist; a track number written into a tag is 1-based.
                    track_number: release.track_offset.map(|o| o + 1),
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
    candidates.sort_by_key(|b| std::cmp::Reverse(b.sort_key()));

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
            let r_dur = (r_dur_ms / 1000) as u32;
            let diff = q_dur.abs_diff(r_dur);
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

#[cfg(test)]
mod tests {
    use super::*;

    fn query(artist: Option<&str>, title: Option<&str>, album: Option<&str>) -> SearchQuery {
        SearchQuery {
            artist: artist.map(str::to_string),
            title: title.map(str::to_string),
            album: album.map(str::to_string),
            duration_secs: None,
        }
    }

    // -- query building -----------------------------------------------------

    #[test]
    fn a_multi_word_value_is_quoted_and_a_single_word_is_not() {
        // Unquoted, `artist:Massive Attack` means "artist Massive" AND the bare
        // term "Attack", which matches far too much.
        let q = build_mb_query(&query(Some("Massive Attack"), Some("Teardrop"), None));
        assert_eq!(q, "artist:\"Massive Attack\" AND recording:Teardrop");
    }

    #[test]
    fn every_supplied_field_is_anded_together() {
        let q = build_mb_query(&query(Some("Radiohead"), Some("Creep"), Some("Pablo Honey")));
        assert_eq!(q, "artist:Radiohead AND recording:Creep AND release:\"Pablo Honey\"");
    }

    #[test]
    fn quotes_in_a_value_are_stripped_rather_than_breaking_the_query() {
        let q = build_mb_query(&query(Some("AC\"DC"), None, None));
        assert_eq!(q, "artist:ACDC", "a stray quote would unbalance the Lucene term");
    }

    #[test]
    fn blank_and_whitespace_only_fields_are_dropped() {
        let q = build_mb_query(&query(Some("   "), Some("Creep"), Some("")));
        assert_eq!(q, "recording:Creep");
    }

    #[test]
    fn a_query_with_nothing_usable_is_empty() {
        assert_eq!(build_mb_query(&query(None, None, None)), "");
        assert_eq!(build_mb_query(&query(Some(" "), None, Some(""))), "");
    }

    #[tokio::test]
    async fn an_empty_query_returns_no_candidates_without_calling_out() {
        // The guard matters because the alternative is a guaranteed 400 that
        // also burns the 1-req/s budget for a real lookup behind it.
        let service = AutoTagService::new();
        let got = service.search(&query(None, None, None)).await.unwrap();
        assert!(got.is_empty());
    }

    // -- response parsing ---------------------------------------------------

    const TWO_RELEASES: &str = r#"{
      "recordings": [
        {
          "id": "rec-1",
          "title": "Teardrop",
          "score": 100,
          "length": 330000,
          "artist-credit": [{ "name": "Massive Attack", "joinphrase": "" }],
          "releases": [
            { "id": "rel-1", "title": "Mezzanine", "date": "1998-04-20", "track-offset": 4,
              "artist-credit": [{ "name": "Massive Attack", "joinphrase": "" }] },
            { "id": "rel-2", "title": "Singles 90/98", "date": "2001",
              "artist-credit": [{ "name": "Massive Attack", "joinphrase": "" }] }
          ]
        }
      ]
    }"#;

    #[test]
    fn each_release_of_a_recording_becomes_its_own_candidate() {
        // One recording on two albums is two things a user might want to tag
        // the file as, so it has to be two rows in the picker.
        let got = parse_mb_response(TWO_RELEASES, &query(Some("Massive Attack"), Some("Teardrop"), None)).unwrap();
        assert_eq!(got.len(), 2);
        let albums: Vec<&str> = got.iter().filter_map(|c| c.album.as_deref()).collect();
        assert!(albums.contains(&"Mezzanine") && albums.contains(&"Singles 90/98"));
        assert!(got.iter().all(|c| c.mbid == "rec-1"));
    }

    #[test]
    fn a_year_is_taken_from_the_leading_component_of_any_date_shape() {
        let got = parse_mb_response(TWO_RELEASES, &query(None, None, None)).unwrap();
        let mezzanine = got.iter().find(|c| c.album.as_deref() == Some("Mezzanine")).unwrap();
        let singles = got.iter().find(|c| c.album.as_deref() == Some("Singles 90/98")).unwrap();
        assert_eq!(mezzanine.year, Some(1998), "from YYYY-MM-DD");
        assert_eq!(singles.year, Some(2001), "from a bare YYYY");
    }

    #[test]
    fn the_album_artist_and_track_number_come_off_the_release() {
        // These two were the casualties of the missing kebab-case rename: both
        // were always absent, so accepting a suggestion wrote nothing for them.
        let got = parse_mb_response(TWO_RELEASES, &query(None, None, None)).unwrap();
        let mezzanine = got.iter().find(|c| c.album.as_deref() == Some("Mezzanine")).unwrap();
        assert_eq!(mezzanine.album_artist.as_deref(), Some("Massive Attack"));
        assert_eq!(
            mezzanine.track_number,
            Some(5),
            "MusicBrainz counts tracks from 0 and tags count from 1"
        );
    }

    #[test]
    fn a_release_with_no_track_offset_has_no_track_number() {
        let got = parse_mb_response(TWO_RELEASES, &query(None, None, None)).unwrap();
        let singles = got.iter().find(|c| c.album.as_deref() == Some("Singles 90/98")).unwrap();
        assert!(singles.track_number.is_none(), "absent must not become 1");
    }

    #[test]
    fn a_recording_with_no_release_still_yields_a_candidate() {
        let json = r#"{"recordings":[{"id":"rec-2","title":"Untitled","score":80,
            "artist-credit":[{"name":"Unknown","joinphrase":""}]}]}"#;
        let got = parse_mb_response(json, &query(None, Some("Untitled"), None)).unwrap();
        assert_eq!(got.len(), 1);
        assert!(got[0].album.is_none());
        assert!(got[0].year.is_none());
    }

    #[test]
    fn a_split_artist_credit_is_joined_into_one_name() {
        let json = r#"{"recordings":[{"id":"rec-3","title":"Duet","score":90,
            "artist-credit":[{"name":"Artist A","joinphrase":" & "},{"name":"Artist B","joinphrase":""}]}]}"#;
        let got = parse_mb_response(json, &query(None, None, None)).unwrap();
        assert_eq!(got[0].artist, "Artist A, Artist B");
    }

    #[test]
    fn the_same_recording_and_album_is_not_listed_twice() {
        let json = r#"{"recordings":[
            {"id":"rec-1","title":"Song","score":100,"releases":[{"id":"r1","title":"Album"}]},
            {"id":"rec-1","title":"Song","score":90,"releases":[{"id":"r2","title":"Album"}]}
        ]}"#;
        let got = parse_mb_response(json, &query(None, None, None)).unwrap();
        assert_eq!(got.len(), 1, "same mbid + same album is one candidate");
    }

    #[test]
    fn candidates_come_back_best_first() {
        let json = r#"{"recordings":[
            {"id":"weak","title":"Something Else","score":20},
            {"id":"strong","title":"Teardrop","score":100,
             "artist-credit":[{"name":"Massive Attack","joinphrase":""}]}
        ]}"#;
        let got = parse_mb_response(json, &query(Some("Massive Attack"), Some("Teardrop"), None)).unwrap();
        assert_eq!(got[0].mbid, "strong");
        assert!(got[0].confidence > got[1].confidence);
    }

    #[test]
    fn an_empty_result_set_is_not_an_error() {
        assert!(parse_mb_response(r#"{"recordings":[]}"#, &query(None, None, None)).unwrap().is_empty());
        assert!(parse_mb_response("{}", &query(None, None, None)).unwrap().is_empty());
    }

    #[test]
    fn malformed_json_is_reported_rather_than_panicking() {
        let err = parse_mb_response("<html>rate limited</html>", &query(None, None, None)).unwrap_err();
        assert!(err.contains("Failed to parse"), "{err}");
    }

    #[test]
    fn missing_optional_fields_do_not_break_parsing() {
        // MusicBrainz omits most fields most of the time; a candidate with
        // nothing but an id and a title has to survive.
        let got = parse_mb_response(r#"{"recordings":[{"id":"x","title":"y"}]}"#, &query(None, None, None)).unwrap();
        assert_eq!(got.len(), 1);
        assert_eq!(got[0].artist, "", "no artist credit means an empty artist");
        assert_eq!(got[0].confidence, 0.0, "no score means no confidence");
    }

    // -- confidence ---------------------------------------------------------

    #[test]
    fn an_exact_title_and_artist_match_scores_higher_than_a_partial_one() {
        let q = query(Some("Massive Attack"), Some("Teardrop"), None);
        let exact = compute_confidence(&q, "Teardrop", "Massive Attack", None, 1.0);
        let partial = compute_confidence(&q, "Teardrop (Remix)", "Massive Attack", None, 1.0);
        let wrong = compute_confidence(&q, "Completely Different", "Someone Else", None, 1.0);
        assert!(exact > partial, "{exact} should beat {partial}");
        assert!(partial > wrong, "{partial} should beat {wrong}");
    }

    #[test]
    fn a_matching_duration_adds_confidence_and_a_wrong_one_does_not() {
        let mut q = query(Some("Massive Attack"), Some("Teardrop"), None);
        q.duration_secs = Some(330);
        let close = compute_confidence(&q, "Teardrop", "Massive Attack", Some(331_000), 0.5);
        let far = compute_confidence(&q, "Teardrop", "Massive Attack", Some(200_000), 0.5);
        assert!(close > far);
    }

    #[test]
    fn a_missing_duration_on_either_side_is_simply_not_scored() {
        let mut q = query(None, Some("Teardrop"), None);
        q.duration_secs = Some(330);
        let no_result_length = compute_confidence(&q, "Teardrop", "", None, 0.5);
        let no_query_length = compute_confidence(&query(None, Some("Teardrop"), None), "Teardrop", "", Some(330_000), 0.5);
        assert_eq!(no_result_length, no_query_length, "neither side should be penalised");
    }

    #[test]
    fn confidence_stays_inside_zero_and_one() {
        let mut q = query(Some("Massive Attack"), Some("Teardrop"), None);
        q.duration_secs = Some(330);
        // Every bonus at once must not push the score past 1.0, since the UI
        // renders it as a percentage.
        let best = compute_confidence(&q, "Teardrop", "Massive Attack", Some(330_000), 1.0);
        assert!((0.0..=1.0).contains(&best), "got {best}");
        let worst = compute_confidence(&q, "zzzz", "yyyy", Some(1), 0.0);
        assert!((0.0..=1.0).contains(&worst), "got {worst}");
    }

    #[test]
    fn matching_is_case_and_padding_insensitive() {
        let q = query(Some("  massive attack "), Some(" TEARDROP "), None);
        let got = compute_confidence(&q, "Teardrop", "Massive Attack", None, 0.5);
        let exact = compute_confidence(
            &query(Some("Massive Attack"), Some("Teardrop"), None),
            "Teardrop",
            "Massive Attack",
            None,
            0.5,
        );
        assert_eq!(got, exact);
    }

    #[test]
    fn levenshtein_measures_small_edits_and_gives_up_on_big_ones() {
        assert_eq!(levenshtein_distance("teardrop", "teardrop"), 0);
        assert_eq!(levenshtein_distance("teardrop", "tardrop"), 1);
        assert_eq!(levenshtein_distance("teardrop", "teardrap"), 1);
        assert_eq!(levenshtein_distance("", ""), 0);
        // A length gap over 5 short-circuits to a penalty, which is above
        // every threshold the callers use.
        assert_eq!(levenshtein_distance("a", "abcdefghij"), 10);
    }

    // -- caching ------------------------------------------------------------

    #[test]
    fn the_cache_key_depends_on_every_part_of_the_query() {
        let base = query(Some("A"), Some("B"), Some("C"));
        let key = AutoTagService::cache_key(&base);
        assert_eq!(key, AutoTagService::cache_key(&query(Some("A"), Some("B"), Some("C"))));
        assert_ne!(key, AutoTagService::cache_key(&query(Some("A"), Some("B"), Some("D"))));
        let mut with_duration = query(Some("A"), Some("B"), Some("C"));
        with_duration.duration_secs = Some(200);
        assert_ne!(key, AutoTagService::cache_key(&with_duration));
    }
}
