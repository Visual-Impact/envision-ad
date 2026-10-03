"use client";

import { UseMediaListProps } from "@/entities/media";
import { MediaCardProps, isExcludedForBusinessType } from "@/entities/media";
import { useOrganization } from "@/entities/organization";
import { useEffect, useMemo, useState } from "react";
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

    // P4: flag screens in the signed-in advertiser's own business type, which no bundle they buy
    // will include. Derived on top of the fetch so a change of organization doesn't refetch.
    const { organization } = useOrganization();
    const businessTypeVenueId = organization?.roles?.advertiser ? organization.businessTypeVenueId ?? null : null;
    const flaggedMedias = useMemo(
        () => businessTypeVenueId
            ? medias.map((m) => ({
                ...m,
                unavailableForBusinessType: isExcludedForBusinessType(m.venue?.venueId, businessTypeVenueId),
            }))
            : medias,
        [medias, businessTypeVenueId],
    );

    return { medias: flaggedMedias, totalPages };

}