"use client";

import { Button, Group, Modal, Select, Stack, Text, Textarea } from "@mantine/core";
import { useState } from "react";
import { useTranslations } from "next-intl";
import { DenialReason } from "@/entities/reservation";

interface DenyAdRequestModalProps {
    opened: boolean;
    onDeny: (reason: DenialReason, description: string | null) => void;
    onCancel: () => void;
}

export function DenyReservationModal({
                                       opened,
                                       onDeny,
                                       onCancel,
                                   }: DenyAdRequestModalProps) {
    const t = useTranslations("adRequests.modal");

    const [selectedReason, setSelectedReason] = useState<DenialReason | null>(null);
    const [description, setDescription] = useState("");
    const [error, setError] = useState("");

    const DENY_REASONS = [
        { value: DenialReason.CONTENT_POLICY, label: t("reasons.contentPolicy") },
        { value: DenialReason.CREATIVE_TECHNICAL, label: t("reasons.creativeTechnical") },
        { value: DenialReason.LEGAL_COMPLIANCE, label: t("reasons.legalCompliance") },
        { value: DenialReason.MEDIA_OWNER_RULES, label: t("reasons.mediaOwnerRules") },
        { value: DenialReason.LOCAL_VENUE, label: t("reasons.localVenue") },
        { value: DenialReason.OTHER, label: t("reasons.other") },
    ];

    const resetForm = () => {
        setSelectedReason(null);
        setDescription("");
        setError("");
    };

    const handleCancel = () => {
        resetForm();
        onCancel();
    };

    const handleDeny = () => {
        if (!selectedReason) {
            setError(t("errors.selectReason"));
            return;
        }

        if (selectedReason === DenialReason.OTHER && !description.trim()) {
            setError(t("errors.provideDetails"));
            return;
        }

        onDeny(selectedReason, description.trim() || null);

        resetForm();
    };

    return (
        <Modal
            opened={opened}
            onClose={handleCancel}
            title={t("title")}
            centered
            padding="lg"
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}
        >
            <Stack gap="md">
                <Text size="sm">
                    {t("description")}
                </Text>

                <Select
                    label={t("reasonLabel")}
                    placeholder={t("reasonPlaceholder")}
                    data={DENY_REASONS}
                    value={selectedReason}
                    onChange={(value) => {
                        setSelectedReason(value as DenialReason);
                        setError("");
                    }}
                    required
                    error={error && !selectedReason ? error : undefined}
                />

                <Textarea
                    label={t("detailsLabel")}
                    placeholder={t("detailsPlaceholder")}
                    value={description}
                    onChange={(event) => {
                        setDescription(event.currentTarget.value);
                        setError("");
                    }}
                    maxLength={512}
                    rows={4}
                    required={selectedReason === DenialReason.OTHER}
                    error={error && selectedReason === DenialReason.OTHER && !description.trim() ? error : undefined}
                    description={t("characterCount", { count: description.length })}
                />

                <Group justify="flex-end" mt="md">
                    <Button variant="default" onClick={handleCancel}>
                        {t("cancelButton")}
                    </Button>

                    <Button color="red" onClick={handleDeny}>
                        {t("denyButton")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}