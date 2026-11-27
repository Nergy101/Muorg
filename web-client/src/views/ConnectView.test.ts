import { beforeEach, describe, expect, it, vi } from "vitest";
import { createApp, nextTick } from "vue";
import ConnectView from "./ConnectView.vue";

const mocks = vi.hoisted(() => ({
  testConnection: vi.fn(),
  loadData: vi.fn(),
  replace: vi.fn(),
}));

vi.mock("../api/client", () => ({
  getServerUrl: () => "",
  getApiKey: () => "",
  setServerUrl: vi.fn(),
  setApiKey: vi.fn(),
  testConnection: (...args: unknown[]) => mocks.testConnection(...args),
}));
vi.mock("../router", () => ({
  router: { replace: mocks.replace },
  loadData: (...args: unknown[]) => mocks.loadData(...args),
}));

const profileStoreStub = {
  profiles: [] as { id: number; name: string; is_default: boolean }[],
  activeProfileId: 1,
  enabled: false,
  bindContext: vi.fn(),
  hasStoredChoice: vi.fn(() => true),
  refresh: vi.fn(async () => {}),
  setActive: vi.fn(),
};
vi.mock("../stores/profiles", () => ({
  useProfilesStore: () => profileStoreStub,
}));
vi.mock("../components/MageIcon.vue", () => ({
  default: { props: ["name"], template: "<span aria-hidden=\"true\"></span>" },
}));

beforeEach(() => {
  vi.clearAllMocks();
  mocks.testConnection.mockResolvedValue(undefined);
  mocks.loadData.mockResolvedValue(undefined);
});

describe("ConnectView", () => {
  function renderConnectView() {
    const root = document.createElement("div");
    document.body.append(root);
    const app = createApp(ConnectView);
    app.mount(root);
    return {
      root,
      unmount() {
        app.unmount();
        root.remove();
      },
    };
  }

  it("associates labels with fields and announces connection errors", async () => {
    mocks.testConnection.mockRejectedValue(new Error("Server unavailable"));
    const view = renderConnectView();
    const serverUrl = view.root.querySelector<HTMLInputElement>("#server-url")!;
    const apiKey = view.root.querySelector<HTMLInputElement>("#api-key")!;
    expect(view.root.querySelector<HTMLLabelElement>('label[for="server-url"]')?.htmlFor).toBe(serverUrl.id);
    expect(view.root.querySelector<HTMLLabelElement>('label[for="api-key"]')?.htmlFor).toBe(apiKey.id);

    serverUrl.value = "http://localhost:7700";
    serverUrl.dispatchEvent(new Event("input", { bubbles: true }));
    apiKey.value = "secret";
    apiKey.dispatchEvent(new Event("input", { bubbles: true }));
    view.root.querySelector("form")!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    await Promise.resolve();
    await nextTick();

    expect(view.root.querySelector('[role="alert"]')?.textContent).toContain("Server unavailable");
    view.unmount();
  });

  it("exposes the API key only while the named visibility control is pressed", async () => {
    const view = renderConnectView();
    const input = view.root.querySelector<HTMLInputElement>("#api-key")!;
    const toggle = view.root.querySelector<HTMLButtonElement>('button[aria-label="Show API key"]')!;

    expect(input.type).toBe("password");
    expect(toggle.getAttribute("aria-pressed")).toBe("false");
    toggle.click();
    await nextTick();

    expect(input.type).toBe("text");
    expect(view.root.querySelector('button[aria-label="Hide API key"]')?.getAttribute("aria-pressed")).toBe("true");
    view.unmount();
  });

  it("asks which profile to listen as when the server has several and none was stored", async () => {
    profileStoreStub.profiles = [
      { id: 1, name: "You", is_default: true },
      { id: 2, name: "Partner", is_default: false },
    ];
    profileStoreStub.enabled = true;
    profileStoreStub.hasStoredChoice.mockReturnValue(false);

    const view = renderConnectView();
    const serverUrl = view.root.querySelector<HTMLInputElement>("#server-url")!;
    const apiKey = view.root.querySelector<HTMLInputElement>("#api-key")!;
    serverUrl.value = "http://localhost:7700";
    serverUrl.dispatchEvent(new Event("input", { bubbles: true }));
    apiKey.value = "secret";
    apiKey.dispatchEvent(new Event("input", { bubbles: true }));
    view.root.querySelector("form")!.dispatchEvent(new Event("submit", { bubbles: true, cancelable: true }));
    for (let i = 0; i < 6; i++) await Promise.resolve();
    await nextTick();

    // Not navigated yet — the picker is shown instead.
    expect(mocks.replace).not.toHaveBeenCalled();
    const partnerBtn = [...view.root.querySelectorAll("button")].find((b) =>
      b.textContent?.includes("Partner"),
    )!;
    expect(partnerBtn).toBeDefined();
    partnerBtn.click();
    for (let i = 0; i < 6; i++) await Promise.resolve();
    await nextTick();

    expect(profileStoreStub.setActive).toHaveBeenCalledWith(2);
    expect(mocks.loadData).toHaveBeenCalled();
    expect(mocks.replace).toHaveBeenCalledWith({ name: "library" });
    view.unmount();

    profileStoreStub.profiles = [];
    profileStoreStub.enabled = false;
    profileStoreStub.hasStoredChoice.mockReturnValue(true);
  });
});
