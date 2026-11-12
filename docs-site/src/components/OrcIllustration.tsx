import React from "react";

// Port of the web client's OrcIllustration.vue — the Muorg Orc: the logo's
// vinyl-record head on a small round body, one scene per variant.
export type OrcVariant = "not-found" | "server-error" | "forbidden" | "empty" | "maintenance";

const LABELS: Record<OrcVariant, string> = {
  "not-found": "A small Orc scratching its head and holding an empty record sleeve",
  "server-error": "A small Orc panicking next to a guitar with a snapped string",
  forbidden: "A small Orc bouncer in sunglasses with crossed arms between two velvet ropes",
  empty: "A small Orc shrugging while holding an empty crate",
  maintenance: "A small Orc in a hard hat holding a tuning fork and a wrench",
};

export default function OrcIllustration({
  variant,
  className,
}: {
  variant: OrcVariant;
  className?: string;
}) {
  const v = variant;
  return (
    <svg viewBox="0 0 240 270" role="img" aria-label={LABELS[v]} className={className} style={{ height: "auto", width: "100%" }}>
      <defs>
        <symbol id="orc-note" viewBox="-10 -32 34 44">
          <ellipse cx="0" cy="0" rx="8" ry="6" transform="rotate(-20)" fill="currentColor" />
          <rect x="5" y="-28" width="3.5" height="28" fill="currentColor" />
          <path d="M8.5,-28 Q20,-22 18,-9 Q15,-18 8.5,-19 Z" fill="currentColor" />
        </symbol>
      </defs>

      <circle cx="120" cy="150" r="112" fill="var(--ifm-color-emphasis-100)" />
      <ellipse cx="120" cy="258" rx="74" ry="8" fill="#000" opacity="0.3" />

      {/* Behind the Orc */}
      {v === "not-found" && (
        <g>
          <use href="#orc-note" x="14" y="22" width="30" height="40" style={{ color: "#e0b040" }} />
          <use href="#orc-note" x="196" y="40" width="24" height="32" style={{ color: "#e0b040" }} />
        </g>
      )}
      {v === "server-error" && (
        <g>
          <use href="#orc-note" x="12" y="150" width="26" height="34" style={{ color: "#78716c" }} />
          <use href="#orc-note" x="204" y="30" width="22" height="30" style={{ color: "#78716c" }} />
          <circle cx="60" cy="44" r="11" fill="#57534e" />
          <circle cx="44" cy="28" r="7" fill="#57534e" />
          <circle cx="76" cy="26" r="8" fill="#57534e" />
        </g>
      )}
      {v === "forbidden" && (
        <g>
          <rect x="14" y="172" width="8" height="82" fill="#e0b040" />
          <rect x="218" y="172" width="8" height="82" fill="#e0b040" />
          <ellipse cx="18" cy="254" rx="13" ry="4" fill="#e0b040" />
          <ellipse cx="222" cy="254" rx="13" ry="4" fill="#e0b040" />
          <circle cx="18" cy="170" r="9" fill="#e0b040" />
          <circle cx="222" cy="170" r="9" fill="#e0b040" />
          <path d="M22,176 Q120,236 218,176" stroke="#8b0000" strokeWidth="7" strokeLinecap="round" fill="none" />
        </g>
      )}
      {v === "empty" && (
        <g>
          <use href="#orc-note" x="16" y="40" width="26" height="34" style={{ color: "#78716c" }} opacity="0.35" />
          <use href="#orc-note" x="200" y="62" width="20" height="28" style={{ color: "#78716c" }} opacity="0.25" />
          <circle cx="40" cy="190" r="3" fill="#78716c" opacity="0.4" />
          <circle cx="206" cy="150" r="2.5" fill="#78716c" opacity="0.4" />
        </g>
      )}
      {v === "maintenance" && (
        <g>
          <use href="#orc-note" x="196" y="38" width="24" height="32" style={{ color: "#e0b040" }} />
          <path d="M36,124 Q29,133 36,142" stroke="#e0b040" strokeWidth="3" strokeLinecap="round" fill="none" />
          <path d="M26,118 Q15,133 26,148" stroke="#e0b040" strokeWidth="3" strokeLinecap="round" fill="none" opacity="0.6" />
        </g>
      )}

      {/* Legs, feet, tunic, belt */}
      <rect x="95" y="222" width="22" height="30" rx="10" fill="#5b7c32" />
      <rect x="125" y="222" width="22" height="30" rx="10" fill="#5b7c32" />
      <ellipse cx="104" cy="253" rx="16" ry="8" fill="#0c0a09" />
      <ellipse cx="138" cy="253" rx="16" ry="8" fill="#0c0a09" />
      <rect x="80" y="164" width="80" height="70" rx="32" fill="#8b0000" />
      <rect x="80" y="204" width="80" height="9" fill="#e0b040" />
      <rect x="113" y="202" width="14" height="13" rx="2" fill="#f5f5f4" />

      {/* Arm that sits under the head */}
      {v === "not-found" && (
        <path d="M156,188 Q172,196 176,198" stroke="#759346" strokeWidth="20" strokeLinecap="round" fill="none" />
      )}

      {/* Ears, mohawk, and the record head */}
      <path d="M60,104 L8,66 L66,138 Z" fill="#5b7c32" />
      <path d="M180,104 L232,66 L174,138 Z" fill="#5b7c32" />
      {v === "server-error" ? (
        <path d="M90,62 L92,30 L108,54 L116,20 L130,54 L146,28 L150,62 Z" fill="#e0b040" />
      ) : (
        v !== "maintenance" && (
          <path d="M92,60 L98,32 L110,54 L120,24 L130,54 L142,32 L148,60 Z" fill="#e0b040" />
        )
      )}
      <circle cx="120" cy="112" r="68" fill="#759346" />
      <circle cx="120" cy="112" r="59" fill="none" stroke="#5b7c32" strokeWidth="3" opacity="0.45" />
      <circle cx="120" cy="112" r="49" fill="none" stroke="#5b7c32" strokeWidth="3" opacity="0.45" />
      <circle cx="120" cy="112" r="40" fill="#5b7c32" />
      <ellipse cx="74" cy={v === "not-found" ? 128 : 130} rx="10" ry="6" fill="#e07a5f" opacity="0.35" />
      <ellipse cx="166" cy={v === "not-found" ? 128 : 130} rx="10" ry="6" fill="#e07a5f" opacity="0.35" />

      {/* Faces */}
      {v === "not-found" && (
        <g>
          <path d="M82,88 Q94,80 106,87" stroke="#1c1917" strokeWidth="4" strokeLinecap="round" fill="none" />
          <path d="M134,82 Q146,74 158,81" stroke="#1c1917" strokeWidth="4" strokeLinecap="round" fill="none" />
          <ellipse cx="94" cy="104" rx="10" ry="12" fill="#f5f5f4" />
          <ellipse cx="146" cy="104" rx="10" ry="12" fill="#f5f5f4" />
          <circle cx="98" cy="101" r="5" fill="#1c1917" />
          <circle cx="150" cy="101" r="5" fill="#1c1917" />
          <ellipse cx="120" cy="122" rx="7" ry="5" fill="#759346" />
          <path d="M100,148 Q110,141 120,148 T140,148" stroke="#1c1917" strokeWidth="4" strokeLinecap="round" fill="none" />
          <path d="M102,148 L105,134 L111,150 Z" fill="#f5f5f4" />
          <path d="M138,148 L135,134 L129,150 Z" fill="#f5f5f4" />
        </g>
      )}
      {v === "server-error" && (
        <g>
          <path d="M84,94 L104,105 L84,116" stroke="#1c1917" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
          <path d="M156,94 L136,105 L156,116" stroke="#1c1917" strokeWidth="5" strokeLinecap="round" strokeLinejoin="round" fill="none" />
          <ellipse cx="120" cy="122" rx="7" ry="5" fill="#759346" />
          <ellipse cx="120" cy="148" rx="15" ry="11" fill="#3b0d0d" />
          <ellipse cx="120" cy="154" rx="8" ry="4" fill="#c2410c" />
          <path d="M103,146 L106,131 L112,148 Z" fill="#f5f5f4" />
          <path d="M137,146 L134,131 L128,148 Z" fill="#f5f5f4" />
          <path d="M180,66 Q173,79 180,86 Q187,79 180,66 Z" fill="#93c5fd" />
        </g>
      )}
      {v === "forbidden" && (
        <g>
          <path d="M72,100 L168,100" stroke="#0c0a09" strokeWidth="5" strokeLinecap="round" />
          <rect x="80" y="92" width="38" height="26" rx="11" fill="#0c0a09" />
          <rect x="122" y="92" width="38" height="26" rx="11" fill="#0c0a09" />
          <path d="M88,99 L98,99" stroke="#f5f5f4" strokeWidth="3" strokeLinecap="round" opacity="0.6" />
          <path d="M130,99 L140,99" stroke="#f5f5f4" strokeWidth="3" strokeLinecap="round" opacity="0.6" />
          <ellipse cx="120" cy="125" rx="7" ry="5" fill="#759346" />
          <path d="M102,148 L138,148" stroke="#1c1917" strokeWidth="4" strokeLinecap="round" />
          <path d="M103,148 L106,134 L112,148 Z" fill="#f5f5f4" />
          <path d="M137,148 L134,131 L128,148 Z" fill="#f5f5f4" />
        </g>
      )}
      {v === "empty" && (
        <g>
          <path d="M84,90 Q95,84 106,90" stroke="#1c1917" strokeWidth="4" strokeLinecap="round" fill="none" />
          <path d="M134,84 Q145,76 156,83" stroke="#1c1917" strokeWidth="4" strokeLinecap="round" fill="none" />
          <ellipse cx="95" cy="106" rx="10" ry="12" fill="#f5f5f4" />
          <ellipse cx="145" cy="106" rx="10" ry="12" fill="#f5f5f4" />
          <circle cx="99" cy="108" r="5" fill="#1c1917" />
          <circle cx="149" cy="108" r="5" fill="#1c1917" />
          <ellipse cx="120" cy="124" rx="7" ry="5" fill="#759346" />
          <path d="M104,148 Q112,152 120,148 T136,148" stroke="#1c1917" strokeWidth="4" strokeLinecap="round" fill="none" />
          <path d="M104,148 L107,134 L113,150 Z" fill="#f5f5f4" />
          <path d="M136,148 L133,134 L127,150 Z" fill="#f5f5f4" />
        </g>
      )}
      {v === "maintenance" && (
        <g>
          <path d="M84,90 Q95,84 106,90" stroke="#1c1917" strokeWidth="4" strokeLinecap="round" fill="none" />
          <path d="M134,90 Q145,84 156,90" stroke="#1c1917" strokeWidth="4" strokeLinecap="round" fill="none" />
          <ellipse cx="95" cy="106" rx="10" ry="12" fill="#f5f5f4" />
          <ellipse cx="145" cy="106" rx="10" ry="12" fill="#f5f5f4" />
          <circle cx="92" cy="108" r="5" fill="#1c1917" />
          <circle cx="142" cy="108" r="5" fill="#1c1917" />
          <ellipse cx="120" cy="124" rx="7" ry="5" fill="#759346" />
          <path d="M100,144 Q120,160 140,144" stroke="#1c1917" strokeWidth="4" strokeLinecap="round" fill="none" />
          <path d="M102,146 L105,132 L111,150 Z" fill="#f5f5f4" />
          <path d="M138,146 L135,132 L129,150 Z" fill="#f5f5f4" />
        </g>
      )}

      {/* In front of the Orc: arms, props, headgear */}
      {v === "not-found" && (
        <g>
          <path d="M84,188 Q44,182 58,134" stroke="#759346" strokeWidth="20" strokeLinecap="round" fill="none" />
          <circle cx="60" cy="126" r="12" fill="#759346" />
          <g transform="rotate(10 196 176)">
            <rect x="164" y="144" width="64" height="64" rx="5" fill="#f5f5f4" />
            <circle cx="196" cy="176" r="22" fill="none" stroke="#a8a29e" strokeWidth="2" strokeDasharray="4 4" />
            <text x="196" y="188" textAnchor="middle" fontFamily="system-ui, sans-serif" fontSize="34" fontWeight="700" fill="#8b0000">?</text>
          </g>
          <circle cx="172" cy="200" r="12" fill="#759346" />
        </g>
      )}
      {v === "server-error" && (
        <g>
          <path d="M84,190 Q40,176 54,134" stroke="#759346" strokeWidth="20" strokeLinecap="round" fill="none" />
          <circle cx="56" cy="126" r="12" fill="#759346" />
          <g transform="rotate(-25 170 200)">
            <rect x="164" y="140" width="12" height="76" fill="#6b4423" />
            <rect x="161" y="126" width="18" height="16" rx="3" fill="#3b2412" />
            <ellipse cx="170" cy="216" rx="33" ry="27" fill="#b8793a" />
            <circle cx="170" cy="216" r="9" fill="#3b2412" />
            <path d="M170,142 Q190,160 177,178 Q166,190 184,202" stroke="#e7e5e4" strokeWidth="1.8" strokeLinecap="round" fill="none" />
          </g>
          <path d="M158,190 Q172,194 176,200" stroke="#759346" strokeWidth="20" strokeLinecap="round" fill="none" />
          <circle cx="176" cy="202" r="12" fill="#759346" />
        </g>
      )}
      {v === "forbidden" && (
        <g>
          <path d="M86,182 Q118,190 148,200" stroke="#759346" strokeWidth="20" strokeLinecap="round" fill="none" />
          <path d="M156,182 Q124,190 94,200" stroke="#759346" strokeWidth="20" strokeLinecap="round" fill="none" />
          <circle cx="150" cy="200" r="12" fill="#759346" />
          <circle cx="92" cy="200" r="12" fill="#759346" />
        </g>
      )}
      {v === "empty" && (
        <g>
          <path d="M72,200 L52,190 L64,180 L84,190 Z" fill="#a06a30" />
          <path d="M168,200 L188,190 L176,180 L156,190 Z" fill="#a06a30" />
          <path d="M72,200 L84,188 L156,188 L168,200 Z" fill="#3b2412" />
          <rect x="72" y="200" width="96" height="46" rx="4" fill="#b8793a" />
          <rect x="104" y="200" width="32" height="9" fill="#e7e5e4" opacity="0.85" />
          <path d="M84,214 L156,214 M84,228 L156,228" stroke="#a06a30" strokeWidth="2" />
          <circle cx="112" cy="190" r="4" fill="#d6d3d1" opacity="0.7" />
          <circle cx="122" cy="184" r="3" fill="#d6d3d1" opacity="0.5" />
          <circle cx="130" cy="191" r="2.5" fill="#d6d3d1" opacity="0.6" />
          <path d="M84,186 Q60,198 70,220" stroke="#759346" strokeWidth="20" strokeLinecap="round" fill="none" />
          <path d="M156,186 Q180,198 170,220" stroke="#759346" strokeWidth="20" strokeLinecap="round" fill="none" />
          <circle cx="72" cy="222" r="12" fill="#759346" />
          <circle cx="168" cy="222" r="12" fill="#759346" />
        </g>
      )}
      {v === "maintenance" && (
        <g>
          <path d="M66,80 Q120,6 174,80 Z" fill="#e0b040" />
          <rect x="56" y="76" width="128" height="11" rx="5.5" fill="#ca9a2c" />
          <rect x="113" y="32" width="14" height="46" fill="#ca9a2c" />
          <path d="M47,146 L47,112 M57,146 L57,112 M47,146 Q52,154 57,146" stroke="#d6d3d1" strokeWidth="4" strokeLinecap="round" fill="none" />
          <path d="M84,188 Q48,192 52,160" stroke="#759346" strokeWidth="20" strokeLinecap="round" fill="none" />
          <circle cx="52" cy="156" r="12" fill="#759346" />
          <g transform="translate(196 186) rotate(25)">
            <rect x="-5" y="-34" width="10" height="76" rx="5" fill="#a8a29e" />
            <circle cx="0" cy="-40" r="15" fill="#a8a29e" />
            <rect x="-5.5" y="-60" width="11" height="16" fill="var(--ifm-color-emphasis-100)" />
          </g>
          <path d="M156,188 Q176,196 190,194" stroke="#759346" strokeWidth="20" strokeLinecap="round" fill="none" />
          <circle cx="192" cy="194" r="12" fill="#759346" />
        </g>
      )}
    </svg>
  );
}
