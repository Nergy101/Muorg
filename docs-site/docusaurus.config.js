module.exports = {
  title: "Muorg",
  tagline: "The Music Organizer from Hell",
  url: "https://docs.muorg.nergy.space",
  baseUrl: "/",
  // Broken internal links are a build failure: the docs are the product surface
  // for self-hosters, and a dead link there costs someone an evening.
  onBrokenLinks: "throw",
  markdown: {
    hooks: {
      onBrokenMarkdownLinks: "warn",
    },
  },
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

  clientModules: [require.resolve("./src/clientModules/orcLoader.js")],

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
      items: [
        // Plain `to:` links, not type:"doc" — every doc lives under /docs/,
        // so type:"doc" would mark ALL items active on every docs page.
        { to: "/docs/intro", position: "left", label: "Docs" },
        { to: "/docs/web-client/", position: "left", label: "Web" },
        { to: "/docs/android/", position: "left", label: "Android" },
        { to: "/docs/server/", position: "left", label: "Server" },
        { to: "/docs/desktop/", position: "left", label: "Desktop (Legacy)" },
        { to: "/docs/releases", position: "left", label: "Releases" },
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
            { label: "Desktop App (Legacy)", to: "/docs/desktop/" },
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
