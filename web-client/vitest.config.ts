import { defineConfig, mergeConfig } from "vitest/config";
import viteConfig from "./vite.config";

/**
 * The web client's own tests — the Pinia stores.
 *
 * Extends the app's vite config so aliases (`@shared`, `openapi-fetch`) and
 * plugins resolve exactly as they do in a real build, and so this project's own
 * dependencies (pinia, vue-router) are the ones in scope. The shared `src/`
 * tree has its own suite at the repo root.
 */
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      include: ["src/**/*.test.ts"],
      // The settings store reads localStorage at construction.
      environment: "happy-dom",
      coverage: {
        provider: "v8",
        reporter: ["text-summary", "text"],
        include: ["src/stores/library.ts", "src/stores/cast.ts", "src/composables/useAutoTag.ts"],
        thresholds: { lines: 60, functions: 60, branches: 60, statements: 60 },
      },
    },
  }),
);
