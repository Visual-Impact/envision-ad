"use client";

import { Button, Group, Modal, Stack, Text } from "@mantine/core";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { Ad } from "@/entities/ad";
import { VenueMultiSelectPicker } from "@/features/venue-management/ui";

interface EditAdVenueTagsModalProps {
    opened: boolean;
    onClose: () => void;
    onSave: (venueIds: string[]) => Promise<void>;
    ad: Ad | null;
}

export function EditAdVenueTagsModal({ opened, onClose, onSave, ad }: EditAdVenueTagsModalProps) {
    const t = useTranslations("editAdVenueTags");
    const tAddAd = useTranslations("AddAdModal");
    const [saving, setSaving] = useState(false);
    const [selectedVenueIds, setSelectedVenueIds] = useState<string[]>(ad?.venueIds ?? []);

    // Re-seed from the ad when a different one is opened, using React's documented
    // "adjust state during render" pattern rather than an effect.
    const [seededAdId, setSeededAdId] = useState<string | null>(ad?.adId ?? null);
    if (ad && ad.adId !== seededAdId) {
        setSeededAdId(ad.adId);
        setSelectedVenueIds(ad.venueIds);
    }

    if (!ad) return null;

    const handleSave = async () => {
        setSaving(true);
        try {
            await onSave(selectedVenueIds);
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            opened={opened}
            onClose={onClose}
            title={t("title")}
            centered
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}
        >
            <Stack gap="md">
                <Text size="sm" fw={500}>
                    {ad.name}
                </Text>

                <VenueMultiSelectPicker
                    selectedVenueIds={selectedVenueIds}
                    onChange={setSelectedVenueIds}
                    helperText={tAddAd("venueTags.helper")}
                    emptyText={tAddAd("venueTags.noVenues")}
                />

                <Group justify="flex-end" mt="md">
                    <Button variant="default" onClick={onClose} disabled={saving}>
                        {t("buttons.cancel")}
                    </Button>
                    {/* Disabled only while in flight — an empty selection is a valid save. */}
                    <Button variant="gradient" onClick={handleSave} loading={saving}>
                        {t("buttons.save")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}
