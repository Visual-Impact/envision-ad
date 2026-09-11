"use client";

import { Avatar, Paper, ScrollArea, Table, Text } from "@mantine/core";
import { useRouter } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import type { MediaStatusEnum } from "@/entities/media";

export interface ApproveMediaRowData {
    id: string | number;
    name: string;
    image: string | null;
    location: string;
    businessName: string;
    dailyImpressions: number;
    price: string;
    status?: MediaStatusEnum;
}

interface ApproveMediaTableProps {
    rows: ApproveMediaRowData[];
}

export function ApproveMediaTable({ rows }: ApproveMediaTableProps) {
    const t = useTranslations("admin.adminActions");
    const router = useRouter();
    const locale = useLocale();

    const base = `/${locale}/dashboard/admin/media/pending`;

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
                            <Table.Th w={80} miw={60}>
                                {t("image")}
                            </Table.Th>
                            <Table.Th miw={180}>{t("name")}</Table.Th>
                            <Table.Th miw={160}>{t("businessName")}</Table.Th>
                            <Table.Th miw={180}>{t("location")}</Table.Th>
                            <Table.Th miw={160}>{t("dailyImpressions")}</Table.Th>
                            <Table.Th w={110} miw={100}>
                                {t("price")}
                            </Table.Th>
                        </Table.Tr>
                    </Table.Thead>

                    <Table.Tbody>
                        {rows.length > 0 ? (
                            rows.map((row) => (
                                <Table.Tr
                                    key={row.id}
                                    onClick={() => router.push(`${base}/${row.id}`)}
                                    style={{ cursor: "pointer" }}
                                >
                                    <Table.Td>
                                        <Avatar src={row.image} alt={row.name} radius="md" />
                                    </Table.Td>
                                    <Table.Td>
                                        <Text fw={500}>{row.name}</Text>
                                    </Table.Td>
                                    <Table.Td>
                                        <Text size="sm">{row.businessName}</Text>
                                    </Table.Td>
                                    <Table.Td>
                                        <Text size="sm">{row.location}</Text>
                                    </Table.Td>
                                    <Table.Td>
                                        <Text size="sm">
                                            {row.dailyImpressions.toLocaleString()}
                                        </Text>
                                    </Table.Td>
                                    <Table.Td>
                                        <Text fw={600} c="teal">
                                            {row.price}
                                        </Text>
                                    </Table.Td>
                                </Table.Tr>
                            ))
                        ) : (
                            <Table.Tr>
                                <Table.Td colSpan={6}>
                                    <Text ta="center" c="dimmed" py="xl">
                                        {t("noMediaPending")}
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
