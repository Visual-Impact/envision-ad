"use client";

import { ActionIcon, Badge, Group, Paper, ScrollArea, Table, Text, Tooltip } from "@mantine/core";
import { IconMailForward, IconPlayerPause, IconPlayerPlay } from "@tabler/icons-react";
import { useLocale, useTranslations } from "next-intl";
import { AccountListItem } from "@/entities/account";
import { Venue } from "@/entities/venue";

interface AccountsTableProps {
    accounts: AccountListItem[];
    venues: Venue[];
    onResend: (account: AccountListItem) => void;
    onToggleActive: (account: AccountListItem) => void;
    pendingBusinessId: string | null;
}

export function AccountsTable({ accounts, venues, onResend, onToggleActive, pendingBusinessId }: AccountsTableProps) {
    const t = useTranslations("accountManagement");
    // Reuses the existing organization.roles.* keys rather than duplicating "Advertiser"/
    // "Media Owner" strings under a new namespace.
    const tRoles = useTranslations("organization.roles");
    const locale = useLocale();

    const venueName = (venueId: string | null) => {
        if (!venueId) return null;
        const venue = venues.find((v) => v.venueId === venueId);
        if (!venue) return null;
        return locale === "fr" ? venue.nameFr : venue.nameEn;
    };

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
                            <Table.Th miw={160}>{t("table.business")}</Table.Th>
                            <Table.Th miw={180}>{t("table.owner")}</Table.Th>
                            <Table.Th miw={140}>{t("table.roles")}</Table.Th>
                            <Table.Th miw={120}>{t("table.businessType")}</Table.Th>
                            <Table.Th miw={90}>{t("table.status")}</Table.Th>
                            <Table.Th miw={100}>{t("table.dateCreated")}</Table.Th>
                            <Table.Th miw={100}>{t("table.actions")}</Table.Th>
                        </Table.Tr>
                    </Table.Thead>

                    <Table.Tbody>
                        {accounts.length > 0 ? (
                            accounts.map((account) => {
                                const type = venueName(account.businessTypeVenueId);
                                const isPending = pendingBusinessId === account.businessId;
                                return (
                                    <Table.Tr key={account.businessId}>
                                        <Table.Td>
                                            <Text fw={500}>{account.name}</Text>
                                        </Table.Td>
                                        <Table.Td>
                                            <Text c={account.ownerEmail ? undefined : "dimmed"}>
                                                {account.ownerEmail ?? "—"}
                                            </Text>
                                        </Table.Td>
                                        <Table.Td>
                                            <Group gap={4}>
                                                {account.roles.advertiser && (
                                                    <Badge color="orange" variant="light" size="sm">
                                                        {tRoles("advertiser")}
                                                    </Badge>
                                                )}
                                                {account.roles.mediaOwner && (
                                                    <Badge color="violet" variant="light" size="sm">
                                                        {tRoles("mediaOwner")}
                                                    </Badge>
                                                )}
                                            </Group>
                                        </Table.Td>
                                        <Table.Td>
                                            {type ? (
                                                <Badge color="gray" variant="light" size="sm">
                                                    {type}
                                                </Badge>
                                            ) : (
                                                <Text c="dimmed">—</Text>
                                            )}
                                        </Table.Td>
                                        <Table.Td>
                                            <Badge color={account.active ? "green" : "red"} variant="light">
                                                {account.active ? t("status.active") : t("status.inactive")}
                                            </Badge>
                                        </Table.Td>
                                        <Table.Td>
                                            <Text>{new Date(account.dateCreated).toLocaleDateString()}</Text>
                                        </Table.Td>
                                        <Table.Td>
                                            <Group gap="xs">
                                                <Tooltip label={t("actions.resend")}>
                                                    <ActionIcon
                                                        variant="subtle"
                                                        color="blue"
                                                        loading={isPending}
                                                        onClick={() => onResend(account)}
                                                    >
                                                        <IconMailForward size={18} stroke={1.5} />
                                                    </ActionIcon>
                                                </Tooltip>
                                                <Tooltip label={account.active ? t("actions.deactivate") : t("actions.reactivate")}>
                                                    <ActionIcon
                                                        variant="subtle"
                                                        color={account.active ? "red" : "green"}
                                                        loading={isPending}
                                                        onClick={() => onToggleActive(account)}
                                                    >
                                                        {account.active
                                                            ? <IconPlayerPause size={18} stroke={1.5} />
                                                            : <IconPlayerPlay size={18} stroke={1.5} />}
                                                    </ActionIcon>
                                                </Tooltip>
                                            </Group>
                                        </Table.Td>
                                    </Table.Tr>
                                );
                            })
                        ) : (
                            <Table.Tr>
                                <Table.Td colSpan={7}>
                                    <Text ta="center" c="dimmed" py="xl">
                                        {t("table.noAccounts")}
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
