import React from "react";
import { Redirect } from "@docusaurus/router";
import useBaseUrl from "@docusaurus/useBaseUrl";

// The site has no standalone landing page — send visitors straight to the docs.
// Without this, `npm run build` emits no build/index.html, and the Docker image's
// nginx base layer serves its default "Welcome to nginx!" page at `/`.
export default function Home() {
  return <Redirect to={useBaseUrl("/docs/intro")} />;
}
