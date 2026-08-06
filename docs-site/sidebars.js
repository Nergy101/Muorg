module.exports = {
  docs: [
    { type: "doc", id: "intro", label: "Welcome" },
    { type: "doc", id: "installation", label: "Installation" },
    {
      type: "category",
      label: "Quick Start",
      collapsed: false,
      items: [
        "quick-start",
        {
          type: "doc",
          id: "desktop/index",
          label: "Run Locally",
        },
        {
          type: "doc",
          id: "server/index",
          label: "Self-host",
        },
      ],
    },
    {
      type: "category",
      label: "Desktop App",
      link: { type: "doc", id: "desktop/index" },
      collapsed: false,
      items: [
        "desktop/index",
      ],
    },
    {
      type: "category",
      label: "Server",
      link: { type: "doc", id: "server/index" },
      collapsed: false,
      items: [
        "server/index",
        "server/configuration",
        "server/api",
        "server/docker",
      ],
    },
    {
      type: "category",
      label: "Web App",
      link: { type: "doc", id: "web-client/index" },
      items: [
        "web-client/index",
      ],
    },
    {
      type: "category",
      label: "Android App",
      link: { type: "doc", id: "android/index" },
      items: [
        "android/index",
      ],
    },
    {
      type: "category",
      label: "Development",
      link: { type: "doc", id: "development/index" },
      items: [
        "development/index",
      ],
    },
    { type: "doc", id: "releases", label: "Release Notes" },
    { type: "doc", id: "faq", label: "FAQ & Troubleshooting" },
    { type: "doc", id: "compatibility", label: "Version Compatibility" },
  ],
};
