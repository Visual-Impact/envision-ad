"use client";

import React from "react";
import { Badge, Group, Pagination, Paper, Stack, Text } from "@mantine/core";
import { useLocale, useTranslations } from "next-intl";
import type { ActiveCampaignItem } from "@/pages/dashboard/media-owner/model/mockMetrics";
import { formatCurrency } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/formatting-utils";

interface ActiveCampaignDetailsSectionProps {
    rows: ActiveCampaignItem[];
    totalRows: number;
    currentPage: number;
    totalPages: number;
    rowsPerPage: number;
    onPageChange: (page: number) => void;
}

export function ActiveCampaignDetailsSection({
    rows,
    totalRows,
    currentPage,
    totalPages,
    rowsPerPage,
    onPageChange,
}: ActiveCampaignDetailsSectionProps) {
    const t = useTranslations("mediaOwnerMetrics");
    const locale = useLocale();

    return (
        <Paper shadow="sm" p="md" radius="lg">
            <Text fw={600} mb="sm">
                {t("sections.activeCampaignDetails")}
            </Text>
            {totalRows === 0 ? (
                <Text size="sm" c="dimmed">
                    {t("emptyState.noData")}
                </Text>
            ) : (
                <Stack gap="md">
                    {rows.map((campaign, index) => (
                        <Group
                            key={`${campaign.campaignName}-${campaign.advertiserName}-${index}`}
                            justify="space-between"
                            align="center"
                        >
                            <Stack gap={0}>
                                <Text size="sm" fw={500}>
                                    {campaign.campaignName}
                                </Text>
                                <Text size="xs" c="dimmed">
                                    {campaign.advertiserName}
                                </Text>
                            </Stack>
                            <Stack gap={4} align="flex-end">
                                <Text fw={600} size="sm">
                                    {formatCurrency(campaign.amount, { locale })}
                                </Text>
                                <Badge color="teal" variant="light" radius="sm" size="xs">
                                    {t(`campaignStatus.${campaign.status.toLowerCase()}`)}
                                </Badge>
                            </Stack>
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
