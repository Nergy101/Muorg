---
sidebar_position: 2
---

# Smart Playlists

A smart playlist is a playlist whose tracks are **resolved from rules instead of
stored manually**. You define matching rules; the server evaluates them against
the library and the playlist's contents stay in sync automatically — add a track
that matches, and it appears in the smart playlist without anyone adding it by
hand.

Smart playlists exist on the server, so they work the same in every client
(web app, desktop app, Android app). The web app's ⚡ **New smart playlist**
button opens the rules editor.

## How membership works

- Only the **rules** are stored (the `smart_rules` column on the playlist).
  No track list is materialized.
- Membership is **re-derived live**: every time the playlist is fetched, the
  server re-runs the rules against the current library (`SELECT id FROM tracks
  WHERE <rules> ORDER BY artist, album, track_number, title`).
- `GET /api/playlists` returns each smart playlist's **live-resolved track
  count**, so the badge you see in the app is always current.
- Because there is no stored track list, manual operations don't apply:
  smart playlists have no drag-to-reorder, and the "add to playlist" picker
  hides them (adds would be discarded anyway).

## Rules format

A rule is one JSON object; a playlist's rules are a JSON array of them:

```json
[
  { "field": "genre", "op": "eq", "value": "Rock" },
  { "field": "year", "op": "gte", "value": 2010 }
]
```

- `field` — the track attribute to test (see the table below).
- `op` — the operator (see below).
- `value` — the value to compare against. Omitted entirely for
  `is_null` / `is_not_null`.

### Fields

| Field | Type | Meaning |
|-------|------|---------|
| `title` | text | Track title |
| `artist` | text | Track artist |
| `album` | text | Album name |
| `album_artist` | text | Album artist |
| `genre` | text | Genre tag |
| `year` | number | Release year |
| `rating` | number | Your star rating (0–5) |
| `play_count` | number | Times played |
| `last_played_at` | number | Unix timestamp (seconds) of the last play |
| `has_cover` | number | `1` = has album art, `0` = none |

Text fields compare as strings; numeric fields (`year`, `rating`,
`play_count`, `last_played_at`, `has_cover`) compare as numbers.

### Operators

| Operator | Meaning |
|----------|---------|
| `eq` | Equals |
| `neq` | Not equals |
| `gt` | Greater than |
| `gte` | Greater than or equal |
| `lt` | Less than |
| `lte` | Less than or equal |
| `contains` | Case-insensitive substring match |
| `is_null` | Value is empty / unset (no `value`) |
| `is_not_null` | Value is set (no `value`) |

## Combining rules

Rules on the **same field** are OR-ed; rules on **different fields** are
AND-ed.

For example, these rules match tracks that are *Rock* **or** *Metal*, **and**
from 2010 or later:

```json
[
  { "field": "genre", "op": "eq", "value": "Rock" },
  { "field": "genre", "op": "eq", "value": "Metal" },
  { "field": "year", "op": "gte", "value": 2010 }
]
```

Same-field OR lets you build "one of these values" lists; cross-field AND
narrows the result.

## Examples

**Unplayed tracks from the last two years**

```json
[
  { "field": "play_count", "op": "eq", "value": 0 },
  { "field": "year", "op": "gte", "value": 2024 }
]
```

**Rated 4★ or higher, Rock or Metal**

```json
[
  { "field": "rating", "op": "gte", "value": 4 },
  { "field": "genre", "op": "eq", "value": "Rock" },
  { "field": "genre", "op": "eq", "value": "Metal" }
]
```

**Everything with album art from the 90s**

```json
[
  { "field": "has_cover", "op": "eq", "value": 1 },
  { "field": "year", "op": "gte", "value": 1990 },
  { "field": "year", "op": "lte", "value": 1999 }
]
```

**Never played**

```json
[
  { "field": "play_count", "op": "eq", "value": 0 }
]
```

`play_count` 0 and `last_played_at` `is_null` are equivalent for never-played
tracks — a fresh library has neither set.

:::tip
`last_played_at` uses Unix seconds, so "played more than a year ago" means
`last_played_at` `lt` (now − 31,536,000). The web app's editor takes a plain
number; computing a one-off cutoff with `date +%s` on your machine is the
easiest way to get it.
:::

## Server API

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/playlists/smart` | Create a smart playlist — body `{ "name": "...", "rules_json": "<rules>" }` |
| `PATCH` | `/api/playlists/smart/{id}/rules` | Replace the rules — body `{ "rules_json": "<rules>" }` |
| `GET` | `/api/playlists/smart/{id}/tracks` | Resolve the matching track IDs (re-evaluated live) |
| `GET` | `/api/playlists` | Lists playlists; smart ones include their live `track_count` |

`rules_json` is the JSON array of rules above, as a string field.

:::note
Editing a smart playlist's rules or deleting it works like any playlist in the
clients. Rules can also be set with `PATCH /api/playlists/smart/{id}/rules` for
automation — the same endpoint the web app's ⚡ **Edit rules** button uses.
:::
