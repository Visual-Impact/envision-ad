"use client";

import { useEffect, useMemo, useRef } from "react";
import { useTranslations } from "next-intl";
import classes from "./HeroScreens.module.css";

interface DeviceLabels {
    stand: string;
    suspended: string;
    kiosk: string;
    cta: string;
}

/** Escape values interpolated into the raw SVG markup. */
function esc(value: string): string {
    return value.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

/**
 * The three DOOH ad screens (STAND / SUSPENDU / KIOSQUE) as one illustration.
 * Kept as raw markup so the artwork (SMIL glow pulses, gradients, filters) stays
 * pixel-faithful; only the language-dependent labels are injected. Style rules
 * are scoped under `#heroDevices` so they can't leak to other SVGs on the page.
 */
function buildDevicesSvg({ stand, suspended, kiosk, cta }: DeviceLabels): string {
    return `<svg id="heroDevices" viewBox="0 0 520 310" xmlns="http://www.w3.org/2000/svg" style="display:block;width:100%;height:auto;overflow:visible">
  <defs>
    <linearGradient id="bodyG" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%" stop-color="#14161d"/><stop offset="45%" stop-color="#22252f"/><stop offset="100%" stop-color="#0f1015"/>
    </linearGradient>
    <linearGradient id="baseG" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#1f222a"/><stop offset="100%" stop-color="#090a0d"/>
    </linearGradient>
    <linearGradient id="poleG" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%" stop-color="#788296"/><stop offset="40%" stop-color="#b8c0d2"/><stop offset="100%" stop-color="#646d80"/>
    </linearGradient>
    <linearGradient id="ceilG" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#a8b2c6"/><stop offset="100%" stop-color="#70788c"/>
    </linearGradient>
    <linearGradient id="bgBlue" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#020d1a"/><stop offset="100%" stop-color="#051424"/>
    </linearGradient>
    <linearGradient id="bgPurp" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#080312"/><stop offset="100%" stop-color="#10041f"/>
    </linearGradient>
    <linearGradient id="bgTeal" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0%" stop-color="#010a0e"/><stop offset="100%" stop-color="#03131a"/>
    </linearGradient>
    <linearGradient id="btnBlue" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%" stop-color="#0088ff"/><stop offset="100%" stop-color="#0055cc"/>
    </linearGradient>
    <linearGradient id="btnPurp" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%" stop-color="#b830e8"/><stop offset="100%" stop-color="#7a10bc"/>
    </linearGradient>
    <linearGradient id="btnTeal" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0%" stop-color="#00b4ed"/><stop offset="100%" stop-color="#0080b3"/>
    </linearGradient>
    <radialGradient id="groundG" cx="50%" cy="0%" r="70%">
      <stop offset="0%" stop-color="#0088ff" stop-opacity="0.12"/><stop offset="100%" stop-color="#0088ff" stop-opacity="0"/>
    </radialGradient>
    <filter id="glowB" x="-20%" y="-20%" width="140%" height="140%">
      <feGaussianBlur stdDeviation="4" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>
    <filter id="glowP" x="-20%" y="-20%" width="140%" height="140%">
      <feGaussianBlur stdDeviation="5" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>
    <filter id="glowT" x="-20%" y="-20%" width="140%" height="140%">
      <feGaussianBlur stdDeviation="4" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>
    <filter id="glowS" x="-20%" y="-20%" width="140%" height="140%">
      <feGaussianBlur stdDeviation="3" result="b"/><feMerge><feMergeNode in="b"/><feMergeNode in="SourceGraphic"/></feMerge>
    </filter>
    <clipPath id="clipL"><rect x="5" y="5" width="82" height="138" rx="6"/></clipPath>
    <clipPath id="clipC"><rect x="5" y="5" width="90" height="150" rx="6"/></clipPath>
    <clipPath id="clipR"><rect x="6" y="6" width="100" height="158" rx="6"/></clipPath>
  </defs>

  <style>
    #heroDevices text {
      font-family: var(--font-lato), 'Inter', system-ui, -apple-system, sans-serif;
      -webkit-font-smoothing: antialiased;
    }
    #heroDevices .badge-txt { font-weight: 700; letter-spacing: 0.5px; }
    #heroDevices .cta-txt { font-weight: 700; letter-spacing: 0.2px; }
  </style>

  <ellipse cx="260" cy="281" rx="240" ry="12" fill="url(#groundG)"/>

  <g transform="translate(22, 0)">
    <g transform="translate(0,28)">
      <rect x="0" y="0" width="92" height="148" rx="10" fill="url(#bodyG)"/>
      <rect x="0" y="0" width="92" height="148" rx="10" fill="none" stroke="#0088ff" stroke-width="2" filter="url(#glowB)">
        <animate attributeName="opacity" values="0.95;0.4;0.95" dur="3.5s" repeatCount="indefinite"/>
      </rect>
      <rect x="5" y="5" width="82" height="138" rx="6" fill="#01050a"/>
      <g clip-path="url(#clipL)">
        <rect x="5" y="5" width="82" height="138" fill="url(#bgBlue)"/>
        <rect x="10" y="12" width="44" height="13" rx="6.5" fill="rgba(0,136,255,0.15)" stroke="#0088ff" stroke-width="0.75"/>
        <text x="32" y="21" text-anchor="middle" font-size="6" fill="#0088ff" class="badge-txt">${esc(stand)}</text>
        <circle cx="76" cy="18" r="3.5" fill="#00e676">
          <animate attributeName="opacity" values="1;0.4;1" dur="3s" repeatCount="indefinite"/>
        </circle>
        <circle cx="46" cy="68" r="22" fill="rgba(0,136,255,0.06)" stroke="#0088ff" stroke-width="1" opacity="0.5"/>
        <polygon points="50,48 38,70 47,70 36,90 58,64 48,64" fill="#0088ff" filter="url(#glowS)"/>
        <g transform="translate(11, 110)">
          <rect x="0" y="0" width="70" height="24" rx="12" fill="url(#btnBlue)" filter="url(#glowS)"/>
          <text x="35" y="12" text-anchor="middle" dominant-baseline="central" font-size="8.5" fill="#ffffff" class="cta-txt">${esc(cta)}</text>
        </g>
      </g>
      <path d="M 5,5 L 45,5 L 5,85 Z" fill="rgba(255,255,255,0.03)"/>
    </g>
    <rect x="37" y="168" width="18" height="10" rx="4" fill="url(#poleG)"/>
    <rect x="41" y="176" width="10" height="91" rx="2" fill="url(#poleG)"/>
    <rect x="18" y="267" width="56" height="10" rx="5" fill="url(#baseG)"/>
    <rect x="12" y="275" width="68" height="6" rx="3" fill="#13151c"/>
  </g>

  <g transform="translate(196, 0)">
    <rect x="20" y="0" width="84" height="8" rx="3" fill="url(#ceilG)"/>
    <line x1="34" y1="8" x2="28" y2="28" stroke="#8a94a6" stroke-width="2"/>
    <line x1="90" y1="8" x2="96" y2="28" stroke="#8a94a6" stroke-width="2"/>
    <g transform="translate(12,28)">
      <rect x="0" y="0" width="100" height="160" rx="10" fill="url(#bodyG)"/>
      <rect x="0" y="0" width="100" height="160" rx="10" fill="none" stroke="#b830e8" stroke-width="2" filter="url(#glowP)">
        <animate attributeName="opacity" values="0.95;0.4;0.95" dur="3.2s" repeatCount="indefinite"/>
      </rect>
      <rect x="5" y="5" width="90" height="150" rx="6" fill="#07020d"/>
      <g clip-path="url(#clipC)">
        <rect x="5" y="5" width="90" height="150" fill="url(#bgPurp)"/>
        <rect x="10" y="12" width="52" height="13" rx="6.5" fill="rgba(184,48,232,0.15)" stroke="#b830e8" stroke-width="0.75"/>
        <text x="36" y="21" text-anchor="middle" font-size="6" fill="#b830e8" class="badge-txt">${esc(suspended)}</text>
        <circle cx="80" cy="18" r="3.5" fill="#00e676">
          <animate attributeName="opacity" values="1;0.4;1" dur="2.5s" repeatCount="indefinite"/>
        </circle>
        <circle cx="50" cy="70" r="24" fill="rgba(184,48,232,0.06)" stroke="#b830e8" stroke-width="1" opacity="0.5"/>
        <circle cx="50" cy="62" r="12" fill="#b830e8" opacity="0.6"/>
        <rect x="38" y="74" width="24" height="20" rx="4" fill="#7a10bc" opacity="0.6"/>
        <circle cx="50" cy="62" r="5" fill="#ffffff" opacity="0.9"/>
        <g transform="translate(11, 120)">
          <rect x="0" y="0" width="78" height="24" rx="12" fill="url(#btnPurp)" filter="url(#glowS)"/>
          <text x="39" y="12" text-anchor="middle" dominant-baseline="central" font-size="8.5" fill="#ffffff" class="cta-txt">${esc(cta)}</text>
        </g>
      </g>
      <path d="M 5,5 L 45,5 L 5,95 Z" fill="rgba(255,255,255,0.03)"/>
    </g>
  </g>

  <g transform="translate(384, 0)">
    <g transform="translate(0,28)">
      <rect x="0" y="0" width="112" height="252" rx="12" fill="url(#bodyG)"/>
      <rect x="6" y="6" width="100" height="158" rx="8" fill="none" stroke="#00b4ed" stroke-width="2" filter="url(#glowT)">
        <animate attributeName="opacity" values="0.95;0.4;0.95" dur="3.8s" repeatCount="indefinite"/>
      </rect>
      <rect x="6" y="6" width="100" height="158" rx="6" fill="#00070a"/>
      <g clip-path="url(#clipR)">
        <rect x="6" y="6" width="100" height="158" fill="url(#bgTeal)"/>
        <rect x="12" y="14" width="46" height="13" rx="6.5" fill="rgba(0,180,237,0.15)" stroke="#00b4ed" stroke-width="0.75"/>
        <text x="35" y="23" text-anchor="middle" font-size="6" fill="#00b4ed" class="badge-txt">${esc(kiosk)}</text>
        <circle cx="88" cy="20" r="3.5" fill="#00e676">
          <animate attributeName="opacity" values="1;0.4;1" dur="2.8s" repeatCount="indefinite"/>
        </circle>
        <circle cx="56" cy="72" r="26" fill="rgba(0,180,237,0.06)" stroke="#00b4ed" stroke-width="1" opacity="0.5"/>
        <circle cx="56" cy="62" r="13" fill="#00b4ed" opacity="0.6"/>
        <rect x="42" y="74" width="28" height="24" rx="5" fill="#0080b3" opacity="0.6"/>
        <circle cx="56" cy="62" r="5.5" fill="#ffffff" opacity="0.9"/>
        <g transform="translate(12, 124)">
          <rect x="0" y="0" width="82" height="26" rx="13" fill="url(#btnTeal)" filter="url(#glowS)"/>
          <text x="41" y="13" text-anchor="middle" dominant-baseline="central" font-size="9" fill="#ffffff" class="cta-txt">${esc(cta)}</text>
        </g>
      </g>
      <path d="M 6,6 L 50,6 L 6,100 Z" fill="rgba(255,255,255,0.03)"/>
      <text x="56" y="214" text-anchor="middle" font-size="7" font-weight="600" fill="rgba(255,255,255,0.22)" letter-spacing="1.8">ENVISION-AD</text>
    </g>
    <rect x="-10" y="268" width="132" height="14" rx="5" fill="url(#baseG)" />
  </g>
</svg>`;
}

/**
 * Right-panel device cluster. Decorative — a single aria-label describes the
 * scene and the SVG is aria-hidden. SMIL animations are paused under
 * prefers-reduced-motion.
 */
export function HeroScreens() {
    const t = useTranslations("homepage.screens");
    const containerRef = useRef<HTMLDivElement | null>(null);

    const markup = useMemo(
        () =>
            buildDevicesSvg({
                stand: t("stand"),
                suspended: t("suspended"),
                kiosk: t("kiosk"),
                cta: t("cta"),
            }),
        [t],
    );

    useEffect(() => {
        const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
        const apply = () => {
            const svg = containerRef.current?.querySelector("svg");
            if (!svg) return;
            if (mq.matches) svg.pauseAnimations();
            else svg.unpauseAnimations();
        };
        apply();
        mq.addEventListener("change", apply);
        return () => mq.removeEventListener("change", apply);
    }, [markup]);

    return (
        <div className={classes.cluster} role="img" aria-label={t("clusterLabel")}>
            <div
                ref={containerRef}
                className={classes.illustration}
                aria-hidden="true"
                dangerouslySetInnerHTML={{ __html: markup }}
            />
        </div>
    );
}
