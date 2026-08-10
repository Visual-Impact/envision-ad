"use client";

import { Button, Group, Loader, Paper, ScrollArea, Table, Text } from "@mantine/core";
import { useTranslations } from "next-intl";
import {VerificationResponseDTO} from "@/entities/organization/model/verification";

interface OrganizationVerificationTableProps {
    rows: VerificationResponseDTO[];
    onRequestRemoved: (id: string) => void;
    onRowClick: (request: VerificationResponseDTO) => void;
    getOrganizationName: (businessId: string) => string;
    getDetailStatus: (businessId: string) => "loading" | "loaded" | "error";
    onRetryDetail: (businessId: string) => void;
}

export function OrganizationVerificationTable({
                                                  rows,
                                                  onRowClick,
                                                  getOrganizationName,
                                                  getDetailStatus,
                                                  onRetryDetail
                                              }: OrganizationVerificationTableProps) {
    const t = useTranslations("admin.adminActions");

    return (
        <Paper shadow="sm" radius="lg">
            <ScrollArea>
                <Table
                    striped
                    highlightOnHover
                    withTableBorder={false}
                    withColumnBorders={false}
                    verticalSpacing="md"
                    horizontalSpacing="lg"
                    layout="auto"
                >
                    <Table.Thead>
                        <Table.Tr>
                            <Table.Th miw={200}>{t("organization")}</Table.Th>
                            <Table.Th miw={150}>{t("requestDate")}</Table.Th>
                        </Table.Tr>
                    </Table.Thead>

                    <Table.Tbody>
                        {rows.length > 0 ? (
                            rows.map((row) => {
                                const status = getDetailStatus(row.businessId);
                                if (status === "error") {
                                    return (
                                        <Table.Tr key={row.verificationId}>
                                            <Table.Td>
                                                <Text c="red" size="sm">{t('errors.loadDetailsFailed')}</Text>
                                            </Table.Td>
                                            <Table.Td>
                                                <Button
                                                    size="xs"
                                                    variant="light"
                                                    color="red"
                                                    onClick={() => onRetryDetail(row.businessId)}
                                                >
                                                    {t('retry')}
                                                </Button>
                                            </Table.Td>
                                        </Table.Tr>
                                    );
                                }

                                if (status === "loading") {
                                    return (
                                        <Table.Tr key={row.verificationId}>
                                            <Table.Td colSpan={2}>
                                                <Group gap="xs">
                                                    <Loader size="xs" />
                                                    <Text size="sm" c="dimmed">{row.businessId}</Text>
                                                </Group>
                                            </Table.Td>
                                        </Table.Tr>
                                    );
                                }

                                return (
                                    <Table.Tr
                                        key={row.verificationId}
                                        style={{ cursor: "pointer" }}
                                        onClick={() => onRowClick(row)}
                                    >
                                        <Table.Td>
                                            <Text fw={500}>{getOrganizationName(row.businessId)}</Text>
                                        </Table.Td>
                                        <Table.Td>
                                            <Text size="sm">
                                                {new Date(row.dateCreated).toLocaleDateString()}
                                            </Text>
                                        </Table.Td>
                                    </Table.Tr>
                                );
                            })
                        ) : (
                            <Table.Tr>
                                <Table.Td colSpan={2}>
                                    <Text ta="center" c="dimmed" py="xl">
                                        {t("noOrganizationsPending")}
                                    </Text>
                                </Table.Td>
                            </Table.Tr>
                        )}
                    </Table.Tbody>
                </Table>
            </ScrollArea>
        </Paper>
    );
}