"use client";

import React from "react";
import { Badge, Group, Pagination, Paper, Table, Text, Tooltip } from "@mantine/core";
import { useMediaQuery } from "@mantine/hooks";
import { useLocale, useTranslations } from "next-intl";
import type { PayoutHistoryRow } from "@/pages/dashboard/media-owner/model/mockMetrics";
import { formatCurrency } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/formatting-utils";

interface PayoutHistorySectionProps {
    rows: PayoutHistoryRow[];
    currentPage: number;
    totalPages: number;
    onPageChange: (page: number) => void;
}

export function PayoutHistorySection({
    rows,
    currentPage,
    totalPages,
    onPageChange,
}: PayoutHistorySectionProps) {
    const t = useTranslations("mediaOwnerMetrics");
    const locale = useLocale();
    const isMobile = useMediaQuery("(max-width: 48em)");

    const formatTransactionIdForViewport = (transactionId: string) => {
        if (!isMobile || transactionId.length <= 16) {
            return transactionId;
        }

        return `${transactionId.slice(0, 8)}...${transactionId.slice(-6)}`;
    };

    return (
        <Paper shadow="sm" p="md" radius="lg" style={isMobile ? { overflowX: "hidden" } : undefined}>
            <Text fw={600} mb="sm">
                {t("sections.payoutHistory")}
            </Text>
            <Table
                striped
                highlightOnHover
                horizontalSpacing={isMobile ? "xs" : "md"}
                verticalSpacing="sm"
                layout={isMobile ? "fixed" : "auto"}
                style={{ width: "100%" }}
            >
                <Table.Thead>
                    <Table.Tr>
                        <Table.Th w={isMobile ? "35%" : undefined}>{t("table.transactionId")}</Table.Th>
                        <Table.Th w={isMobile ? "25%" : undefined}>{t("table.date")}</Table.Th>
                        <Table.Th ta="right" w={isMobile ? "22%" : undefined}>
                            {t("table.amount")}
                        </Table.Th>
                        <Table.Th w={isMobile ? "18%" : undefined}>{t("table.status")}</Table.Th>
                    </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                    {rows.length === 0 ? (
                        <Table.Tr>
                            <Table.Td colSpan={4}>
                                <Text c="dimmed" ta="center">
                                    {t("emptyState.noData")}
                                </Text>
                            </Table.Td>
                        </Table.Tr>
                    ) : (
                        rows.map((row) => (
                            <Table.Tr key={row.transactionId}>
                                <Table.Td>
                                    {isMobile ? (
                                        <Tooltip label={row.transactionId} withArrow>
                                            <Text size="sm" truncate>
                                                {formatTransactionIdForViewport(row.transactionId)}
                                            </Text>
                                        </Tooltip>
                                    ) : (
                                        row.transactionId
                                    )}
                                </Table.Td>
                                <Table.Td>
                                    <Text size="sm" style={{ whiteSpace: "nowrap" }}>
                                        {row.date}
                                    </Text>
                                </Table.Td>
                                <Table.Td ta="right">
                                    <Text size="sm" style={{ whiteSpace: "nowrap" }}>
                                        {formatCurrency(row.amount, { locale })}
                                    </Text>
                                </Table.Td>
                                <Table.Td>
                                    <Badge
                                        size={isMobile ? "xs" : "sm"}
                                        radius="sm"
                                        variant="light"
                                        color={row.status === "PAID" ? "teal" : "yellow"}
                                    >
                                        {t(`payoutStatus.${row.status.toLowerCase()}`)}
                                    </Badge>
                                </Table.Td>
                            </Table.Tr>
                        ))
                    )}
                </Table.Tbody>
            </Table>
            {totalPages > 1 && (
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
