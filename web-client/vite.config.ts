import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";
import { resolve } from "path";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    vue(),
    VitePWA({
      registerType: "autoUpdate",
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
  base: "./",
  resolve: {
    alias: {
      "@": resolve(__dirname, "src"),
      "@shared": resolve(__dirname, "../src"),
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
