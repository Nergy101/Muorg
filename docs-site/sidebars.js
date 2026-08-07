/**
 * Docs sidebar.
 *
 * Ordering follows the reader's journey:
 *   what is this → get it running → the app you use → the server behind it →
 *   reference material → contributing.
 *
 * This file is the single source of truth for order and labels; doc pages do
 * not carry `sidebar_position` frontmatter.
 */
module.exports = {
  docs: [
    { type: "doc", id: "intro", label: "Welcome" },
    { type: "doc", id: "quick-start", label: "Quick Start" },
    { type: "doc", id: "installation", label: "Install & Update" },
    {
      type: "category",
      label: "Apps",
      collapsed: false,
      items: [
        { type: "doc", id: "desktop/index", label: "Desktop App" },
        { type: "doc", id: "web-client/index", label: "Web App" },
        { type: "doc", id: "android/index", label: "Android App" },
      ],
    },
    {
      type: "category",
      label: "Server",
      link: { type: "doc", id: "server/index" },
      collapsed: false,
      items: [
        { type: "doc", id: "server/configuration", label: "Configuration" },
        { type: "doc", id: "server/docker", label: "Docker Deployment" },
        { type: "doc", id: "server/api", label: "HTTP API" },
      ],
    },
    {
      type: "category",
      label: "Reference",
      collapsed: false,
      items: [
        { type: "doc", id: "faq", label: "FAQ & Troubleshooting" },
        { type: "doc", id: "compatibility", label: "Version Compatibility" },
        { type: "doc", id: "releases", label: "Release Notes" },
      ],
    },
    { type: "doc", id: "development/index", label: "Development" },
  ],
};
