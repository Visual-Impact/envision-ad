"use client";

import dynamic from "next/dynamic";

// Leaflet touches `window` as soon as its module loads, so PinDropMap must never be evaluated
// during server rendering. Exporting it lazily from the shared/ui public API keeps every
// importer of this barrel (MetricCard, BackButton, ConfirmationModal consumers) SSR-safe.
export const PinDropMap = dynamic(() => import("./PinDropMap").then((m) => m.PinDropMap), { ssr: false });
