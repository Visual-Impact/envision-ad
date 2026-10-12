"use client";

import { useState } from "react";
import { Alert, Button, Group, Modal, Stack, TextInput } from "@mantine/core";
import { notifications } from "@mantine/notifications";
import axios from "axios";
import { useLocale, useTranslations } from "next-intl";
import { upsertProofLink, type MediaOwnerProofSubscription, type SavedProofLink } from "../../api";

type Props = {
    /** The row being edited. The parent mounts this per row, so the field starts from its link. */
    subscription: MediaOwnerProofSubscription;
    onClose: () => void;
    onSaved: (saved: SavedProofLink) => void;
};

/** Mirrors the backend's check (P12-PROGRESS D8) so an obvious typo never makes a round trip. */
function isHttpUrl(value: string): boolean {
    try {
        const url = new URL(value);
        return (url.protocol === "http:" || url.protocol === "https:") && url.hostname.length > 0;
    } catch {
        return false;
    }
}

/**
 * Add or edit one subscription's proof link (P12 FR-3.3). Replaces the old two-step campaign
 * picker + Cloudinary upload: the row already identifies the subscription, and the proof itself
 * lives on the owner's own page. The filename is kept so route wiring doesn't churn.
 */
export default function SubmitProofStepperModal({ subscription, onClose, onSaved }: Props) {
    const t = useTranslations("proofOfDisplay");
    const locale = useLocale();

    const [url, setUrl] = useState(subscription.proofLink.url ?? "");
    const [fieldError, setFieldError] = useState<string | null>(null);
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    const advertiser = subscription.advertiserBusinessName ?? t("table.advertiserUnknown");
    const bundle =
        (locale === "fr" ? subscription.bundleNameFr : subscription.bundleNameEn) ?? t("table.bundleUnavailable");

    async function handleSave() {
        const trimmed = url.trim();
        if (!isHttpUrl(trimmed)) {
            setFieldError(t("errors.invalidUrl"));
            return;
        }

        setSaving(true);
        setFieldError(null);
        setSubmitError(null);
        try {
            const saved = await upsertProofLink(subscription.subscriptionId, trimmed);
            notifications.show({ message: t("link.saved"), color: "green" });
            onSaved(saved);
        } catch (e) {
            // The backend's `message` is free text for logs, not for display (see HttpErrorInfo):
            // branch on the status and the machine-readable `code` instead.
            const response = axios.isAxiosError(e) ? e.response : undefined;
            const code = (response?.data as { code?: string } | undefined)?.code;
            if (response?.status === 400) {
                setFieldError(t("errors.invalidUrl"));
            } else if (code === "PROOF_LINK_MULTI_OWNER") {
                setSubmitError(t("errors.multiOwner"));
            } else if (code === "PROOF_LINK_NOT_EDITABLE") {
                setSubmitError(t("errors.notEditable"));
            } else {
                setSubmitError(t("errors.saveFailed"));
            }
        } finally {
            setSaving(false);
        }
    }

    return (
        <Modal
            opened
            onClose={onClose}
            centered
            size="lg"
            radius="lg"
            title={t("modal.title", { advertiser, bundle })}
            overlayProps={{ backgroundOpacity: 0.55 }}
        >
            <Stack gap="md">
                <TextInput
                    label={t("link.urlLabel")}
                    description={t("link.urlDescription")}
                    placeholder={t("link.urlPlaceholder")}
                    value={url}
                    onChange={(event) => {
                        setUrl(event.currentTarget.value);
                        setFieldError(null);
                    }}
                    error={fieldError}
                    type="url"
                    data-autofocus
                    required
                />

                {submitError && <Alert color="red">{submitError}</Alert>}

                <Group justify="flex-end">
                    <Button variant="default" onClick={onClose} disabled={saving}>
                        {t("link.cancel")}
                    </Button>
                    <Button variant="gradient" onClick={handleSave} loading={saving} disabled={url.trim() === ""}>
                        {t("link.save")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}
