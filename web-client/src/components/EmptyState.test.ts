import { describe, expect, it } from "vitest";
import { createApp, nextTick } from "vue";
import EmptyState from "./EmptyState.vue";

async function render(props: Record<string, unknown> = {}) {
  const root = document.createElement("div");
  document.body.append(root);
  const app = createApp(EmptyState, props);
  app.mount(root);
  await nextTick();
  return { root, done: () => { app.unmount(); root.remove(); } };
}

describe("EmptyState", () => {
  it("defaults to the generic copy with the Orc drawing", async () => {
    const { root, done } = await render();
    expect(root.textContent).toContain("It seems to be empty here");
    expect(root.querySelector("svg[role=img]")?.getAttribute("aria-label")).toMatch(/empty crate/);
    done();
  });

  it("shows a custom title and body", async () => {
    const { root, done } = await render({ title: "No playlists yet.", body: "Tap + to create one." });
    expect(root.textContent).toContain("No playlists yet.");
    expect(root.textContent).toContain("Tap + to create one.");
    done();
  });
});
