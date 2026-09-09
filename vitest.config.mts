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
      // own "@" alias, so it has to resolve here too. Types only — nothing is
      // executed from client/src by these tests.
      "@": resolve(root, "client/src"),
      // openapi-fetch, feather-icons and vue are NOT aliased into an app's
      // node_modules: `src/` is its own project and declares them at the root.
      // Reaching across worked locally, where every app is installed, and broke
      // in CI, where this job installs only the root. `vue` in particular must
      // stay unaliased — @vitejs/plugin-vue compiles the SFCs against the
      // root's own @vue/compiler-sfc, and pointing the runtime at a different
      // copy makes template refs land on hoisted vnodes and prop updates stop
      // re-rendering.
    },
  },
  test: {
    // Only the shared tree. Each app runs its own tests with its own
    // dependencies (web-client/vitest.config.ts) — pulling theirs in here
    // meant this project needed pinia, vue-router and everything else they
    // depend on, which is backwards.
    include: ["src/**/*.test.ts"],
    environment: "happy-dom",
    coverage: {
      provider: "v8",
      reporter: ["text-summary", "text"],
      // Only the modules these tests are actually aimed at. Listing every file
      // in the repo would report a coverage number that means nothing.
      include: ["src/**/*.ts", "src/components/**/*.vue"],
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
