"use client";

import { Alert, Button, Group, Modal, Stack, Text } from "@mantine/core";
import { IconAlertTriangle } from "@tabler/icons-react";
import { useLocale, useTranslations } from "next-intl";
import { useState } from "react";
import { Bundle } from "@/entities/bundle";

interface BundleDeleteModalProps {
    opened: boolean;
    onClose: () => void;
    onConfirm: () => Promise<void>;
    bundle: Bundle | null;
}

export function BundleDeleteModal({ opened, onClose, onConfirm, bundle }: BundleDeleteModalProps) {
    const t = useTranslations("bundleManagement.delete");
    const locale = useLocale();
    const [deleting, setDeleting] = useState(false);

    if (!bundle) return null;

    // The server rejects this with a 409 regardless; warning here saves the round trip.
    // Since D47 the count covers every status, not just live ones — cancelled subscriptions
    // still pin the bundle, because bundle_subscriptions.bundle_id has no ON DELETE clause and
    // Postgres refuses the delete. The field keeps its `activeSubscriptionCount` name for wire
    // compatibility, but "active" no longer describes what it counts.
    const isBlocked = bundle.activeSubscriptionCount > 0;
    const name = locale === "fr" ? bundle.nameFr : bundle.nameEn;

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
                <Text>{t("message", { name })}</Text>

                {isBlocked && (
                    <Alert color="red" icon={<IconAlertTriangle size={18} />}>
                        {t("blockedWarning", { count: bundle.activeSubscriptionCount })}
                    </Alert>
                )}

                <Group justify="flex-end" mt="md">
                    <Button variant="default" onClick={onClose}>
                        {t("cancel")}
                    </Button>
                    <Button color="red" onClick={handleConfirm} loading={deleting} disabled={isBlocked}>
                        {t("confirm")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}
