// Injects the Muorg Orc spinner as a full-page loader as early as possible
// (client modules run before hydration paints). The overlay is removed on
// window load, on the `orc:hydrated` event dispatched by src/theme/Root.js,
// or after a 4s fallback so it can never get stuck.

const SPINNER_SVG = `
<svg viewBox="0 0 168 162" width="112" height="108" role="progressbar" aria-label="Loading" style="display:block">
  <g transform="translate(-16 -22)">
    <path d="M32,28 L84,50 L50,94 Z" fill="#5B7C32"/>
    <path d="M168,28 L116,50 L150,94 Z" fill="#5B7C32"/>
    <path d="M28,108 A72,72 0 1,0 172,108 A72,72 0 1,0 28,108 Z" fill="#759346"/>
    <path d="M38,108 A62,62 0 1,0 162,108 A62,62 0 1,0 38,108 Z" fill="none" stroke="#5B7C32" stroke-width="3" opacity="0.45"/>
    <path d="M48,108 A52,52 0 1,0 152,108 A52,52 0 1,0 48,108 Z" fill="none" stroke="#5B7C32" stroke-width="3" opacity="0.45"/>
    <g class="orc-loader-grooves">
      <path d="M46.4,88.5 A57,57 0 0,1 75.9,56.3" fill="none" stroke="#A3C46E" stroke-width="6" stroke-linecap="round"/>
      <path d="M153.6,127.5 A57,57 0 0,1 124.1,159.7" fill="none" stroke="#8FB35A" stroke-width="5" stroke-linecap="round"/>
    </g>
    <path d="M60,108 A40,40 0 1,0 140,108 A40,40 0 1,0 60,108 Z" fill="#5B7C32"/>
    <path d="M71,106 Q82,92 93,106" fill="none" stroke="#1C1917" stroke-width="5" stroke-linecap="round"/>
    <path d="M107,106 Q118,92 129,106" fill="none" stroke="#1C1917" stroke-width="5" stroke-linecap="round"/>
    <path d="M95,117 A5,4 0 1,0 105,117 A5,4 0 1,0 95,117 Z" fill="#759346"/>
    <path d="M81,127 Q100,144 119,127" fill="none" stroke="#1C1917" stroke-width="5" stroke-linecap="round"/>
    <path d="M84,129 L88,115 L94,135 Z" fill="#F5F5F4"/>
    <path d="M116,129 L112,115 L106,135 Z" fill="#F5F5F4"/>
  </g>
</svg>`;

const CSS = `
#orc-loader {
  position: fixed;
  inset: 0;
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--ifm-background-color, #fff);
  transition: opacity 0.25s ease;
}
#orc-loader.orc-loader-hidden { opacity: 0; pointer-events: none; }
#orc-loader .orc-loader-grooves {
  transform-origin: 84px 86px;
  animation: orc-loader-spin 1.1s linear infinite;
}
@keyframes orc-loader-spin { to { transform: rotate(360deg); } }
@media (prefers-reduced-motion: reduce) {
  #orc-loader .orc-loader-grooves { animation: none; }
}
`;

function removeLoader() {
  const el = document.getElementById("orc-loader");
  if (!el) return;
  el.classList.add("orc-loader-hidden");
  setTimeout(() => el.remove(), 300);
}

if (typeof document !== "undefined" && !document.getElementById("orc-loader")) {
  const style = document.createElement("style");
  style.textContent = CSS;
  document.head.appendChild(style);

  const loader = document.createElement("div");
  loader.id = "orc-loader";
  loader.innerHTML = SPINNER_SVG;
  document.body.appendChild(loader);

  window.addEventListener("load", removeLoader, { once: true });
  window.addEventListener("orc:hydrated", removeLoader, { once: true });
  setTimeout(removeLoader, 4000);
}
