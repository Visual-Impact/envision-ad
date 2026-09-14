"use client";

import { useEffect, useState } from "react";

/**
 * Seconds left until `until` (epoch ms), re-rendering once a second while a cooldown runs.
 * Returns 0 when there is no cooldown or it has passed.
 */
export function useCooldownSeconds(until: number | null): number {
    const [now, setNow] = useState(() => Date.now());

    useEffect(() => {
        if (until === null) return;
        const id = window.setInterval(() => setNow(Date.now()), 1000);
        return () => window.clearInterval(id);
    }, [until]);

    if (until === null) return 0;
    return Math.max(0, Math.ceil((until - now) / 1000));
}
