"use client";

import { useEffect, useState } from "react";
import { getPendingMedia } from "@/features/media-management";
import type { Media } from "@/entities/media";

export function useAdminPendingMedia() {
    const [media, setMedia] = useState<Media[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        getPendingMedia()
            .then((data) => setMedia((data ?? []).filter((m): m is Media => !!m?.id)))
            .catch((e) => setError(e instanceof Error ? e.message : "Failed to load media"))
            .finally(() => setLoading(false));
    }, []);

    return { media, loading, error };
}
