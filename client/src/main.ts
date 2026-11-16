import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import { useSettingsStore } from "./stores/settings";
import { getBackendMode, setOnlineApiKey, setUnauthorizedHandler } from "./api/client";
import "overlayscrollbars/overlayscrollbars.css";
import "./style.css";

const pinia = createPinia();
const app = createApp(App);
app.use(pinia);

// A 401 on any API call means the server-side key changed: drop the stored
// online key and open the connection settings so the user can log in again.
// Local mode needs no key, so there is nothing to clear there.
setUnauthorizedHandler(() => {
  if (getBackendMode() !== "online") return;
  setOnlineApiKey("");
  useSettingsStore().openSettings("connection");
});

(async () => {
  const settings = useSettingsStore();
  try {
    await settings.loadFromFile();
  } catch {
    // In mock mode or when Tauri isn't available, use defaults
  }
  settings.initTheme();
  app.mount("#app");
})();
