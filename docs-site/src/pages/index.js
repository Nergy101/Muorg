import React from "react";
import clsx from "clsx";
import Link from "@docusaurus/Link";
import useDocusaurusContext from "@docusaurus/useDocusaurusContext";
import Layout from "@theme/Layout";
import useBaseUrl from "@docusaurus/useBaseUrl";
import styles from "./index.module.css";

const components = [
  {
    title: "Desktop App",
    description:
      "The full editing experience on macOS, Windows and Linux. Track table, album grid, metadata editor with MusicBrainz auto-tagging, smart playlists and library reports.",
    to: "/docs/desktop/",
    icon: "🖥️",
  },
  {
    title: "Web App",
    description:
      "A mobile-first browser UI (Vue 3 + Vite) that connects to your server. Installable as a PWA, no app store, works on every device with a browser.",
    to: "/docs/web-client/",
    icon: "🌐",
  },
  {
    title: "Android App",
    description:
      "A native app (Kotlin + Jetpack Compose) that streams from your server or plays an on-device library. Home-screen widget, Chromecast and a sleep timer.",
    to: "/docs/android/",
    icon: "📱",
  },
  {
    title: "Server",
    description:
      "A standalone REST API (Rust + Axum) that serves your library to every client. Docker-friendly, streams from local folders or S3-compatible cloud storage.",
    to: "/docs/server/",
    icon: "🗄️",
  },
];

function Hero() {
  const { siteConfig } = useDocusaurusContext();
  return (
    <header className={clsx("hero", styles.heroBanner)}>
      <div className="container">
        <img className={styles.heroLogo} src={useBaseUrl("img/logo.svg")} alt="Muorg logo" />
        <h1 className="hero__title">{siteConfig.title}</h1>
        <p className="hero__subtitle">{siteConfig.tagline}</p>
        <p className={styles.heroLead}>
          A cross-platform music organizer for people who care about their metadata.
          Organize, clean up and play your library — locally or from your own server.
        </p>
        <div className={styles.buttons}>
          <Link className="button button--primary button--lg" to="/docs/quick-start">
            Quick Start
          </Link>
          <Link
            className="button button--secondary button--lg"
            to="https://github.com/Nergy101/Muorg/releases"
          >
            Download
          </Link>
        </div>
      </div>
    </header>
  );
}

function FeatureCards() {
  return (
    <section className={styles.features}>
      <div className="container">
        <div className="row">
          {components.map((c) => (
            <div className={clsx("col col--3", styles.featureCol)} key={c.title}>
              <Link className={styles.featureCard} to={c.to}>
                <div className={styles.featureIcon}>{c.icon}</div>
                <h3>{c.title}</h3>
                <p>{c.description}</p>
              </Link>
            </div>
          ))}
        </div>
      </div>
    </section>
  );
}

function GitHubLinks() {
  return (
    <section className={styles.github}>
      <div className="container">
        <h2>Open source, self-hosted</h2>
        <p>
          Muorg is built in the open. Report a bug, request a feature, or browse the
          source on GitHub.
        </p>
        <div className={styles.buttons}>
          <Link className="button button--secondary" to="https://github.com/Nergy101/Muorg">
            GitHub Repository
          </Link>
          <Link className="button button--secondary" to="https://github.com/Nergy101/Muorg/issues">
            Issues
          </Link>
        </div>
      </div>
    </section>
  );
}

export default function Home() {
  const { siteConfig } = useDocusaurusContext();
  return (
    <Layout
      title={`${siteConfig.title} — ${siteConfig.tagline}`}
      description="Muorg — a cross-platform music organizer. Desktop, server, web and Android clients for managing and playing your music library."
    >
      <main>
        <Hero />
        <FeatureCards />
        <GitHubLinks />
      </main>
    </Layout>
  );
}
