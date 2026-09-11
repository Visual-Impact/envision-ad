"use client";

import { Badge, Group, Text, Loader } from "@mantine/core";
import { useTranslations, useLocale } from "next-intl";
import { useEffect, useState } from "react";
import { Venue } from "@/entities/venue";
import { getAllVenues } from "@/features/venue-management";

interface VenuePillSelectorProps {
    selectedVenueId: string | null;
    onSelect: (venueId: string | null) => void;
}

export function VenuePillSelector({ selectedVenueId, onSelect }: VenuePillSelectorProps) {
    const t = useTranslations("mediaModal");
    const locale = useLocale();
    const [venues, setVenues] = useState<Venue[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let cancelled = false;
        (async () => {
            setLoading(true);
            try {
                const data = await getAllVenues(locale);
                if (!cancelled) setVenues(data);
            } catch {
                if (!cancelled) setVenues([]);
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [locale]);

    if (loading) return <Loader size="xs" />;
    if (venues.length === 0) return null;

    return (
        <div>
            <Text size="sm" fw={500} mb={6}>
                {t("labels.venueType")}
            </Text>
            <Group gap="xs" wrap="wrap">
                {venues.map((venue) => {
                    const isSelected = selectedVenueId === venue.venueId;
                    const name = locale === "fr" ? venue.nameFr : venue.nameEn;
                    return (
                        <Badge
                            key={venue.venueId}
                            color={venue.colorCode}
                            variant={isSelected ? "filled" : "light"}
                            size="lg"
                            style={{ cursor: "pointer" }}
                            onClick={() => onSelect(isSelected ? null : venue.venueId)}
                        >
                            {name}
                        </Badge>
                    );
                })}
            </Group>
        </div>
    );
}
