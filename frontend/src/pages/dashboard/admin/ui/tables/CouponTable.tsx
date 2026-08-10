"use client";

import { ActionIcon, Badge, Group, Paper, ScrollArea, Switch, Table, Text } from "@mantine/core";
import { IconTrash } from "@tabler/icons-react";
import { useLocale, useTranslations } from "next-intl";
import { Coupon } from "@/entities/coupon";
import { formatCurrency } from "@/shared/lib/formatCurrency";

interface CouponTableProps {
    coupons: Coupon[];
    onToggleActive: (coupon: Coupon, active: boolean) => void;
    onDelete: (coupon: Coupon) => void;
}

export function CouponTable({ coupons, onToggleActive, onDelete }: CouponTableProps) {
    const t = useTranslations("couponManagement.table");
    const tDuration = useTranslations("couponManagement.duration");
    const locale = useLocale();

    const formatDiscount = (coupon: Coupon) =>
        coupon.discountType === "PERCENT"
            ? `${coupon.percentOff}%`
            : formatCurrency((coupon.amountOffCents ?? 0) / 100, { locale });

    const formatDuration = (coupon: Coupon) => {
        if (coupon.duration === "REPEATING") return tDuration("repeating", { months: coupon.durationInMonths ?? 0 });
        if (coupon.duration === "FOREVER") return tDuration("forever");
        return tDuration("once");
    };

    const formatExpires = (coupon: Coupon) =>
        coupon.expiresAt ? new Intl.DateTimeFormat(locale).format(new Date(coupon.expiresAt)) : t("noExpiry");

    const formatRedemptions = (coupon: Coupon) =>
        `${coupon.timesRedeemed} / ${coupon.maxRedemptions ?? "∞"}`;

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
                            <Table.Th miw={120}>{t("code")}</Table.Th>
                            <Table.Th miw={100}>{t("discount")}</Table.Th>
                            <Table.Th miw={100}>{t("duration")}</Table.Th>
                            <Table.Th miw={100}>{t("expires")}</Table.Th>
                            <Table.Th miw={100}>{t("redemptions")}</Table.Th>
                            <Table.Th miw={120}>{t("status")}</Table.Th>
                            <Table.Th miw={80}>{t("actions")}</Table.Th>
                        </Table.Tr>
                    </Table.Thead>

                    <Table.Tbody>
                        {coupons.length > 0 ? (
                            coupons.map((coupon) => (
                                <Table.Tr key={coupon.couponId}>
                                    <Table.Td>
                                        <Text fw={600} ff="monospace">{coupon.code}</Text>
                                    </Table.Td>
                                    <Table.Td>
                                        <Text>{formatDiscount(coupon)}</Text>
                                    </Table.Td>
                                    <Table.Td>
                                        <Text>{formatDuration(coupon)}</Text>
                                    </Table.Td>
                                    <Table.Td>
                                        <Text>{formatExpires(coupon)}</Text>
                                    </Table.Td>
                                    <Table.Td>
                                        <Text>{formatRedemptions(coupon)}</Text>
                                    </Table.Td>
                                    <Table.Td>
                                        {coupon.status === "ARCHIVED" ? (
                                            <Badge color="gray" variant="light" size="sm">
                                                {t("statusArchived")}
                                            </Badge>
                                        ) : (
                                            <Group gap="xs" wrap="nowrap">
                                                <Switch
                                                    checked={coupon.status === "ACTIVE"}
                                                    onChange={(e) => onToggleActive(coupon, e.currentTarget.checked)}
                                                    size="sm"
                                                />
                                                <Text size="sm" c={coupon.status === "ACTIVE" ? "green" : "dimmed"}>
                                                    {coupon.status === "ACTIVE" ? t("statusActive") : t("statusInactive")}
                                                </Text>
                                            </Group>
                                        )}
                                    </Table.Td>
                                    <Table.Td>
                                        {coupon.status !== "ARCHIVED" && (
                                            <ActionIcon
                                                variant="subtle"
                                                color="red"
                                                onClick={() => onDelete(coupon)}
                                                aria-label={t("actions")}
                                            >
                                                <IconTrash size={18} stroke={1.5} />
                                            </ActionIcon>
                                        )}
                                    </Table.Td>
                                </Table.Tr>
                            ))
                        ) : (
                            <Table.Tr>
                                <Table.Td colSpan={7}>
                                    <Text ta="center" c="dimmed" py="xl">
                                        {t("noCoupons")}
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
