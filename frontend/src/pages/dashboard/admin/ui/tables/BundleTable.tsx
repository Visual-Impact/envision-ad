"use client";

import { ActionIcon, Badge, ColorSwatch, Group, Paper, ScrollArea, Table, Text } from "@mantine/core";
import { IconEdit, IconFilterOff, IconTrash } from "@tabler/icons-react";
import { useTranslations, useLocale } from "next-intl";
import { Bundle } from "@/entities/bundle";
import { formatCurrency } from "@/shared/lib/formatCurrency";

interface BundleTableProps {
    bundles: Bundle[];
    onEdit: (bundle: Bundle) => void;
    onManageExclusions: (bundle: Bundle) => void;
    onDelete: (bundle: Bundle) => void;
}

export function BundleTable({ bundles, onEdit, onManageExclusions, onDelete }: BundleTableProps) {
    const t = useTranslations("bundleManagement.table");
    const locale = useLocale();

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
                            <Table.Th miw={60}>{t("color")}</Table.Th>
                            <Table.Th miw={150}>{t("name")}</Table.Th>
                            <Table.Th miw={140}>{t("rule")}</Table.Th>
                            <Table.Th miw={80}>{t("screens")}</Table.Th>
                            <Table.Th miw={110}>{t("monthlyPrice")}</Table.Th>
                            <Table.Th miw={90}>{t("discount")}</Table.Th>
                            <Table.Th miw={110}>{t("subscriptions")}</Table.Th>
                            <Table.Th miw={80}>{t("status")}</Table.Th>
                            <Table.Th miw={130}>{t("actions")}</Table.Th>
                        </Table.Tr>
                    </Table.Thead>

                    <Table.Tbody>
                        {bundles.length > 0 ? (
                            bundles.map((bundle) => (
                                <Table.Tr key={bundle.bundleId}>
                                    <Table.Td>
                                        <ColorSwatch color={bundle.badgeColor} size={24} />
                                    </Table.Td>
                                    <Table.Td>
                                        <Text fw={500}>
                                            {locale === "fr" ? bundle.nameFr : bundle.nameEn}
                                        </Text>
                                    </Table.Td>
                                    <Table.Td>
                                        <Group gap="xs">
                                            <Badge variant="light" size="sm">
                                                {t(`ruleTypes.${bundle.ruleType}`)}
                                            </Badge>
                                            {bundle.ruleValue && (
                                                <Text size="sm" c="dimmed">
                                                    {bundle.ruleValue}
                                                </Text>
                                            )}
                                        </Group>
                                    </Table.Td>
                                    <Table.Td>
                                        <Text>{bundle.screenCount}</Text>
                                    </Table.Td>
                                    <Table.Td>
                                        <Text>{formatCurrency(bundle.finalPrice ?? 0, { locale })}</Text>
                                    </Table.Td>
                                    <Table.Td>
                                        {bundle.discountPercent > 0 ? (
                                            <Badge color="red" variant="light" size="sm">
                                                −{bundle.discountPercent}%
                                            </Badge>
                                        ) : (
                                            <Text c="dimmed">—</Text>
                                        )}
                                    </Table.Td>
                                    <Table.Td>
                                        <Text>{bundle.activeSubscriptionCount}</Text>
                                    </Table.Td>
                                    <Table.Td>
                                        <Badge color={bundle.active ? "green" : "gray"} variant="light" size="sm">
                                            {bundle.active ? t("active") : t("inactive")}
                                        </Badge>
                                    </Table.Td>
                                    <Table.Td>
                                        <Group gap="xs">
                                            <ActionIcon
                                                variant="subtle"
                                                color="blue"
                                                onClick={() => onEdit(bundle)}
                                                aria-label={t("editAction")}
                                            >
                                                <IconEdit size={18} stroke={1.5} />
                                            </ActionIcon>
                                            <ActionIcon
                                                variant="subtle"
                                                color="grape"
                                                onClick={() => onManageExclusions(bundle)}
                                                aria-label={t("exclusionsAction")}
                                            >
                                                <IconFilterOff size={18} stroke={1.5} />
                                            </ActionIcon>
                                            <ActionIcon
                                                variant="subtle"
                                                color="red"
                                                onClick={() => onDelete(bundle)}
                                                aria-label={t("deleteAction")}
                                            >
                                                <IconTrash size={18} stroke={1.5} />
                                            </ActionIcon>
                                        </Group>
                                    </Table.Td>
                                </Table.Tr>
                            ))
                        ) : (
                            <Table.Tr>
                                <Table.Td colSpan={9}>
                                    <Text ta="center" c="dimmed" py="xl">
                                        {t("noBundles")}
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
