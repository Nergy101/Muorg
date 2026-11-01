import { afterEach, describe, expect, it } from "vitest";
import { createApp, nextTick } from "vue";
import { createMemoryHistory, createRouter } from "vue-router";
import ErrorView, { type ErrorCode } from "./ErrorView.vue";

const Stub = { template: "<div />" };

async function render(code: ErrorCode) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: "/", component: Stub },
      { path: "/library", name: "library", component: Stub },
      { path: "/search", name: "search", component: Stub },
      { path: "/settings", name: "settings", component: Stub },
    ],
  });
  await router.push("/");
  await router.isReady();
  const root = document.createElement("div");
  document.body.append(root);
  const app = createApp(ErrorView, { code });
  app.use(router);
  app.mount(root);
  await nextTick();
  return {
    root,
    router,
    unmount() {
      app.unmount();
      root.remove();
    },
  };
}

let current: Awaited<ReturnType<typeof render>> | null = null;
afterEach(() => current?.unmount());

describe("ErrorView", () => {
  it.each([
    ["404", "This track skipped."],
    ["403", "Backstage pass needed."],
    ["500", "Something went off-key."],
    ["503", "Tuning up. Back soon."],
  ] as const)("shows the %s copy", async (code, title) => {
    current = await render(code);
    expect(current.root.querySelector("h1")?.textContent?.trim()).toBe(title);
    expect(current.root.querySelector(`[aria-label="Error ${code}"]`)).not.toBeNull();
    expect(current.root.querySelector("svg[role=img]")).not.toBeNull();
  });

  it("only cracks the record on the 500 page", async () => {
    current = await render("500");
    expect(current.root.querySelector('[aria-label="Error 500"] path')).not.toBeNull();
    current.unmount();
    current = await render("404");
    expect(current.root.querySelector('[aria-label="Error 404"] path')).toBeNull();
  });

  it("links the 404 back to the library", async () => {
    current = await render("404");
    const links = [...current.root.querySelectorAll("a")].map((a) => a.getAttribute("href"));
    expect(links).toEqual(["/library", "/search"]);
  });

  it("offers a reload instead of a link on 500 and 503", async () => {
    for (const code of ["500", "503"] as const) {
      current = await render(code);
      expect(current.root.querySelectorAll("button")).toHaveLength(1);
      expect(current.root.querySelectorAll("a")).toHaveLength(1);
      current.unmount();
    }
    current = null;
  });
});
