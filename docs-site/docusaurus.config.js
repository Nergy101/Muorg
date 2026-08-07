module.exports = {
  title: "Muorg",
  tagline: "The Music Organizer from Hell",
  url: "https://docs.muorg.nergy.space",
  baseUrl: "/",
  // Broken internal links are a build failure: the docs are the product surface
  // for self-hosters, and a dead link there costs someone an evening.
  onBrokenLinks: "throw",
  onBrokenMarkdownLinks: "warn",
  favicon: "img/favicon.ico",
  organizationName: "Nergy101",
  projectName: "Muorg",

  presets: [
    [
      "classic",
      {
        docs: {
          sidebarPath: require.resolve("./sidebars.js"),
          editUrl: "https://github.com/Nergy101/Muorg/edit/main/docs-site/",
          // Unversioned on purpose: every component ships in lockstep and
          // patch releases land weekly, so a frozen snapshot rots faster than
          // it helps. These docs always describe the latest release.
          // See docs/development/index.md → "Docs site".
        },
        blog: false,
        theme: {
          customCss: require.resolve("./src/css/custom.css"),
        },
      },
    ],
  ],

  plugins: [
    [
      require.resolve("@easyops-cn/docusaurus-search-local"),
      {
        hashed: true,
        docsRouteBasePath: ["/docs"],
        searchResultLimits: 8,
        searchResultContextMaxLength: 140,
      },
    ],
  ],

  themeConfig: {
    colorMode: {
      defaultMode: "dark",
      respectPrefersColorScheme: true,
    },
    navbar: {
      title: "Muorg",
      logo: {
        alt: "Muorg Logo",
        src: "img/logo.svg",
      },
      // Same order as the sidebar: apps first, then the server behind them.
      items: [
        { type: "doc", docId: "intro", position: "left", label: "Docs" },
        { type: "doc", docId: "desktop/index", position: "left", label: "Desktop" },
        { type: "doc", docId: "web-client/index", position: "left", label: "Web" },
        { type: "doc", docId: "android/index", position: "left", label: "Android" },
        { type: "doc", docId: "server/index", position: "left", label: "Server" },
        { type: "doc", docId: "releases", position: "left", label: "Releases" },
        {
          href: "https://github.com/Nergy101/Muorg",
          label: "GitHub",
          position: "right",
        },
      ],
    },
    footer: {
      style: "dark",
      links: [
        {
          title: "Get Started",
          items: [
            { label: "Welcome", to: "/docs/intro" },
            { label: "Quick Start", to: "/docs/quick-start" },
            { label: "Install & Update", to: "/docs/installation" },
          ],
        },
        {
          title: "Components",
          items: [
            { label: "Desktop App", to: "/docs/desktop/" },
            { label: "Web App", to: "/docs/web-client/" },
            { label: "Android App", to: "/docs/android/" },
            { label: "Server", to: "/docs/server/" },
          ],
        },
        {
          title: "Reference",
          items: [
            { label: "FAQ & Troubleshooting", to: "/docs/faq" },
            { label: "Version Compatibility", to: "/docs/compatibility" },
            { label: "Release Notes", to: "/docs/releases" },
            { label: "Development", to: "/docs/development/" },
          ],
        },
        {
          title: "Project",
          items: [
            { label: "GitHub", href: "https://github.com/Nergy101/Muorg" },
            { label: "Issues", href: "https://github.com/Nergy101/Muorg/issues" },
            { label: "Downloads", href: "https://github.com/Nergy101/Muorg/releases" },
          ],
        },
      ],
      copyright: `Copyright © ${new Date().getFullYear()} Christian van Dijk. Built with Docusaurus.`,
    },
    prism: {
      theme: require("prism-react-renderer").themes.dracula,
      additionalLanguages: ["rust", "toml", "kotlin", "bash", "docker", "typescript"],
    },
  },
};
