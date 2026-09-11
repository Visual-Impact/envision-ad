"use client";

import { Badge, Group, Loader, Text } from "@mantine/core";
import { useLocale } from "next-intl";
import { useEffect, useState } from "react";
import { Venue } from "@/entities/venue";
import { getAllVenues } from "../api";

interface VenueMultiSelectPickerProps {
    selectedVenueIds: string[];
    onChange: (venueIds: string[]) => void;
    /** Rendered dimmed under the pills. Callers pass their own namespace's copy so this
     *  component stays free of any single modal's i18n namespace. */
    helperText?: string;
    /** Shown instead of the pills when no venue types exist yet. */
    emptyText?: string;
}

/**
 * Multi-select variant of the media-owner VenuePillSelector. Same badge treatment, but
 * array-valued: selecting is a toggle, and an empty selection is meaningful (it means the
 * creative suits every venue type) rather than "nothing chosen yet".
 */
export function VenueMultiSelectPicker({
    selectedVenueIds,
    onChange,
    helperText,
    emptyText,
}: VenueMultiSelectPickerProps) {
    const locale = useLocale();
    const [venues, setVenues] = useState<Venue[]>([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        let cancelled = false;
        // Nested in an IIFE deliberately: react-hooks/set-state-in-effect flags setState
        // calls in the effect's own top-level statement list, but not one level deeper.
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

    const toggleVenue = (venueId: string) => {
        onChange(
            selectedVenueIds.includes(venueId)
                ? selectedVenueIds.filter((id) => id !== venueId)
                : [...selectedVenueIds, venueId]
        );
    };

    if (loading) return <Loader size="xs" />;

    // Unlike VenuePillSelector, this renders a message rather than null — an advertiser
    // who sees nothing here cannot tell the feature apart from a broken form.
    if (venues.length === 0) {
        return emptyText ? (
            <Text size="xs" c="dimmed">
                {emptyText}
            </Text>
        ) : null;
    }

    return (
        <div>
            <Group gap="xs" wrap="wrap">
                {venues.map((venue) => {
                    const isSelected = selectedVenueIds.includes(venue.venueId);
                    const name = locale === "fr" ? venue.nameFr : venue.nameEn;
                    return (
                        <Badge
                            key={venue.venueId}
                            color={venue.colorCode}
                            variant={isSelected ? "filled" : "light"}
                            size="lg"
                            style={{ cursor: "pointer" }}
                            onClick={() => toggleVenue(venue.venueId)}
                        >
                            {name}
                        </Badge>
                    );
                })}
            </Group>

            {helperText && (
                <Text size="xs" c="dimmed" mt={6}>
                    {helperText}
                </Text>
            )}
        </div>
    );
}
