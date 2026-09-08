import { defineConfig } from "vitest/config";
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
  resolve: {
    alias: {
      "@shared": resolve(root, "src"),
      // The shared client imports openapi-fetch, which is installed in the apps
      // rather than at the root — same aliasing the two vite configs do.
      "openapi-fetch": resolve(root, "web-client/node_modules/openapi-fetch"),
      vue: resolve(root, "web-client/node_modules/vue"),
    },
  },
  test: {
    include: ["src/**/*.test.ts", "web-client/src/**/*.test.ts", "client/src/**/*.test.ts"],
    environment: "happy-dom",
    coverage: {
      provider: "v8",
      reporter: ["text-summary", "text"],
      // Only the modules these tests are actually aimed at. Listing every file
      // in the repo would report a coverage number that means nothing.
      include: ["src/**/*.ts", "web-client/src/stores/library.ts"],
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
