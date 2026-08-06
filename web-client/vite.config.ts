import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { resolve } from "path";
import { VitePWA } from "vite-plugin-pwa";
import pkg from "./package.json" with { type: "json" };

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      // 'prompt' (not 'autoUpdate'): a new service worker waits instead of
      // force-reloading, and the app surfaces an "update available" banner +
      // settings button (composables/usePwaUpdate.ts) so the user refreshes
      // on their own terms.
      registerType: "prompt",
      // Registration is handled manually via the virtual:pwa-register module
      // in usePwaUpdate.ts — no injected <script>.
      injectRegister: false,
      includeAssets: ["favicon.svg", "icons/*.png"],
      manifest: {
        name: "Muorg",
        short_name: "Muorg",
        description: "Your personal music library",
        theme_color: "#1c1917",
        background_color: "#1c1917",
        display: "standalone",
        start_url: "./",
        scope: "./",
        icons: [
          {
            src: "icons/pwa-192x192.png",
            sizes: "192x192",
            type: "image/png",
          },
          {
            src: "icons/pwa-512x512.png",
            sizes: "512x512",
            type: "image/png",
          },
          {
            src: "icons/pwa-512x512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
      },
      workbox: {
        cacheId: "muorg-web",
        cleanupOutdatedCaches: true,
        globPatterns: ["**/*.{js,css,html,ico,png,svg,woff2}"],
        navigateFallback: "index.html",
        runtimeCaching: [
          {
            // Never cache audio streams or API calls
            urlPattern: /\/(stream|api)\//,
            handler: "NetworkOnly",
          },
        ],
      },
    }),
  ],
  // The Docker image passes the release version as VITE_APP_VERSION (the
  // docker-web CI job never rewrites package.json), so that wins when set.
  define: {
    __APP_VERSION__: JSON.stringify(process.env.VITE_APP_VERSION || pkg.version),
  },
  base: "./",
  resolve: {
    alias: {
      "@": resolve(__dirname, "src"),
      "@shared": resolve(__dirname, "../src"),
      "feather-icons": resolve(__dirname, "node_modules/feather-icons"),
    },
  },
  server: {
    port: 7800,
  },
  build: {
    target: "es2020",
    minify: "esbuild",
  },
});
