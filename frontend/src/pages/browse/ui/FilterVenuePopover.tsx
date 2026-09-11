"use client";

import { Badge, Button, Group, Popover, PopoverDropdown, PopoverTarget, Stack, Text } from "@mantine/core";
import { IconChevronDown } from "@tabler/icons-react";
import { useLocale, useTranslations } from "next-intl";
import { Dispatch, SetStateAction, useEffect, useState } from "react";
import { Venue } from "@/entities/venue";
import { getAllVenues } from "@/features/venue-management";

interface FilterVenuePopoverProps {
    id?: string;
    selectedVenueIds: string[];
    setSelectedVenueIds: Dispatch<SetStateAction<string[]>>;
}

export function FilterVenuePopover({ id, selectedVenueIds, setSelectedVenueIds }: FilterVenuePopoverProps) {
    const t = useTranslations("browse.browseactions.filters");
    const locale = useLocale();
    const [opened, setOpened] = useState(false);
    const [venues, setVenues] = useState<Venue[]>([]);
    const [draft, setDraft] = useState<string[]>(selectedVenueIds);

    useEffect(() => {
        getAllVenues(locale).then(setVenues).catch(() => setVenues([]));
    }, [locale]);


    function toggleDraft(venueId: string) {
        setDraft(prev =>
            prev.includes(venueId) ? prev.filter(id => id !== venueId) : [...prev, venueId]
        );
    }

    function handleApply() {
        setSelectedVenueIds(draft);
        setOpened(false);
    }

    function handleReset() {
        setDraft([]);
        setSelectedVenueIds([]);
        setOpened(false);
    }

    if (venues.length === 0) return null;

    const isActive = selectedVenueIds.length > 0;

    function getButtonLabel() {
        if (!isActive) return t("venue");
        const names = selectedVenueIds.map(id => {
            const v = venues.find(v => v.venueId === id);
            return v ? (locale === "fr" ? v.nameFr : v.nameEn) : null;
        }).filter(Boolean) as string[];
        if (names.length === 1) return names[0];
        if (names.length === 2) return names.join(", ");
        return `${names[0]}, ${names[1]} +${names.length - 2}`;
    }

    return (
        <Popover id={id} opened={opened} onChange={setOpened} trapFocus position="bottom" withArrow shadow="md" keepMounted>
            <PopoverTarget>
                <Button onClick={() => { if (!opened) setDraft(selectedVenueIds); setOpened(o => !o); }} variant={isActive ? "light" : "white"} color={isActive ? "blue" : undefined} rightSection={<IconChevronDown />} size="xs">
                    {getButtonLabel()}
                </Button>
            </PopoverTarget>
            <PopoverDropdown>
                <Stack gap="xs">
                    <Text size="sm" fw={500}>{t("venue")}</Text>
                    <Group gap="xs" wrap="wrap" maw={280}>
                        {venues.map(venue => {
                            const isSelected = draft.includes(venue.venueId);
                            const name = locale === "fr" ? venue.nameFr : venue.nameEn;
                            return (
                                <Badge
                                    key={venue.venueId}
                                    color={venue.colorCode}
                                    variant={isSelected ? "filled" : "light"}
                                    size="lg"
                                    style={{ cursor: "pointer" }}
                                    onClick={() => toggleDraft(venue.venueId)}
                                >
                                    {name}
                                </Badge>
                            );
                        })}
                    </Group>
                </Stack>
                <Group justify={isActive ? "space-between" : "flex-end"} mt="md">
                    {isActive && (
                        <Button size="xs" variant="subtle" color="red" onClick={handleReset}>
                            {t("clear")}
                        </Button>
                    )}
                    <Button size="xs" onClick={handleApply}>
                        {t("showresults")}
                    </Button>
                </Group>
            </PopoverDropdown>
        </Popover>
    );
}
