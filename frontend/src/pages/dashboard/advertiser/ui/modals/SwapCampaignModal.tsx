"use client";

import React, { useEffect, useState } from "react";
import axios from "axios";
import { Alert, Badge, Button, Center, Group, Loader, Modal, Radio, ScrollArea, Stack, Text } from "@mantine/core";
import { IconAlertCircle } from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import { ActiveCampaignSummary, AdCampaign } from "@/entities/ad-campaign";
import { getEligibleSwapCampaigns, swapActiveCampaign } from "@/features/ad-campaign-management";
import { AdThumbnail } from "../AdThumbnail";

const STRIP_THUMBNAILS = 4;

interface SwapCampaignModalProps {
    opened: boolean;
    businessId: string;
    onClose: () => void;
    onSwapped: (summary: ActiveCampaignSummary, campaignName: string) => void;
    /** The server refused because of the shared cooldown; the page disables swap and notify. */
    onCooldown: (retryAfterSeconds: number) => void;
    onCreateCampaign: () => void;
}

export function SwapCampaignModal({ opened, onClose, ...rest }: SwapCampaignModalProps) {
    const t = useTranslations("activeCampaignSlot.swapModal");

    return (
        <Modal
            opened={opened}
            onClose={onClose}
            title={t("title")}
            centered
            radius="lg"
            size="lg"
            overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}
        >
            {/* Mantine unmounts a closed modal's content, so each open starts with a fresh list,
                no stale selection and no leftover error. */}
            <SwapCampaignModalBody onClose={onClose} {...rest} />
        </Modal>
    );
}

type LoadState =
    | { kind: "loading" }
    | { kind: "failed" }
    | { kind: "ready"; campaigns: AdCampaign[] };

function SwapCampaignModalBody({ businessId, onClose, onSwapped, onCooldown, onCreateCampaign }: Omit<SwapCampaignModalProps, "opened">) {
    const t = useTranslations("activeCampaignSlot");
    const [load, setLoad] = useState<LoadState>({ kind: "loading" });
    const [selectedId, setSelectedId] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        let ignored = false;

        // IIFE-nested per react-hooks/set-state-in-effect (see VenueMultiSelectPicker).
        (async () => {
            try {
                // Eligible campaigns already exclude the active one and any without creatives,
                // so the confirm button never has a no-op or an invalid target to send (FR-2.5).
                const campaigns = await getEligibleSwapCampaigns(businessId);
                if (!ignored) setLoad({ kind: "ready", campaigns });
            } catch (e) {
                console.error("Failed to load campaigns eligible for swap", e);
                if (!ignored) setLoad({ kind: "failed" });
            }
        })();

        return () => { ignored = true; };
    }, [businessId]);

    const handleConfirm = async () => {
        if (!selectedId || load.kind !== "ready") return;
        const campaign = load.campaigns.find((c) => c.campaignId === selectedId);

        setSubmitting(true);
        setError(null);
        try {
            const summary = await swapActiveCampaign(businessId, selectedId);
            onSwapped(summary, campaign?.name ?? summary.name);
        } catch (e) {
            // The modal stays open with the selection intact, and says why (FR-2.4).
            const status = axios.isAxiosError(e) ? e.response?.status : undefined;
            const data = axios.isAxiosError(e) ? e.response?.data : undefined;
            if (data?.code === "SWAP_DEBOUNCED" && typeof data.retryAfterSeconds === "number") {
                onCooldown(data.retryAfterSeconds);
                setError(t("notifications.swap.error.debounceMessage"));
            } else if (status === 409) {
                setError(t("notifications.swap.error.noAdsMessage"));
            } else {
                setError(t("notifications.swap.error.genericMessage"));
            }
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Stack gap="md">
            {load.kind === "loading" && (
                <Center py="xl"><Loader /></Center>
            )}

            {load.kind === "failed" && (
                <Alert color="red" icon={<IconAlertCircle size={18} />}>{t("swapModal.loadFailed")}</Alert>
            )}

            {load.kind === "ready" && load.campaigns.length === 0 && (
                <Stack align="center" gap="sm" py="lg">
                    <Text c="dimmed" ta="center" size="sm">{t("swapModal.noEligibleCampaigns")}</Text>
                    <Button variant="light" onClick={onCreateCampaign}>{t("swapModal.createCampaignShortcut")}</Button>
                </Stack>
            )}

            {load.kind === "ready" && load.campaigns.length > 0 && (
                <ScrollArea.Autosize mah={420}>
                    <Radio.Group value={selectedId} onChange={setSelectedId}>
                        <Stack gap="sm">
                            {load.campaigns.map((campaign) => (
                                <Radio.Card key={campaign.campaignId} value={campaign.campaignId} radius="md" p="md">
                                    <Group wrap="nowrap" align="flex-start" gap="md">
                                        <Radio.Indicator />
                                        <Stack gap={6} style={{ flex: 1, minWidth: 0 }}>
                                            <Group gap="xs">
                                                <Text fw={600}>{campaign.name}</Text>
                                                <Badge variant="light" size="sm">
                                                    {t("swapModal.adsCount", { count: campaign.ads.length })}
                                                </Badge>
                                            </Group>
                                            <Group gap="xs" wrap="nowrap">
                                                {campaign.ads.slice(0, STRIP_THUMBNAILS).map((ad) => (
                                                    <AdThumbnail key={ad.adId} ad={ad} width={80} height={45} />
                                                ))}
                                            </Group>
                                        </Stack>
                                    </Group>
                                </Radio.Card>
                            ))}
                        </Stack>
                    </Radio.Group>
                </ScrollArea.Autosize>
            )}

            {error && (
                <Alert color="red" icon={<IconAlertCircle size={18} />}>{error}</Alert>
            )}

            <Group justify="flex-end">
                <Button variant="default" onClick={onClose}>{t("swapModal.buttons.cancel")}</Button>
                <Button variant="gradient" onClick={handleConfirm} loading={submitting} disabled={!selectedId}>
                    {t("swapModal.buttons.confirm")}
                </Button>
            </Group>
        </Stack>
    );
}
