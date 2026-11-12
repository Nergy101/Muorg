import React from "react";
import clsx from "clsx";
import Link from "@docusaurus/Link";
import useDocusaurusContext from "@docusaurus/useDocusaurusContext";
import Layout from "@theme/Layout";
import useBaseUrl from "@docusaurus/useBaseUrl";
import styles from "./index.module.css";

// Inline SVG icons (Mage, Apache-2.0) so stroke="currentColor" inherits the
// card's CSS color — an <img>-loaded SVG would render black regardless.
function IconScreencast() {
  return (
    <svg width="36" height="36" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M4.6 20.325C4.6 19.8344 4.40509 19.3638 4.05815 19.0169C3.71121 18.6699 3.24065 18.475 2.75 18.475" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M7.56523 20.325C7.56523 19.6926 7.44068 19.0665 7.19869 18.4823C6.9567 17.8981 6.60202 17.3672 6.15488 16.9201C5.70774 16.473 5.17692 16.1183 4.59271 15.8763C4.0085 15.6343 3.38234 15.5098 2.75 15.5098" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M10.6988 20.325C10.6988 18.2168 9.86138 16.195 8.37068 14.7043C6.87999 13.2136 4.85817 12.3762 2.75 12.3762" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M13.925 20.325H17.55C18.5313 20.325 19.4724 19.9352 20.1663 19.2413C20.8602 18.5474 21.25 17.6063 21.25 16.625V7.375C21.25 6.3937 20.8602 5.45259 20.1663 4.75871C19.4724 4.06482 18.5313 3.675 17.55 3.675H6.45C5.4687 3.675 4.52759 4.06482 3.8337 4.75871C3.13982 5.45259 2.75 6.3937 2.75 7.375V9.15" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function IconGlobe() {
  return (
    <svg width="36" height="36" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M12 21.5C17.2467 21.5 21.5 17.2467 21.5 12C21.5 6.75329 17.2467 2.5 12 2.5C6.75329 2.5 2.5 6.75329 2.5 12C2.5 17.2467 6.75329 21.5 12 21.5Z" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M12 21.5C14.3319 21.5 16.2222 17.2467 16.2222 12C16.2222 6.75329 14.3319 2.5 12 2.5C9.66812 2.5 7.77776 6.75329 7.77776 12C7.77776 17.2467 9.66812 21.5 12 21.5Z" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M2.5 12H21.5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function IconRobot() {
  return (
    <svg width="36" height="36" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M14.706 4.3125H9.294C6.54279 4.3125 4.3125 6.54279 4.3125 9.294V14.706C4.3125 17.4572 6.54279 19.6875 9.294 19.6875H14.706C17.4572 19.6875 19.6875 17.4572 19.6875 14.706V9.294C19.6875 6.54279 17.4572 4.3125 14.706 4.3125Z" stroke="currentColor" strokeWidth="1.5" />
      <path d="M19.6055 15.5875H21.225C21.4968 15.5875 21.7576 15.4795 21.9498 15.2873C22.142 15.0951 22.25 14.8344 22.25 14.5625V9.43751C22.25 9.16566 22.142 8.90495 21.9498 8.71272C21.7576 8.5205 21.4968 8.41251 21.225 8.41251H19.6055" stroke="currentColor" strokeWidth="1.5" />
      <path d="M4.3945 15.5875H2.775C2.50315 15.5875 2.24244 15.4795 2.05022 15.2873C1.85799 15.0951 1.75 14.8344 1.75 14.5625V9.43751C1.75 9.16566 1.85799 8.90495 2.05022 8.71272C2.24244 8.5205 2.50315 8.41251 2.775 8.41251H4.3945" stroke="currentColor" strokeWidth="1.5" />
      <path d="M2.76474 8.4125V4.3125" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M21.225 8.4125L21.2147 4.3125" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M8.91472 8.76315V11.4605" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M15.0648 8.76315V11.4605" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M9.93975 15.5875H14.0397" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

function IconServer() {
  return (
    <svg width="36" height="36" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M3.00221 5.12738L3.01318 10.8419C3.0144 11.4782 3.53122 11.993 4.16752 11.9918L19.8479 11.9617C20.4842 11.9605 20.999 11.4437 20.9978 10.8074L20.9868 5.09285C20.9856 4.45655 20.4688 3.94172 19.8325 3.94294L4.15212 3.97305C3.51582 3.97427 3.00099 4.49108 3.00221 5.12738Z" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M17.5511 7.96198H14.5326" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M4.16345 11.9944C3.52715 11.9944 3.01133 12.5102 3.01133 13.1465L3.01133 18.9071C3.01133 19.5434 3.52715 20.0593 4.16345 20.0593H19.8438C20.4801 20.0593 20.996 19.5434 20.996 18.9071V13.1465C20.996 12.5102 20.4801 11.9944 19.8438 11.9944" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
      <path d="M17.5511 16.0269H14.5326" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
    </svg>
  );
}

const components = [
  {
    title: "Desktop App (Legacy)",
    description:
      "Legacy client for macOS, Windows and Linux — the full editing experience with track table, album grid, metadata editor, smart playlists and reports. New setups should use the Web or Android app.",
    to: "/docs/desktop/",
    Icon: IconScreencast,
  },
  {
    title: "Web App",
    description:
      "The easiest client to use Muorg, from any device, built for every viewport.",
    to: "/docs/web-client/",
    Icon: IconGlobe,
  },
  {
    title: "Android App",
    description:
      "A native app (Kotlin + Jetpack Compose) that streams from your server or plays an on-device library. Home-screen widget, Chromecast and a sleep timer.",
    to: "/docs/android/",
    Icon: IconRobot,
  },
  {
    title: "Server",
    description:
      "A standalone REST API (Rust + Axum) that serves your library to every client. Docker-friendly, streams from local folders or S3-compatible cloud storage.",
    to: "/docs/server/",
    Icon: IconServer,
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
          <Link className="button button--primary button--lg" to="/docs/intro">
            Read the Docs
          </Link>
          <Link className="button button--secondary button--lg" to="/docs/quick-start">
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
                <div className={styles.featureIcon}>
                  <c.Icon />
                </div>
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
