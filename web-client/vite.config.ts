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
            // API calls always go to the server — never cache them.
            // The patterns must be start-anchored and origin-agnostic: the
            // API server is usually a different origin than the web client,
            // and workbox's RegExpRoute only matches a cross-origin URL when
            // the regex matches at index 0 of it — a bare /\/api\// would
            // never match and the route would be dead.
            urlPattern: /^https?:\/\/[^/]+\/(?:.*\/)?api\//,
            handler: "NetworkOnly",
          },
          {
            // Audio streams: a cached track (a whole file written by the
            // offline store, src/stores/offline.ts) is served from Cache
            // Storage; everything else goes to the network and is never
            // cached. The Cache API cannot store partial (206) range
            // responses, and caching every played track would grow without
            // bound, so cacheWillUpdate only lets responses explicitly
            // marked ?offline=1 through — and those are written by the
            // offline store via cache.put, not by the SW. Start-anchored
            // for the same cross-origin reason as the /api/ route above —
            // without it the SW never serves cached tracks and offline
            // playback silently falls through to the network.
            urlPattern: /^https?:\/\/[^/]+\/(?:.*\/)?stream\//,
            handler: "CacheFirst",
            options: {
              cacheName: "muorg-offline-tracks",
              matchOptions: { ignoreSearch: true },
              plugins: [
                {
                  cacheWillUpdate: async ({
                    request,
                    response,
                  }: {
                    request: Request;
                    response: Response;
                  }): Promise<Response | null> =>
                    new URL(request.url).searchParams.has("offline") &&
                    response.status === 200
                      ? response
                      : null,
                },
              ],
            },
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
      // ../src (the @shared tree) sits outside this app's node_modules, so the
      // shared API client needs openapi-fetch resolved explicitly.
      "openapi-fetch": resolve(__dirname, "node_modules/openapi-fetch"),
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
