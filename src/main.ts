import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import { useSettingsStore } from "./stores/settings";
import "./style.css";

const pinia = createPinia();
const app = createApp(App);
app.use(pinia);
useSettingsStore().initTheme();
app.mount("#app");
