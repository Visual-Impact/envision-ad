"use client";

import React from "react";
import { Group, Pagination, Paper, Stack, Text, ThemeIcon } from "@mantine/core";
import { useLocale, useTranslations } from "next-intl";
import { formatCurrency } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/formatting-utils";
import type { RevenueByLocationItem } from "@/pages/dashboard/media-owner/model/mockMetrics";

interface RevenueByMediaSectionProps {
    rows: RevenueByLocationItem[];
    totalRows: number;
    currentPage: number;
    totalPages: number;
    rowsPerPage: number;
    onPageChange: (page: number) => void;
}

export function RevenueByMediaSection({
    rows,
    totalRows,
    currentPage,
    totalPages,
    rowsPerPage,
    onPageChange,
}: RevenueByMediaSectionProps) {
    const t = useTranslations("mediaOwnerMetrics");
    const locale = useLocale();

    return (
        <Paper shadow="sm" p="md" radius="lg">
            <Text fw={600} mb="sm">
                {t("sections.revenueByMediaLocation")}
            </Text>
            {totalRows === 0 ? (
                <Text size="sm" c="dimmed">
                    {t("emptyState.noData")}
                </Text>
            ) : (
                <Stack gap="md">
                    {rows.map((item) => (
                        <Group justify="space-between" key={item.locationName}>
                            <Group gap="xs">
                                <ThemeIcon size={10} radius="xl" color={item.color} />
                                <Text size="sm">{item.locationName}</Text>
                            </Group>
                            <Text fw={600} size="sm">
                                {formatCurrency(item.revenue, { locale })}
                            </Text>
                        </Group>
                    ))}
                </Stack>
            )}
            {totalRows > rowsPerPage && (
                <Group justify="flex-end" mt="md">
                    <Pagination
                        value={currentPage}
                        onChange={onPageChange}
                        total={totalPages}
                        withEdges
                    />
                </Group>
            )}
        </Paper>
    );
}
