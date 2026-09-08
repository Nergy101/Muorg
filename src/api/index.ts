/**
 * The shared Muorg API client, used by both `client/` (desktop) and
 * `web-client/`.
 *
 * ```ts
 * import { createApi, createTransport } from "@shared/api";
 *
 * const api = createApi(createTransport({
 *   baseUrl: () => localStorage.getItem("muorg-web-url") ?? "",
 *   apiKey: () => localStorage.getItem("muorg-web-key") ?? "",
 * }));
 * ```
 *
 * See `src/api/README.md` for how the types are generated and kept honest.
 */

export { createApi } from "./endpoints";
export type { MuorgApi } from "./endpoints";
export { createTransport, jsonBody, ApiError } from "./transport";
export type { Credentials, Transport } from "./transport";
export * from "./types";
