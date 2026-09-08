import { defineConfig } from "vitest/config";
import vue from "@vitejs/plugin-vue";
import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = fileURLToPath(new URL(".", import.meta.url));

/**
 * Tests for the code that is shared between the desktop and web apps, plus the
 * pure logic inside each of them.
 *
 * `src/` is the highest-leverage place in the repo to test: a break there
 * breaks both apps at once, which is the trade for not keeping two copies.
 * Anything needing a DOM, a Vue component tree or a live server is out of scope
 * here — component behaviour belongs in an app-level suite, and the live-server
 * checks live in scripts/smoke-api.ts.
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      "@shared": resolve(root, "src"),
      // The shared stats charts import their track type via the desktop app's
      // own "@" alias, so it has to resolve here too.
      "@": resolve(root, "client/src"),
      // The shared client imports openapi-fetch and feather-icons, which are
      // installed in the apps rather than at the root — the same aliasing both
      // vite configs do. `vue` is NOT aliased: @vitejs/plugin-vue compiles the
      // SFCs against the root's own @vue/compiler-sfc, and pointing the runtime
      // at a different copy makes template refs land on hoisted vnodes and
      // prop updates stop re-rendering.
      "openapi-fetch": resolve(root, "web-client/node_modules/openapi-fetch"),
      "feather-icons": resolve(root, "web-client/node_modules/feather-icons"),
    },
  },
  test: {
    include: [
      "src/**/*.test.ts",
      "web-client/src/**/*.test.ts",
      "client/src/**/*.test.ts",
    ],
    environment: "happy-dom",
    coverage: {
      provider: "v8",
      reporter: ["text-summary", "text"],
      // Only the modules these tests are actually aimed at. Listing every file
      // in the repo would report a coverage number that means nothing.
      include: [
        "src/**/*.ts",
        "src/components/**/*.vue",
        "web-client/src/stores/library.ts",
        "web-client/src/stores/cast.ts",
      ],
      exclude: [
        "**/*.test.ts",
        // Generated, or pure type/re-export modules with nothing to execute.
        "src/api/schema.d.ts",
        "src/api/types.ts",
        "src/api/index.ts",
      ],
      thresholds: {
        lines: 60,
        functions: 60,
        branches: 60,
        statements: 60,
      },
    },
  },
});
