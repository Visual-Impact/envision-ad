"use client";

import { useEffect, useState } from "react";
import { Alert, Button, Group, Modal, Select, Stack, Text } from "@mantine/core";
import { IconAlertTriangle } from "@tabler/icons-react";
import { useLocale, useTranslations } from "next-intl";
import { AccountListItem } from "../../model/account";
import { Venue } from "@/entities/venue";
import { getBusinessTypeChangeImpact } from "../../api";

interface EditBusinessTypeModalProps {
    opened: boolean;
    onClose: () => void;
    onSave: (businessTypeVenueId: string | null) => Promise<void>;
    account: AccountListItem | null;
    venues: Venue[];
}

// Admin-only (PATCH /api/v1/admin/accounts/{businessId}/business-type). The business type drives
// competitive exclusion (P4), which is why the self-service organization form never exposes it.
export function EditBusinessTypeModal({ opened, onClose, onSave, account, venues }: EditBusinessTypeModalProps) {
    const t = useTranslations("accountManagement.businessTypeModal");
    const locale = useLocale();
    const [businessTypeVenueId, setBusinessTypeVenueId] = useState<string | null>(null);
    const [saving, setSaving] = useState(false);

    // Same render-time reset as EditRolesModal: seed from the account each time the modal opens.
    const [prevOpened, setPrevOpened] = useState(opened);
    if (opened !== prevOpened) {
        setPrevOpened(opened);
        if (opened && account) {
            setBusinessTypeVenueId(account.businessTypeVenueId);
        }
    }

    const venueOptions = venues.map((venue) => ({
        value: venue.venueId,
        label: locale === "fr" ? venue.nameFr : venue.nameEn,
    }));

    const unchanged = businessTypeVenueId === (account?.businessTypeVenueId ?? null);

    // Setting a type never touches existing subscriptions: the advertiser keeps paying for screens
    // of that venue they already bought, but their creatives stop being sent there. Warn before
    // saving, never block. Keyed by venue so a slow response for a previous pick is ignored.
    const businessId = account?.businessId ?? null;
    const [impact, setImpact] = useState<{ venueId: string; screenCount: number } | null>(null);
    useEffect(() => {
        let cancelled = false;

        (async () => {
            if (!opened || !businessId || !businessTypeVenueId || unchanged) {
                if (!cancelled) setImpact(null);
                return;
            }
            try {
                const result = await getBusinessTypeChangeImpact(businessId, businessTypeVenueId);
                if (!cancelled) {
                    setImpact({ venueId: businessTypeVenueId, screenCount: result.liveSubscriptionScreenCount });
                }
            } catch {
                // The warning is advisory; failing to load it must not block the edit.
                if (!cancelled) setImpact(null);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [opened, businessId, businessTypeVenueId, unchanged]);
    const affectedScreenCount =
        impact && impact.venueId === businessTypeVenueId && !unchanged ? impact.screenCount : 0;

    const handleSave = async () => {
        setSaving(true);
        try {
            await onSave(businessTypeVenueId);
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            opened={opened}
            onClose={onClose}
            title={t("title", { business: account?.name ?? "" })}
            size="sm"
            centered
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55 }}
        >
            <Stack gap="md">
                <Text size="sm" c="dimmed">
                    {t("description")}
                </Text>

                <Select
                    label={t("label")}
                    placeholder={t("placeholder")}
                    data={venueOptions}
                    value={businessTypeVenueId}
                    onChange={setBusinessTypeVenueId}
                    clearable
                    searchable
                />

                {affectedScreenCount > 0 && (
                    <Alert color="yellow" variant="light" icon={<IconAlertTriangle size={18} />}>
                        {t("liveSubscriptionWarning", { count: affectedScreenCount })}
                    </Alert>
                )}

                <Group justify="flex-end" mt="md">
                    <Button variant="default" onClick={onClose} disabled={saving}>
                        {t("cancel")}
                    </Button>
                    <Button variant="gradient" onClick={handleSave} loading={saving} disabled={unchanged}>
                        {t("save")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}
