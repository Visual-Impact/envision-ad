"use client";

import { Paper, ScrollArea, Table, Text } from "@mantine/core";
import { useTranslations } from "next-intl";
import {VerificationResponseDTO} from "@/entities/organization/model/verification";

interface OrganizationVerificationTableProps {
    rows: VerificationResponseDTO[];
    onRequestRemoved: (id: string) => void;
    onRowClick: (request: VerificationResponseDTO) => void;
    getOrganizationName: (businessId: string) => string;
    isLoading: (businessId: string) => boolean;
}

export function OrganizationVerificationTable({
                                                  rows,
                                                  onRowClick,
                                                  getOrganizationName,
                                                  isLoading
                                              }: OrganizationVerificationTableProps) {
    const t = useTranslations("admin.adminActions");

    const loadedRows = rows.filter(row => !isLoading(row.businessId));

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
                        {loadedRows.length > 0 ? (
                            loadedRows.map((row) => (
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
                            ))
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