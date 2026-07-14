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
}

export function VenueDeleteModal({ opened, onClose, onConfirm, venue }: VenueDeleteModalProps) {
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
            overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}
        >
            <Stack gap="md">
                <Text>{t("message", { name: venue.nameEn })}</Text>

                {venue.mediaCount > 0 && (
                    <Alert color="orange" icon={<IconAlertTriangle size={18} />}>
                        {t("mediaWarning", { count: venue.mediaCount })}
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
