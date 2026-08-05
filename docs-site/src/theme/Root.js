import React, { useEffect } from "react";

// Wraps the whole site. Adds the `/` keyboard shortcut for local search
// (the @easyops-cn/docusaurus-search-local plugin ships ⌘K / Ctrl+K only).
export default function Root({ children }) {
  useEffect(() => {
    const isTyping = (e) => {
      const tag = e.target?.tagName;
      return (
        tag === "INPUT" ||
        tag === "TEXTAREA" ||
        e.target?.isContentEditable
      );
    };

    const onKeyDown = (e) => {
      // Only trigger on a bare "/" (no modifiers), and not while typing in a field.
      if (e.key === "/" && !e.metaKey && !e.ctrlKey && !e.altKey && !isTyping(e)) {
        e.preventDefault();
        const input = document.querySelector(".navbar__search-input");
        if (input) {
          input.focus();
          input.select();
        }
      }
    };

    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, []);

  return <>{children}</>;
}
