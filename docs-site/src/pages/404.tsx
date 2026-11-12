import React from "react";
import Layout from "@theme/Layout";
import Link from "@docusaurus/Link";
import OrcIllustration from "@site/src/components/OrcIllustration";

export default function NotFound() {
  return (
    <Layout title="Page not found" description="This page does not exist — but the Orc does.">
      <main className="container" style={{ padding: "3rem 0", textAlign: "center" }}>
        <div style={{ maxWidth: 320, margin: "0 auto 1.5rem" }}>
          <OrcIllustration variant="not-found" />
        </div>
        <h1>404 — this record is missing</h1>
        <p>The page you asked for does not exist. The Orc checked twice.</p>
        <p>
          <Link className="button button--primary" to="/docs/intro">
            Back to the docs
          </Link>{" "}
          <Link className="button button--secondary" to="/">
            Homepage
          </Link>
        </p>
      </main>
    </Layout>
  );
}
