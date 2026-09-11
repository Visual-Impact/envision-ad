"use client";

import React from "react";
import { Group, Paper, Text, ThemeIcon } from "@mantine/core";
import { useLocale } from "next-intl";
import { formatCurrency } from "@/pages/dashboard/media-owner/lib/metrics/formatting-utils";
import type { ChartTooltipPayload } from "@/pages/dashboard/media-owner/model/metricsTypes";

interface ChartTooltipContentProps {
    labelText: string;
    items: ChartTooltipPayload[];
}

export function ChartTooltipContent({ labelText, items }: ChartTooltipContentProps) {
    const locale = useLocale();

    if (!items || items.length === 0) return null;

    const activeItems = items.filter((item) => Number(item.value) > 0);

    if (activeItems.length === 0) return null;

    return (
        <Paper px="md" py="xs" withBorder shadow="md" radius="md">
            <Text fw={500} mb={5}>
                {labelText}
            </Text>
            {activeItems.map((item, index) => (
                <Group key={item.dataKey ?? item.name ?? index} gap="xs" justify="space-between" mt={4}>
                    <Group gap={5}>
                        <ThemeIcon color={item.color} variant="filled" size={8} radius="xl" />
                        <Text size="sm" c="dimmed">
                            {item.name}
                        </Text>
                    </Group>
                    <Text size="sm" fw={500}>
                        {formatCurrency(item.value, { locale })}
                    </Text>
                </Group>
            ))}
        </Paper>
    );
}
