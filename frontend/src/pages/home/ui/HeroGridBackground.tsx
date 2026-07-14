"use client";

import { useEffect, useRef } from "react";
import classes from "./HeroGridBackground.module.css";

/* --- Tuning knobs (dial the look in here) ------------------------------- */
const CELL_SIZE = 64; // cell pitch in CSS px
const GUTTER = 2; // keep the grey outline visible around a lit cell
const COLORS = ["#00BFFF", "#A855F7"]; // platform gradient: blue -> purple
const PALETTE_STEPS = 20; // pre-quantised gradient (no per-frame allocs)
const PARTICIPATION = 0.34; // only this share of cells can ever be fireflies
const POP_GATE = 0.9; // a cell's pulse must exceed this to light (sparse)
const MAX_POP_ALPHA = 0.5; // peak brightness of an ambient firefly pop
const POINTER_POP_ALPHA = 0.42; // brightness of the cell under the pointer
const POINTER_RISE = 0.045; // slow ease-in of the hovered cell
const POINTER_DECAY = 0.95; // slow ease-out / trailing fade
const SPEED = 0.0038; // pulse phase advance per frame (very slow fade in/out)
const FPS_CAP = 40; // frame-cap to save battery
const MAX_DPR = 2;
const KEY_STRIDE = 100000; // pack (row, col) into one numeric map key
const TAU = Math.PI * 2;
/* ----------------------------------------------------------------------- */

interface HeroGridBackgroundProps {
    cellSize?: number;
    colors?: string[];
    speed?: number;
    className?: string;
}

/**
 * Deterministic 3D integer hash -> [0,1). Two finalizer rounds so that the same
 * (x, y) with different z seeds decorrelate fully (used for independent per-cell
 * participation / phase / rate / colour draws). No allocations.
 */
function hash3(x: number, y: number, z: number): number {
    let n = (x | 0) * 374761393 + (y | 0) * 668265263 + (z | 0) * 1274126177;
    n = Math.imul(n ^ (n >>> 15), 2246822519);
    n = Math.imul(n ^ (n >>> 13), 3266489917);
    n = n ^ (n >>> 16);
    return (n >>> 0) / 4294967295;
}

/** Pre-build an rgb() string palette spanning the gradient stops. */
function buildPalette(colors: string[], steps: number): string[] {
    const stops = colors.map((hex) => {
        const h = hex.replace("#", "");
        return [parseInt(h.slice(0, 2), 16), parseInt(h.slice(2, 4), 16), parseInt(h.slice(4, 6), 16)];
    });
    const palette: string[] = [];
    const segs = stops.length - 1;
    for (let i = 0; i < steps; i++) {
        const u = (i / (steps - 1)) * segs;
        const seg = Math.min(Math.floor(u), segs - 1);
        const f = u - seg;
        const a = stops[seg];
        const b = stops[seg + 1];
        palette.push(
            `rgb(${Math.round(a[0] + (b[0] - a[0]) * f)},${Math.round(a[1] + (b[1] - a[1]) * f)},${Math.round(a[2] + (b[2] - a[2]) * f)})`,
        );
    }
    return palette;
}

/**
 * Ambient grid backdrop for the hero. A grid of white cells with a faint
 * light-grey outline (matching the site background) over which a few blue/purple
 * "fireflies" slowly and sporadically light up and dim — each participating cell
 * pulses on its own random phase + rate. The cell under the pointer eases in
 * gently. Purely decorative — never intercepts clicks.
 *
 * Fallbacks: SSR/no-JS shows the CSS grid pattern; prefers-reduced-motion
 * renders a single static frame; the rAF loop pauses off-screen / tab-hidden.
 */
