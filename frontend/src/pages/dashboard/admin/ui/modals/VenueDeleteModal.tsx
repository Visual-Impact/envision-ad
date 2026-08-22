"use client";

import { Modal, Stack, Text, Group, Button, Alert } from "@mantine/core";
import { IconAlertTriangle } from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { Venue } from "@/entities/venue";

interface VenueDeleteModalProps {
    opened: boolean;
    onClose: () => void;
    onConfirm: () => Promise<void>;
    venue: Venue | null;
    /** Fetched by the parent, matching how mediaCount already arrives on the venue prop —
     *  this modal stays presentational and never fetches. */
    adCount: number;
}

export function VenueDeleteModal({ opened, onClose, onConfirm, venue, adCount }: VenueDeleteModalProps) {
    const t = useTranslations("venueManagement.delete");
    const [deleting, setDeleting] = useState(false);

    if (!venue) return null;

    const handleConfirm = async () => {
        setDeleting(true);
        try {
            await onConfirm();
        } finally {
            setDeleting(false);
        }
    };

    return (
        <Modal
            opened={opened}
            onClose={onClose}
            title={t("title")}
            size="sm"
            centered
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55 }}
        >
            <Stack gap="md">
                <Text>{t("message", { name: venue.nameEn })}</Text>

                {venue.mediaCount > 0 && (
                    <Alert color="orange" icon={<IconAlertTriangle size={18} />}>
                        {t("mediaWarning", { count: venue.mediaCount })}
                    </Alert>
                )}

                {/* Worth its own warning: an ad losing its last tag flips from
                    "this venue only" to "shown everywhere", unlike a media listing
                    which merely stops displaying a label. */}
                {adCount > 0 && (
                    <Alert color="orange" icon={<IconAlertTriangle size={18} />}>
                        {t("adWarning", { count: adCount })}
                    </Alert>
                )}

                <Group justify="flex-end" mt="md">
                    <Button variant="default" onClick={onClose}>
                        {t("cancel")}
                    </Button>
                    <Button color="red" onClick={handleConfirm} loading={deleting}>
                        {t("confirm")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}
