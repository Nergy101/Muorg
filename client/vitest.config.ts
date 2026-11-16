import { defineConfig, mergeConfig } from "vitest/config";
import { resolve } from "path";
import viteConfig from "./vite.config";

/**
 * The desktop client's own tests — the Pinia stores.
 *
 * Extends the app's vite config so aliases (`@`, `@shared`) and plugins resolve
 * exactly as they do in a real build, and so this project's own dependencies
 * are the ones in scope. The shared `src/` tree has its own suite at the repo
 * root, and the web client has one of its own.
 */
export default mergeConfig(
  viteConfig,
  defineConfig({
    server: {
      fs: {
        // Component tests pull in @shared/components/MageIcon.vue, whose
        // import.meta.glob reads ../assets/mage-icons/*.svg?raw — files outside
        // this app's root. Vitest denies serving them unless the repo root is
        // explicitly allowed.
        allow: [resolve(__dirname, "..")],
      },
    },
    test: {
      include: ["src/**/*.test.ts"],
      environment: "happy-dom",
      coverage: {
        provider: "v8",
        reporter: ["text-summary", "text"],
        // The catalog store is the only thing this suite aims at so far, and
        // within it only the cover cache. No threshold yet: the number here
        // measures a 1000-line store against tests for one part of it, so
        // gating on it would say nothing useful until the rest is covered.
        include: ["src/stores/catalog.ts"],
      },
    },
  }),
);
