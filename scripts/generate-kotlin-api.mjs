#!/usr/bin/env node
/**
 * Emit a Retrofit interface for every operation in an OpenAPI document.
 *
 * Invoked by scripts/generate-api-clients.sh — not meant to be run by hand.
 * The companion generator (generate-kotlin-models.mjs) emits the data classes
 * this interface returns.
 *
 * Together they are the Android equivalent of what openapi-fetch gives the two
 * TypeScript apps: the call surface is derived from the spec, so a route or a
 * parameter that changes on the server fails the Kotlin build instead of
 * silently 404ing or deserializing to null.
 *
 * Conventions, all chosen to match what the app already does by hand:
 *   - every method returns `Response<T>`, so callers can see the status rather
 *     than only catching an exception;
 *   - a non-JSON success (audio, cover art, the Prometheus text) returns
 *     `Response<ResponseBody>` — the bytes, undecoded;
 *   - a success with no body returns `Response<Unit>`;
 *   - DELETE with a request body uses @HTTP(hasBody = true), because Retrofit's
 *     @DELETE cannot carry one.
 */

import { readFileSync, writeFileSync } from "node:fs";

const [, , specPath, outPath] = process.argv;
if (!specPath || !outPath) {
  console.error("usage: generate-kotlin-api.mjs <openapi.json> <out.kt>");
  process.exit(2);
}

const spec = JSON.parse(readFileSync(specPath, "utf8"));

const METHODS = ["get", "post", "put", "patch", "delete"];

function camel(snake) {
  return snake.replace(/_([a-z0-9])/g, (_, c) => c.toUpperCase());
}

function refName(ref) {
  const m = /^#\/components\/schemas\/(.+)$/.exec(ref);
  if (!m) throw new Error(`unsupported $ref: ${ref}`);
  return m[1];
}

/**
 * `Option<T>` in Rust becomes `oneOf: [null, T]`. That is a nullable T, not a
 * union worth its own sealed type — unwrap it, or the generator falls through
 * to `Unit` and the response body silently disappears.
 */
function unwrapNullableOneOf(node) {
  if (!Array.isArray(node?.oneOf) || node.oneOf.length !== 2) return null;
  const nullBranch = node.oneOf.find((b) => b.type === "null");
  const valueBranch = node.oneOf.find((b) => b.type !== "null");
  return nullBranch && valueBranch ? valueBranch : null;
}

/** Schema node -> Kotlin type. Mirrors generate-kotlin-models.mjs. */
function kotlinType(node, required = true) {
  const nul = required ? "" : "?";
  if (!node) return "Unit";
  if (node.$ref) return refName(node.$ref) + nul;
  if (node.allOf?.length === 1) return kotlinType(node.allOf[0], required);

  const nullableInner = unwrapNullableOneOf(node);
  if (nullableInner) return kotlinType(nullableInner, false);

  const raw = Array.isArray(node.type)
    ? node.type.filter((t) => t !== "null")[0]
    : node.type;
  const nullable = Array.isArray(node.type) && node.type.includes("null");
  const q = nullable || !required ? "?" : "";

  switch (raw) {
    case "string":
      return "String" + q;
    case "boolean":
      return "Boolean" + q;
    case "integer":
      return (node.format === "int64" ? "Long" : "Int") + q;
    case "number":
      return (node.format === "float" ? "Float" : "Double") + q;
    case "array":
      return `List<${kotlinType(node.items, true)}>` + q;
    default:
      return "Unit";
  }
}

/** The 2xx response body: a model, ResponseBody for raw bytes, or Unit. */
function successType(op) {
  for (const status of ["200", "201", "204", "206"]) {
    const res = op.responses?.[status];
    if (!res) continue;
    const content = res.content;
    if (!content) return "Unit";
    if (content["application/json"]) {
      return kotlinType(content["application/json"].schema, true);
    }
    // Audio, images, Prometheus text — hand back the raw body.
    return "ResponseBody";
  }
  return "Unit";
}

function requestBodyType(op) {
  const schema = op.requestBody?.content?.["application/json"]?.schema;
  return schema ? kotlinType(schema, true) : null;
}

function docComment(op, path, method) {
  const lines = [];
  if (op.summary) lines.push(...op.summary.trim().split("\n"));
  if (op.description && op.description !== op.summary) {
    if (lines.length) lines.push("");
    lines.push(...op.description.trim().split("\n"));
  }
  if (lines.length === 0) return `    /** \`${method.toUpperCase()} ${path}\` */\n`;
  return (
    "    /**\n" +
    lines.map((l) => `     * ${l}`.trimEnd()).join("\n") +
    `\n     *\n     * \`${method.toUpperCase()} ${path}\`\n     */\n`
  );
}

const methods = [];
const usesResponseBody = { value: false };

for (const [path, item] of Object.entries(spec.paths).sort()) {
  for (const method of METHODS) {
    const op = item[method];
    if (!op) continue;

    const name = camel(op.operationId);
    const retrofitPath = path.replace(/^\//, "");
    const params = op.parameters ?? [];
    const body = requestBodyType(op);
    const success = successType(op);
    if (success === "ResponseBody") usesResponseBody.value = true;

    const args = [];
    for (const p of params.filter((p) => p.in === "path")) {
      args.push(
        `@Path("${p.name}") ${camel(p.name)}: ${kotlinType(p.schema, true)}`,
      );
    }
    for (const p of params.filter((p) => p.in === "query")) {
      const type = kotlinType(p.schema, p.required === true);
      const dflt = type.endsWith("?") ? " = null" : "";
      args.push(`@Query("${p.name}") ${camel(p.name)}: ${type}${dflt}`);
    }
    if (body) args.push(`@Body body: ${body}`);

    // Retrofit's @DELETE has no body; @HTTP does.
    const annotation =
      method === "delete" && body
        ? `    @HTTP(method = "DELETE", path = "${retrofitPath}", hasBody = true)`
        : `    @${method.toUpperCase()}("${retrofitPath}")`;

    const signature =
      args.length === 0
        ? `    suspend fun ${name}(): Response<${success}>`
        : `    suspend fun ${name}(\n` +
          args.map((a) => `        ${a},`).join("\n") +
          `\n    ): Response<${success}>`;

    methods.push(docComment(op, path, method) + annotation + "\n" + signature);
  }
}

const header = `// GENERATED FILE — do not edit.
//
// Source: server/openapi.json, itself derived from the muorg-server route
// handlers. Regenerate with ./scripts/generate-api-clients.sh after any API
// change; CI fails if this file is stale.
//
// One method per operation in the spec. Repositories map these wire types onto
// the app's own models — see WireMapping.kt in the data layer.

package nl.muorg.android.data.api.schema

import retrofit2.Response
${usesResponseBody.value ? "import okhttp3.ResponseBody\n" : ""}import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * The complete Muorg server API, generated from its OpenAPI document.
 */
interface MuorgApi {

`;

writeFileSync(outPath, header + methods.join("\n\n") + "\n}\n");
console.log(`    ${methods.length} operations -> ${outPath}`);
