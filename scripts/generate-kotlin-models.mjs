#!/usr/bin/env node
/**
 * Emit Kotlin data classes for every schema in an OpenAPI document.
 *
 * Invoked by scripts/generate-api-clients.sh — not meant to be run by hand.
 *
 * Why a 200-line generator instead of openapi-generator: openapi-generator needs
 * a JVM and emits an entire Retrofit client with its own model conventions,
 * which would fight the hand-written MuorgApiService the Android app already
 * has. All the Android side actually needs from the contract is the *shapes*, so
 * that a field renamed in Rust breaks the Kotlin build instead of quietly
 * deserializing to null. That is what this emits.
 *
 * The input is narrow by construction — utoipa produces objects, arrays, $refs,
 * `type: [x, "null"]` unions, string enums, and internally-tagged oneOf — so the
 * generator handles exactly those and throws on anything it does not recognise
 * rather than guessing.
 */

import { readFileSync, writeFileSync } from "node:fs";

const [, , specPath, outPath] = process.argv;
if (!specPath || !outPath) {
  console.error("usage: generate-kotlin-models.mjs <openapi.json> <out.kt>");
  process.exit(2);
}

const spec = JSON.parse(readFileSync(specPath, "utf8"));
const schemas = spec.components?.schemas ?? {};

/** `["string", "null"]` -> { type: "string", nullable: true } */
function normalizeType(node) {
  const t = node.type;
  if (Array.isArray(t)) {
    const nonNull = t.filter((x) => x !== "null");
    if (nonNull.length !== 1) {
      throw new Error(`unsupported type union: ${JSON.stringify(t)}`);
    }
    return { type: nonNull[0], nullable: t.includes("null") };
  }
  return { type: t, nullable: false };
}

/**
 * `#[serde(flatten)]` shows up as a top-level `allOf`. On the wire the fields
 * are one flat object, so flatten the composition back into a single schema.
 */
function flattenAllOf(node) {
  if (!node.allOf) return node;
  const merged = { type: "object", properties: {}, required: [], description: node.description };
  for (const part of node.allOf) {
    const resolved = flattenAllOf(part.$ref ? schemas[refName(part.$ref)] : part);
    Object.assign(merged.properties, resolved.properties ?? {});
    merged.required.push(...(resolved.required ?? []));
  }
  return merged;
}

function refName(ref) {
  const m = /^#\/components\/schemas\/(.+)$/.exec(ref);
  if (!m) throw new Error(`unsupported $ref: ${ref}`);
  return m[1];
}

/** OpenAPI schema node -> Kotlin type, e.g. `List<CatalogTrack>?` */
function kotlinType(node, required) {
  const suffix = required ? "" : "?";

  if (node.$ref) return refName(node.$ref) + suffix;

  // utoipa emits `allOf: [$ref]` when a field carries its own description.
  if (node.allOf?.length === 1) return kotlinType(node.allOf[0], required);

  // An internally-tagged Rust enum becomes oneOf; model it as the sealed
  // interface we emit separately.
  if (node.oneOf) return (node.title ?? "JsonElement") + suffix;

  const { type, nullable } = normalizeType(node);
  const nul = nullable || !required ? "?" : "";

  switch (type) {
    case "string":
      return "String" + nul;
    case "boolean":
      return "Boolean" + nul;
    case "integer":
      return (node.format === "int64" ? "Long" : "Int") + nul;
    case "number":
      return (node.format === "float" ? "Float" : "Double") + nul;
    case "array":
      return `List<${kotlinType(node.items ?? {}, true)}>` + nul;
    case "object":
      // Free-form maps (rare here) stay untyped rather than inventing a class.
      return "Map<String, Any?>" + nul;
    case undefined:
      return "Any?";
    default:
      throw new Error(`unsupported schema type: ${type}`);
  }
}

function docComment(text, indent = "") {
  if (!text) return "";
  const lines = String(text).trim().split("\n");
  if (lines.length === 1) return `${indent}/** ${lines[0]} */\n`;
  return (
    `${indent}/**\n` +
    lines.map((l) => `${indent} * ${l}`.trimEnd()).join("\n") +
    `\n${indent} */\n`
  );
}

