"use client";

import { useState } from "react";
import { Alert, Button, Group, Modal, Select, Stack, TextInput } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import { useLocale, useTranslations } from "next-intl";
import { OrganizationDetailsForm } from "@/features/organization-management";
import { useOrganizationForm } from "@/features/organization-management";
import { CreateAccountRequestDTO } from "@/entities/account";
import { Venue } from "@/entities/venue";

interface CreateAccountModalProps {
    opened: boolean;
    onClose: () => void;
    onSave: (data: CreateAccountRequestDTO) => Promise<void>;
    venues: Venue[];
}

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function CreateAccountModal({ opened, onClose, onSave, venues }: CreateAccountModalProps) {
    const t = useTranslations("accountManagement.form");
    const locale = useLocale();

    const { formState: businessForm, updateField: updateBusinessField, resetForm: resetBusinessForm } =
        useOrganizationForm();
    const [email, setEmail] = useState("");
    const [name, setName] = useState("");
    const [businessTypeVenueId, setBusinessTypeVenueId] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);
    const [validationError, setValidationError] = useState<string | null>(null);

    // Reset once the modal transitions to open — same pattern as OrganizationModal /
    // VenueFormModal (adjusting state during render for a prop change, per
    // https://react.dev/reference/react/useState#storing-information-from-previous-renders).
    const [prevOpened, setPrevOpened] = useState(opened);
    if (opened !== prevOpened) {
        setPrevOpened(opened);
        if (opened) {
            setEmail("");
            setName("");
            setBusinessTypeVenueId(null);
            setValidationError(null);
            resetBusinessForm();
        }
    }

    const venueOptions = venues.map((venue) => ({
        value: venue.venueId,
        label: locale === "fr" ? venue.nameFr : venue.nameEn,
    }));

    const validate = (): boolean => {
        if (!email.trim()) {
            setValidationError(t("errors.emailRequired"));
            return false;
        }
        if (!EMAIL_REGEX.test(email.trim())) {
            setValidationError(t("errors.emailInvalid"));
            return false;
        }
        if (!name.trim()) {
            setValidationError(t("errors.nameRequired"));
            return false;
        }
        // Business-detail validation (name/size/address/roles) mirrors
        // OrganizationModal's own validateForm — no shared helper exists yet for it.
        if (!businessForm.name.trim()) {
            setValidationError(t("errors.validationError"));
            return false;
        }
        if (!businessForm.address.street.trim() || !businessForm.address.city.trim()
            || !businessForm.address.state.trim() || !businessForm.address.zipCode.trim()
            || !businessForm.address.country.trim()) {
            setValidationError(t("errors.validationError"));
            return false;
        }
        if (!businessForm.roles.advertiser && !businessForm.roles.mediaOwner) {
            setValidationError(t("errors.validationError"));
            return false;
        }
        return true;
    };

    const handleSubmit = async () => {
        setValidationError(null);
        if (!validate()) return;

        setSaving(true);
        try {
            await onSave({
                email: email.trim(),
                name: name.trim(),
                business: { ...businessForm, businessTypeVenueId },
            });
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            opened={opened}
            onClose={onClose}
            title={t("createTitle")}
            size="lg"
            centered
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55 }}
        >
            <Stack gap="md">
                {validationError && (
                    <Alert variant="light" color="red" icon={<IconInfoCircle />}>
                        {validationError}
                    </Alert>
                )}

                <TextInput
                    label={t("emailLabel")}
                    placeholder={t("emailPlaceholder")}
                    value={email}
                    onChange={(e) => setEmail(e.currentTarget.value)}
                    required
                />
                <TextInput
                    label={t("nameLabel")}
                    placeholder={t("namePlaceholder")}
                    value={name}
                    onChange={(e) => setName(e.currentTarget.value)}
                    required
                />

                <OrganizationDetailsForm formState={businessForm} onFieldChange={updateBusinessField} />

                <Select
                    label={t("businessTypeLabel")}
                    placeholder={t("businessTypePlaceholder")}
                    data={venueOptions}
                    value={businessTypeVenueId}
                    onChange={setBusinessTypeVenueId}
                    clearable
                    searchable
                />

                <Group justify="flex-end" mt="md">
                    <Button variant="default" onClick={onClose} disabled={saving}>
                        {t("cancel")}
                    </Button>
                    <Button variant="gradient" onClick={handleSubmit} loading={saving}>
                        {t("submit")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}
