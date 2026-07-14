"use client";

import { Modal, Stack, TextInput, ColorInput, Group, Button } from "@mantine/core";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { Venue, VenueRequestDTO } from "@/entities/venue";

interface VenueFormModalProps {
    opened: boolean;
    onClose: () => void;
    onSave: (data: VenueRequestDTO) => Promise<void>;
    venue: Venue | null;
}

export function VenueFormModal({ opened, onClose, onSave, venue }: VenueFormModalProps) {
    const t = useTranslations("venueManagement.form");
    const [nameEn, setNameEn] = useState("");
    const [nameFr, setNameFr] = useState("");
    const [colorCode, setColorCode] = useState("#3B82F6");
    const [saving, setSaving] = useState(false);
    const [errors, setErrors] = useState<{ nameEn?: string; nameFr?: string; colorCode?: string }>({});

    const isEdit = venue !== null;

    // Reset the form from `venue` each time the modal transitions to open.
    // Adjusting state during render (rather than in an effect) for a prop
    // change is the pattern React recommends here — see
    // https://react.dev/reference/react/useState#storing-information-from-previous-renders
    const [prevOpened, setPrevOpened] = useState(opened);
    if (opened !== prevOpened) {
        setPrevOpened(opened);
        if (opened) {
            setNameEn(venue?.nameEn ?? "");
            setNameFr(venue?.nameFr ?? "");
            setColorCode(venue?.colorCode ?? "#3B82F6");
            setErrors({});
        }
    }

    const validate = (): boolean => {
        const newErrors: typeof errors = {};
        if (!nameEn.trim()) newErrors.nameEn = t("nameEnLabel");
        else if (nameEn.trim().length > 20) newErrors.nameEn = t("nameTooLong");
        if (!nameFr.trim()) newErrors.nameFr = t("nameFrLabel");
        else if (nameFr.trim().length > 20) newErrors.nameFr = t("nameTooLong");
        if (!colorCode || !/^#[0-9A-Fa-f]{6}$/.test(colorCode)) newErrors.colorCode = t("colorCodeLabel");
        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = async () => {
        if (!validate()) return;
        setSaving(true);
        try {
            await onSave({ nameEn: nameEn.trim(), nameFr: nameFr.trim(), colorCode });
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            opened={opened}
            onClose={onClose}
            title={isEdit ? t("editTitle") : t("createTitle")}
            size="md"
            centered
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}
        >
            <Stack gap="md">
                <TextInput
                    label={t("nameEnLabel")}
                    placeholder={t("nameEnPlaceholder")}
                    value={nameEn}
                    onChange={(e) => setNameEn(e.currentTarget.value)}
                    error={errors.nameEn}
                    maxLength={20}
                    required
                />
                <TextInput
                    label={t("nameFrLabel")}
                    placeholder={t("nameFrPlaceholder")}
                    value={nameFr}
                    onChange={(e) => setNameFr(e.currentTarget.value)}
                    error={errors.nameFr}
                    maxLength={20}
                    required
                />
                <ColorInput
                    label={t("colorCodeLabel")}
                    value={colorCode}
                    onChange={setColorCode}
                    error={errors.colorCode}
                    format="hex"
                    required
                />
                <Group justify="flex-end" mt="md">
                    <Button variant="default" onClick={onClose}>
                        {t("cancel")}
                    </Button>
                    <Button variant="gradient" onClick={handleSubmit} loading={saving}>
                        {isEdit ? t("update") : t("create")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}