function emitEnum(name, node) {
  const values = node.enum.map((v) => {
    const constName = String(v)
      .replace(/[^A-Za-z0-9]+/g, "_")
      .replace(/^(\d)/, "_$1")
      .toUpperCase();
    return `    @SerialName("${v}") ${constName}`;
  });
  return (
    docComment(node.description) +
    `@Serializable\nenum class ${name} {\n${values.join(",\n")}\n}\n`
  );
}

function emitDataClass(name, node) {
  const props = node.properties ?? {};
  const required = new Set(node.required ?? []);
  const entries = Object.entries(props);

  if (entries.length === 0) {
    return docComment(node.description) + `@Serializable\nclass ${name}\n`;
  }

  // Kotlin reads better with the required parameters first; JSON field order
  // does not matter, and it keeps positional construction usable.
  entries.sort(([a], [b]) => Number(required.has(b)) - Number(required.has(a)));

  const fields = entries.map(([prop, sub]) => {
    const isRequired = required.has(prop);
    const type = kotlinType(sub, isRequired);
    const camel = prop.replace(/_([a-z0-9])/g, (_, c) => c.toUpperCase());
    const rename = camel === prop ? "" : `@SerialName("${prop}") `;
    const dflt = type.endsWith("?") ? " = null" : "";
    return (
      docComment(sub.description, "    ") +
      `    ${rename}val ${camel}: ${type}${dflt}`
    );
  });

  return (
    docComment(node.description) +
    `@Serializable\ndata class ${name}(\n${fields.join(",\n")}\n)\n`
  );
}

/** Internally-tagged Rust enum (`#[serde(tag = "status")]`) -> sealed interface. */
function emitSealed(name, node) {
  const variants = node.oneOf.map((v) => {
    const tagProp = v.properties?.[node.discriminator?.propertyName ?? "status"];
    const tag = tagProp?.enum?.[0] ?? "unknown";
    const variantName = tag.charAt(0).toUpperCase() + tag.slice(1);
    const extra = Object.entries(v.properties ?? {}).filter(
      ([p]) => p !== (node.discriminator?.propertyName ?? "status"),
    );
    if (extra.length === 0) {
      return `    @Serializable\n    @SerialName("${tag}")\n    object ${variantName} : ${name}`;
    }
    const required = new Set(v.required ?? []);
    const fields = extra
      .map(([p, sub]) => {
        const type = kotlinType(sub, required.has(p));
        const camel = p.replace(/_([a-z0-9])/g, (_, c) => c.toUpperCase());
        const rename = camel === p ? "" : `@SerialName("${p}") `;
        const dflt = type.endsWith("?") ? " = null" : "";
        return `${rename}val ${camel}: ${type}${dflt}`;
      })
      .join(", ");
    return `    @Serializable\n    @SerialName("${tag}")\n    data class ${variantName}(${fields}) : ${name}`;
  });
  const tagField = node.discriminator?.propertyName ?? "status";
  return (
    docComment(node.description) +
    `@OptIn(ExperimentalSerializationApi::class)\n` +
    `@Serializable\n@JsonClassDiscriminator("${tagField}")\n` +
    `sealed interface ${name} {\n${variants.join("\n\n")}\n}\n`
  );
}

const blocks = [];
let needsDiscriminatorImport = false;
for (const name of Object.keys(schemas).sort()) {
  const node = schemas[name];
  try {
    if (node.enum) blocks.push(emitEnum(name, node));
    else if (node.oneOf) {
      needsDiscriminatorImport = true;
      blocks.push(emitSealed(name, node));
    } else blocks.push(emitDataClass(name, flattenAllOf(node)));
  } catch (e) {
    throw new Error(`while generating ${name}: ${e.message}`);
  }
}

const header = `// GENERATED FILE — do not edit.
//
// Source: server/openapi.json, itself derived from the muorg-server route
// handlers. Regenerate with ./scripts/generate-api-clients.sh after any API
// change; CI fails if this file is stale.
//
// These are the wire shapes only; the Retrofit interface that uses them is
// hand-written in MuorgApiService.kt. They live in their own package because the
// app's own models in ../ApiModels.kt still shadow some of these names while
// they are migrated onto the generated ones.

package nl.muorg.android.data.api.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
${needsDiscriminatorImport ? "import kotlinx.serialization.ExperimentalSerializationApi\nimport kotlinx.serialization.json.JsonClassDiscriminator\n" : ""}
`;

writeFileSync(outPath, header + blocks.join("\n"));
console.log(`    ${Object.keys(schemas).length} schemas -> ${outPath}`);
