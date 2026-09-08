/**
 * Named aliases for the generated wire types.
 *
 * `schema.d.ts` is machine-generated and awkward to import from
 * (`components["schemas"]["CatalogTrack"]`), so everything the apps actually
 * touch gets a plain name here. Renaming a field in Rust changes the generated
 * type, which breaks compilation at every use site — that is the point.
 */

import type { components, operations } from "./schema";

type Schemas = components["schemas"];

/**
 * Drops the `?` from a generated response type.
 *
 * A Rust `Option<T>` becomes a not-required OpenAPI property, so the generator
 * emits `title?: string | null`. But serde has no `skip_serializing_if` on these
 * fields, so the key is *always* in the JSON — it is just `null` when empty.
 * `?` would force every read site through an `undefined` check that can never
 * happen. Applied only to response bodies; request bodies really are optional.
 */
type AlwaysPresent<T> = Required<T>;

// ---------------------------------------------------------------- catalog ---

export type CatalogTrack = AlwaysPresent<Schemas["CatalogTrack"]>;
export type LibraryStats = AlwaysPresent<Schemas["LibraryStats"]>;
export type TrackLyrics = AlwaysPresent<Schemas["TrackLyrics"]>;
export type TrackMetadata = AlwaysPresent<Schemas["TrackMetadata"]>;
export type MetadataUpdate = Schemas["MetadataUpdate"];
export type TrackBackupRecord = AlwaysPresent<Schemas["TrackBackupRecord"]>;
export type BatchMetadataItem = Schemas["BatchMetadataItem"];
export type MatchCandidate = AlwaysPresent<Schemas["MatchCandidate"]>;

// -------------------------------------------------------------- playlists ---

export type Playlist = AlwaysPresent<Schemas["Playlist"]>;
export type PlaylistTrackEntry = AlwaysPresent<Schemas["PlaylistTrackEntry"]>;

// ------------------------------------------------------------------- cast ---

export type CastDevice = AlwaysPresent<Schemas["CastDevice"]>;
export type CastSessionStatus = Schemas["CastSessionStatus"];
export type CastStatusResponse = Schemas["CastStatusResponse"];

// ----------------------------------------------------------------- system ---

export type AdminHealthResponse = AlwaysPresent<Schemas["AdminHealthResponse"]>;
export type RescanResult = AlwaysPresent<Schemas["RescanResult"]>;
export type FetchedImage = AlwaysPresent<Schemas["FetchedImage"]>;
export type ErrorResponse = AlwaysPresent<Schemas["ErrorResponse"]>;

/**
 * The default page size of `GET /api/tracks`, straight out of the spec rather
 * than repeated as a magic 500 in each app.
 *
 * Three separate "only the first 500 tracks load" bugs — one per client — came
 * from hard-coding this and forgetting to loop. Prefer {@link fetchAllTracks}
 * or {@link streamTracks} in `endpoints.ts` over paging by hand.
 */
export const TRACKS_PAGE_SIZE = 500;

/** One page of the catalog, plus the total the server reported. */
export interface TracksPage {
  tracks: CatalogTrack[];
  /** Value of the `X-Total-Count` response header. */
  total: number;
}

export type { components, operations };
