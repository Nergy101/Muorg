import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import { useSettingsStore } from "./stores/settings";
import "./style.css";

const pinia = createPinia();
const app = createApp(App);
app.use(pinia);

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