export function HeroGridBackground({
    cellSize = CELL_SIZE,
    colors = COLORS,
    speed = SPEED,
    className,
}: HeroGridBackgroundProps) {
    const rootRef = useRef<HTMLDivElement | null>(null);
    const canvasRef = useRef<HTMLCanvasElement | null>(null);

    useEffect(() => {
        const root = rootRef.current;
        const canvas = canvasRef.current;
        if (!root || !canvas) return;
        const ctx = canvas.getContext("2d");
        if (!ctx) return;

        const reduceMotionMq = window.matchMedia("(prefers-reduced-motion: reduce)");
        const canHover = window.matchMedia("(hover: hover)").matches;
        const frameInterval = 1000 / FPS_CAP;
        const palette = buildPalette(colors, PALETTE_STEPS);

        // Mutable state (reused across frames — no per-frame allocation).
        let width = 0;
        let height = 0;
        let cols = 0;
        let rows = 0;
        let t = 0;
        let rafId = 0;
        let lastTime = 0;
        let onScreen = true;
        let tabVisible = !document.hidden;
        let running = false;
        // Cells under / recently under the pointer -> eased intensity (small map).
        const hover = new Map<number, number>();
        let pointerKey = -1;

        const measure = () => {
            const rect = root.getBoundingClientRect();
            width = rect.width;
            height = rect.height;
            if (width === 0 || height === 0) return;
            const dpr = Math.min(window.devicePixelRatio || 1, MAX_DPR);
            canvas.width = Math.round(width * dpr);
            canvas.height = Math.round(height * dpr);
            ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
            cols = Math.ceil(width / cellSize) + 1;
            rows = Math.ceil(height / cellSize) + 1;
        };

        const draw = () => {
            if (width === 0 || height === 0) return;
            ctx.clearRect(0, 0, width, height); // stay transparent -> CSS grid shows

            // Ease the hovered cell up; fade everything else down gently.
            if (pointerKey >= 0) {
                const cur = hover.get(pointerKey) ?? 0;
                hover.set(pointerKey, cur + (1 - cur) * POINTER_RISE);
            }
            if (hover.size) {
                for (const [k, v] of hover) {
                    if (k === pointerKey) continue;
                    const nv = v * POINTER_DECAY;
                    if (nv < 0.02) hover.delete(k);
                    else hover.set(k, nv);
                }
            }

            const size = cellSize - GUTTER;
            const gateSpan = 1 - POP_GATE;

            for (let r = 0; r < rows; r++) {
                for (let c = 0; c < cols; c++) {
                    let b = 0;

                    // Ambient firefly: only a subset of cells participate, and each
                    // pulses on its own slow phase/rate — so few are lit at any time.
                    if (hash3(c, r, 101) < PARTICIPATION) {
                        const phase = hash3(c, r, 11) * TAU;
                        const rate = 0.4 + hash3(c, r, 29) * 1.1;
                        const s = Math.sin(t * rate + phase);
                        if (s > POP_GATE) {
                            const ramp = (s - POP_GATE) / gateSpan; // 0..1 near the peak
                            b = ramp * ramp * ramp * MAX_POP_ALPHA; // cube -> only peaks read
                        }
                    }

                    // Pointer pop: the eased cell under the cursor (+ short trail).
                    if (hover.size) {
                        const hv = hover.get(r * KEY_STRIDE + c);
                        if (hv) b += hv * POINTER_POP_ALPHA;
                    }

                    if (b <= 0.01) continue;

                    const idx = Math.min(PALETTE_STEPS - 1, (hash3(c, r, 777) * PALETTE_STEPS) | 0);
                    ctx.globalAlpha = b > 0.62 ? 0.62 : b;
                    ctx.fillStyle = palette[idx];
                    ctx.fillRect(c * cellSize + GUTTER / 2, r * cellSize + GUTTER / 2, size, size);
                }
            }
            ctx.globalAlpha = 1;
        };

        const frame = (now: number) => {
            rafId = requestAnimationFrame(frame);
            const elapsed = now - lastTime;
            if (elapsed < frameInterval) return;
            lastTime = now - (elapsed % frameInterval);
            t += speed;
            draw();
        };

        const start = () => {
            if (running || reduceMotionMq.matches) return;
            if (!onScreen || !tabVisible) return;
            running = true;
            lastTime = performance.now();
            rafId = requestAnimationFrame(frame);
        };

        const stop = () => {
            running = false;
            if (rafId) cancelAnimationFrame(rafId);
            rafId = 0;
        };

        // --- Resize (ResizeObserver catches content-driven hero height too) ---
        let resizeTimer = 0;
        const ro = new ResizeObserver(() => {
            window.clearTimeout(resizeTimer);
            resizeTimer = window.setTimeout(() => {
                measure();
                if (!running) draw();
            }, 120);
        });
        ro.observe(root);

        // --- Pointer pop: light only the cell under the cursor ---
        const onPointerMove = (e: PointerEvent) => {
            const rect = root.getBoundingClientRect();
            const px = e.clientX - rect.left;
            const py = e.clientY - rect.top;
            if (px < 0 || py < 0 || px > width || py > height) {
                pointerKey = -1;
                return;
            }
            pointerKey = Math.floor(py / cellSize) * KEY_STRIDE + Math.floor(px / cellSize);
        };
        const onPointerLeave = () => {
            pointerKey = -1;
        };
        const hoverTarget = root.parentElement;
        const pointerEnabled = canHover && !reduceMotionMq.matches && hoverTarget;
        if (pointerEnabled) {
            hoverTarget.addEventListener("pointermove", onPointerMove);
            hoverTarget.addEventListener("pointerleave", onPointerLeave);
        }

        // --- Pause when off-screen / tab hidden ---
        const io = new IntersectionObserver(
            (entries) => {
                onScreen = entries[0]?.isIntersecting ?? true;
                if (onScreen) start();
                else stop();
            },
            { threshold: 0 },
        );
        io.observe(root);

        const onVisibility = () => {
            tabVisible = !document.hidden;
            if (tabVisible) start();
            else stop();
        };
        document.addEventListener("visibilitychange", onVisibility);

        // --- Reduced-motion: static single frame, react to the toggle ---
        const onReduceMotionChange = () => {
            if (reduceMotionMq.matches) {
                stop();
                measure();
                draw();
            } else {
                start();
            }
        };
        reduceMotionMq.addEventListener("change", onReduceMotionChange);

        // --- Initial paint ---
        measure();
        draw();
        start();

        return () => {
            stop();
            window.clearTimeout(resizeTimer);
            ro.disconnect();
            io.disconnect();
            document.removeEventListener("visibilitychange", onVisibility);
            reduceMotionMq.removeEventListener("change", onReduceMotionChange);
            if (pointerEnabled) {
                hoverTarget.removeEventListener("pointermove", onPointerMove);
                hoverTarget.removeEventListener("pointerleave", onPointerLeave);
            }
        };
    }, [cellSize, colors, speed]);

    return (
        <div
            ref={rootRef}
            className={`${classes.root} ${className ?? ""}`}
            aria-hidden="true"
            role="presentation"
        >
            <canvas ref={canvasRef} className={classes.canvas} />
        </div>
    );
}
