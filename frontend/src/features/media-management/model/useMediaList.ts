"use client";

import { UseMediaListProps } from "@/entities/media";
import { MediaCardProps } from "@/entities/media";
import { useEffect, useState } from "react";
import { getAllFilteredActiveMedia, SpecialSort } from "../api";


export function useMediaList({
  filteredMediaProps, loadingLocation, setMediaStatus
}: UseMediaListProps) {
    const [medias, setMedias] = useState<MediaCardProps[]>([]);
    const [totalPages, setTotalPages] = useState<number>(1);

    useEffect(() => {
        if (filteredMediaProps.sort === SpecialSort.nearest && loadingLocation) {
            return;
        }

        const controller = new AbortController();

        async function loadMedia() {
            setMediaStatus?.("loading");

            try {
                const data = await getAllFilteredActiveMedia(filteredMediaProps, controller.signal);

                if (controller.signal.aborted) return;

                const items = (data.content || [])
                    .filter((m) => m.id != null)
                    .map((m, index) => ({
                    index: String(index),
                    href: String(m.id),
                    title: m.title,
                    organizationId: m.businessId,
                    organizationName: m.businessName,
                    mediaLocation: m.mediaLocation,
                    resolution: m.resolution,
                    price: m.price ?? 0,
                    dailyImpressions: m.dailyImpressions ?? 0,
                    schedule: m.schedule,
                    typeOfDisplay: m.typeOfDisplay,
                    imageUrl: m.imageUrl,
                    venue: m.venue ?? null
                    }));

                setMedias(items);
                setTotalPages(data.totalPages ?? 1);

                if (items.length > 0) {
                    setMediaStatus?.('success');
                } else {
                    setMediaStatus?.('empty');
                }
                } catch {
                    if (!controller.signal.aborted) {
                        setMediaStatus?.('error');
                    }
                }
        }

        loadMedia();

        return () => {
            controller.abort();
        };
    },[filteredMediaProps, loadingLocation, setMediaStatus]);

    return { medias, totalPages };

}