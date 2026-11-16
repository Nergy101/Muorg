import React from "react";
import styles from "./OrcSpinner.module.css";

// The Muorg Orc record spinner: the app logo as a record. Base and face
// never move; only the grooves rotate (same geometry as the Android
// orc_spinner_* drawables, 168x162 viewport, record centre at 84,86).
export default function OrcSpinner({ size = 56 }: { size?: number }) {
  return (
    <svg
      className={styles.spinner}
      style={{ width: size, height: (size * 162) / 168 }}
      viewBox="0 0 168 162"
      role="progressbar"
      aria-label="Loading"
    >
      <g transform="translate(-16 -22)">
        {/* base */}
        <path d="M32,28 L84,50 L50,94 Z" fill="#5B7C32" />
        <path d="M168,28 L116,50 L150,94 Z" fill="#5B7C32" />
        <path d="M28,108 A72,72 0 1,0 172,108 A72,72 0 1,0 28,108 Z" fill="#759346" />
        <path d="M38,108 A62,62 0 1,0 162,108 A62,62 0 1,0 38,108 Z" fill="none" stroke="#5B7C32" strokeWidth="3" opacity="0.45" />
        <path d="M48,108 A52,52 0 1,0 152,108 A52,52 0 1,0 48,108 Z" fill="none" stroke="#5B7C32" strokeWidth="3" opacity="0.45" />
        {/* grooves (rotating) */}
        <g className={styles.grooves}>
          <path d="M46.4,88.5 A57,57 0 0,1 75.9,56.3" fill="none" stroke="#A3C46E" strokeWidth="6" strokeLinecap="round" />
          <path d="M153.6,127.5 A57,57 0 0,1 124.1,159.7" fill="none" stroke="#8FB35A" strokeWidth="5" strokeLinecap="round" />
        </g>
        {/* face */}
        <path d="M60,108 A40,40 0 1,0 140,108 A40,40 0 1,0 60,108 Z" fill="#5B7C32" />
        <path d="M71,106 Q82,92 93,106" fill="none" stroke="#1C1917" strokeWidth="5" strokeLinecap="round" />
        <path d="M107,106 Q118,92 129,106" fill="none" stroke="#1C1917" strokeWidth="5" strokeLinecap="round" />
        <path d="M95,117 A5,4 0 1,0 105,117 A5,4 0 1,0 95,117 Z" fill="#759346" />
        <path d="M81,127 Q100,144 119,127" fill="none" stroke="#1C1917" strokeWidth="5" strokeLinecap="round" />
        <path d="M84,129 L88,115 L94,135 Z" fill="#F5F5F4" />
        <path d="M116,129 L112,115 L106,135 Z" fill="#F5F5F4" />
      </g>
    </svg>
  );
}
