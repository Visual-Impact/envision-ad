"use client";

import React from "react";
import { Group, Paper, Text, ThemeIcon } from "@mantine/core";
import {
    IconArrowDownRight,
    IconArrowUpRight,
    IconChartBar,
    IconCoin,
    IconSpeakerphone,
    IconTrendingUp,
} from "@tabler/icons-react";
import { useLocale, useTranslations } from "next-intl";
import type { MetricsKpi } from "@/pages/dashboard/media-owner/model/mockMetrics";
import { formatCurrency } from "@/pages/dashboard/media-owner/lib/metrics/formatting-utils";
import styles from "./KpiCard.module.css";

const kpiIconMap: Record<string, React.ElementType> = {
    weeklyEarnings: IconCoin,
    monthlyEarnings: IconChartBar,
    yearlyEarnings: IconTrendingUp,
    activeCampaigns: IconSpeakerphone,
};

const kpiColorMap: Record<string, string> = {
    weeklyEarnings: "blue",
    monthlyEarnings: "grape",
    yearlyEarnings: "teal",
    activeCampaigns: "orange",
};

export function KpiCard({ item }: { item: MetricsKpi }) {
    const t = useTranslations("mediaOwnerMetrics");
    const locale = useLocale();
    const Icon = kpiIconMap[item.id] ?? IconChartBar;
    const kpiColor = kpiColorMap[item.id] ?? "blue";
    const isHighlightedTitle =
        item.id === "weeklyEarnings" ||
        item.id === "monthlyEarnings" ||
        item.id === "yearlyEarnings" ||
        item.id === "activeCampaigns";
    const isUp = item.trend === "up";
    const diffColor = isUp ? "teal.6" : "red.6";
    const comparisonTextKey = isUp
        ? "cards.increaseComparedToLastPeriod"
        : "cards.decreaseComparedToLastPeriod";

    return (
        <Paper p="md" radius="lg" shadow="sm" className={styles.card}>
            <Group justify="space-between" mb="xs">
                <Text c={isHighlightedTitle ? kpiColor : "dimmed"} size="xs" fw={700} tt="uppercase">
                    {t(`cards.${item.id}`)}
                </Text>
                <ThemeIcon variant="light" color={kpiColor} radius="xl" size="md">
                    <Icon size={14} />
                </ThemeIcon>
            </Group>

            <Group align="baseline" gap="xs" mb={2}>
                <Text fw={700} size="xl">
                    {item.id === "activeCampaigns"
                        ? item.value.toString()
                        : formatCurrency(item.value, { locale })}
                </Text>
                <Group gap={4}>
                    {isUp ? (
                        <IconArrowUpRight size={14} color="var(--mantine-color-teal-6)" />
                    ) : (
                        <IconArrowDownRight size={14} color="var(--mantine-color-red-6)" />
                    )}
                    <Text fw={600} size="xs" c={diffColor}>
                        {item.deltaPercent > 0 ? `${item.deltaPercent}%` : "--"}
                    </Text>
                </Group>
            </Group>

            <Text size="xs" c="dimmed">
                {t(comparisonTextKey)}
            </Text>
        </Paper>
    );
}
