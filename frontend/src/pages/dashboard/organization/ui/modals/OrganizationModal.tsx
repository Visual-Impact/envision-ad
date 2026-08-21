"use client";

import React, {useState} from "react";
import {Alert, Button, Group, Modal, Stack} from "@mantine/core";
import {useTranslations} from "next-intl";
import {OrganizationDetailsForm} from "./OrganizationDetailsForm";
import {OrganizationRequestDTO} from "@/entities/organization";
import {IconInfoCircle} from "@tabler/icons-react";

interface OrganizationModalProps {
    opened: boolean;
    onClose: () => void;
    onSave: () => Promise<void>;
    formState: OrganizationRequestDTO;
    onFieldChange: <K extends keyof OrganizationRequestDTO>(
        field: K,
        value: OrganizationRequestDTO[K]
    ) => void;
    editingId: string | null;
}

export function OrganizationModal({
                                      opened,
                                      onClose,
                                      onSave,
                                      formState,
                                      onFieldChange,
                                      editingId,
                                  }: OrganizationModalProps) {
    const t = useTranslations("organization.form");
    const [saving, setSaving] = useState(false);
    const [validationError, setValidationError] = useState<string | null>(null);

    // Reset once the modal transitions to open. Adjusting state during
    // render (rather than in an effect) for a prop change is the pattern
    // React recommends — see
    // https://react.dev/reference/react/useState#storing-information-from-previous-renders
    const [prevOpened, setPrevOpened] = useState(opened);
    if (opened !== prevOpened) {
        setPrevOpened(opened);
        if (opened) {
            setValidationError(null);
        }
    }

    const validateForm = (): boolean => {
        if (!formState.name || formState.name.trim() === '') {
            setValidationError(t("errors.nameRequired"));
            return false;
        }

        if (!formState.organizationSize) {
            setValidationError(t("errors.sizeRequired"));
            return false;
        }

        if (!formState.address.street || formState.address.street.trim() === '') {
            setValidationError(t("errors.streetRequired"));
            return false;
        }

        if (!formState.address.city || formState.address.city.trim() === '') {
            setValidationError(t("errors.cityRequired"));
            return false;
        }

        if (!formState.address.state || formState.address.state.trim() === '') {
            setValidationError(t("errors.stateRequired"));
            return false;
        }

        if (!formState.address.zipCode || formState.address.zipCode.trim() === '') {
            setValidationError(t("errors.zipRequired"));
            return false;
        }

        if (!formState.address.country || formState.address.country.trim() === '') {
            setValidationError(t("errors.countryRequired"));
            return false;
        }

        // Roles are not editable here (admin-only post-creation, see
        // OrganizationDetailsForm's showRoles prop) — formState.roles always carries
        // whatever the organization already had, which was already validated at creation.

        return true;
    };

    const handleSave = async () => {
        setValidationError(null);

        if (!validateForm()) {
            return;
        }

        setSaving(true);
        try {
            await onSave();
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            opened={opened}
            onClose={onClose}
            title={editingId ? t("editTitle") : t("createTitle")}
            size="lg"
            centered
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55 }}
        >
            <Stack gap="md">
                {validationError && (
                    <Alert
                        variant="light"
                        color="red"
                        title={t("errors.validationError")}
                        icon={<IconInfoCircle />}
                    >
                        {validationError}
                    </Alert>
                )}

                <OrganizationDetailsForm
                    formState={formState}
                    onFieldChange={onFieldChange}
                    showRoles={false}
                />

                <Group justify="flex-end" mt="md">
                    <Button variant="default" onClick={onClose} disabled={saving}>
                        {t("cancel")}
                    </Button>
                    <Button variant="gradient" onClick={handleSave} loading={saving}>
                        {editingId ? t("update") : t("create")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}