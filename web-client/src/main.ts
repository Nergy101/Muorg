import { createApp } from "vue";
import { createPinia } from "pinia";
import App from "./App.vue";
import router from "./router";
import { setUnauthorizedHandler } from "./api/client";
import { logout } from "./logout";
import "./style.css";

// A 401 on any API call means the server-side key changed: log out so the
// user lands on the connect screen and can enter the new key.
setUnauthorizedHandler(() => void logout());

createApp(App).use(createPinia()).use(router).mount("#app");
