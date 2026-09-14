"use client";

import React from "react";
import { Alert, Anchor, Badge, Button, Group, Paper, Skeleton, Stack, Text, Title } from "@mantine/core";
import { IconAlertTriangle, IconBell } from "@tabler/icons-react";
import { useFormatter, useTranslations } from "next-intl";
import { ActiveCampaignSummary } from "@/entities/ad-campaign";
import { AdThumbnail } from "./AdThumbnail";

export type ActiveCampaignSlotState =
    | { kind: "loading" }
    /** No live subscription: nothing is on screen, so there is no slot (FR-1.3). */
    | { kind: "hidden" }
    /** A live subscription but no active campaign — inconsistent data, never auto-fixed (FR-1.4). */
    | { kind: "missing" }
    | { kind: "ready"; summary: ActiveCampaignSummary };

const MAX_THUMBNAILS = 6;

interface ActiveCampaignSlotProps {
    state: ActiveCampaignSlotState;
    /** Seconds left on the shared swap/notify cooldown; 0 when there is none. */
    cooldownSeconds: number;
    notifying: boolean;
    supportHref: string;
    onSwap: () => void;
    onNotify: () => void;
}

/** The "Currently Displaying" panel at the top of the advertiser's campaigns page. */
export function ActiveCampaignSlot({ state, cooldownSeconds, notifying, supportHref, onSwap, onNotify }: ActiveCampaignSlotProps) {
    const t = useTranslations("activeCampaignSlot");
    const format = useFormatter();

    if (state.kind === "hidden") return null;

    if (state.kind === "loading") {
        return (
            <Paper withBorder radius="lg" shadow="md" p="lg">
                <Stack gap="sm">
                    <Skeleton height={14} width={140} />
                    <Skeleton height={26} width="40%" />
                    <Group gap="sm">
                        {Array.from({ length: 4 }, (_, i) => (
                            <Skeleton key={i} height={68} width={120} radius="md" />
                        ))}
                    </Group>
                </Stack>
            </Paper>
        );
    }

    if (state.kind === "missing") {
        return (
            <Paper withBorder radius="lg" shadow="md" p="lg" style={{ borderLeft: "4px solid var(--mantine-color-red-6)" }}>
                <Alert color="red" variant="light" icon={<IconAlertTriangle size={18} />} title={t("errorState.title")}>
                    <Stack gap="xs" align="flex-start">
                        <Text size="sm">{t("errorState.message")}</Text>
                        <Anchor href={supportHref} size="sm">{t("errorState.contactSupport")}</Anchor>
                    </Stack>
                </Alert>
            </Paper>
        );
    }

    const { summary } = state;
    const visibleAds = summary.ads.slice(0, MAX_THUMBNAILS);
    const hiddenCount = summary.ads.length - visibleAds.length;
    const onCooldown = cooldownSeconds > 0;
    const pending = summary.hasUnnotifiedCreativeChanges;
    // The automatic-send notice is only worth showing while it is the latest word: a later swap
    // or manual notify supersedes it, and pending changes mean it is already out of date.
    const autoNotifiedAt = summary.lastAutoNotifiedAt;
    const showAutoNotice = autoNotifiedAt !== null
        && !pending
        && (summary.lastSwapAt === null || Date.parse(autoNotifiedAt) > Date.parse(summary.lastSwapAt));

    return (
        <Paper withBorder radius="lg" shadow="md" p="lg" style={{ borderLeft: "4px solid var(--mantine-color-teal-6)" }}>
            {pending && (
                <Alert mb="md" color="orange" variant="light" icon={<IconBell size={18} />}>
                    {t("pendingBanner")}
                </Alert>
            )}

            <Group justify="space-between" align="flex-start" wrap="wrap" gap="lg">
                <Stack gap={6} style={{ flex: "1 1 220px", minWidth: 0 }}>
                    <Badge color="teal" variant="light" w="fit-content">{t("badge")}</Badge>
                    <Title order={3}>{summary.name}</Title>
                    <Text size="sm" c="dimmed">
                        {t("stats", { bundles: summary.subscribedBundleCount, screens: summary.subscribedScreenCount })}
                    </Text>
                </Stack>

                <Group gap="sm" wrap="wrap" style={{ flex: "2 1 320px" }}>
                    {visibleAds.map((ad) => (
                        <AdThumbnail key={ad.adId} ad={ad} />
                    ))}
                    {hiddenCount > 0 && (
                        <Badge variant="default" size="lg">{t("moreThumbnails", { count: hiddenCount })}</Badge>
                    )}
                </Group>

                <Stack gap="xs" style={{ minWidth: 200 }}>
                    <Button variant="gradient" onClick={onSwap} disabled={onCooldown}>
                        {t("swapButton")}
                    </Button>
                    {/* Promoted to primary emphasis while owners are out of date (FR-8.4). */}
                    <Button
                        variant={pending ? "filled" : "light"}
                        color={pending ? "orange" : undefined}
                        size={pending ? "sm" : "xs"}
                        leftSection={<IconBell size={14} />}
                        onClick={onNotify}
                        loading={notifying}
                        disabled={onCooldown}
                    >
                        {t("notifyButton")}
                    </Button>
                </Stack>
            </Group>

            {onCooldown && (
                <Text mt="sm" size="sm" c="dimmed">
                    {t("cooldown", { minutes: Math.floor(cooldownSeconds / 60), seconds: cooldownSeconds % 60 })}
                </Text>
            )}
            {showAutoNotice && (
                <Text mt="sm" size="xs" c="dimmed">
                    {t("autoNotifiedNotice", {
                        date: format.dateTime(new Date(autoNotifiedAt), { dateStyle: "medium", timeStyle: "short" }),
                    })}
                </Text>
            )}
        </Paper>
    );
}
