module.exports = {
  title: "Muorg",
  tagline: "The Music Organizer from Hell",
  url: "https://muorg.nousresearch.com",
  baseUrl: "/",
  onBrokenLinks: "warn",
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
        },
        blog: false,
        theme: {
          customCss: require.resolve("./src/css/custom.css"),
        },
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
      items: [
        { type: "doc", docId: "intro", position: "left", label: "Docs" },
        { type: "doc", docId: "desktop/index", position: "left", label: "Desktop" },
        { type: "doc", docId: "server/index", position: "left", label: "Server" },
        { type: "doc", docId: "web-client/index", position: "left", label: "Web" },
        { type: "doc", docId: "android/index", position: "left", label: "Android" },
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
          title: "Docs",
          items: [
            { label: "Getting Started", to: "/docs/intro" },
            { label: "Desktop App", to: "/docs/desktop/" },
            { label: "Server Setup", to: "/docs/server/" },
            { label: "Web Client", to: "/docs/web-client/" },
            { label: "Android", to: "/docs/android/" },
          ],
        },
        {
          title: "Community",
          items: [
            { label: "GitHub", href: "https://github.com/Nergy101/Muorg" },
            { label: "Issues", href: "https://github.com/Nergy101/Muorg/issues" },
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
