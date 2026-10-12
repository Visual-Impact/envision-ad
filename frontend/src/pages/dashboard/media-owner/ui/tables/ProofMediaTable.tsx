"use client";

import {
    ActionIcon,
    Badge,
    Button,
    Card,
    Group,
    Pagination,
    Paper,
    ScrollArea,
    Stack,
    Table,
    Text,
    Tooltip,
} from "@mantine/core";
import { useMediaQuery } from "@mantine/hooks";
import { IconExternalLink } from "@tabler/icons-react";
import { useMemo, useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import type { MediaOwnerProofSubscription } from "../../api";

type Props = {
    rows: MediaOwnerProofSubscription[];
    onEditLink: (row: MediaOwnerProofSubscription) => void;
    pageSize?: number;
};

/**
 * One row per subscription covering the owner's screens (P12 FR-3.1), identified by advertiser +
 * bundle (P12-PROGRESS D1). Multi-owner and ended subscriptions stay listed with the action
 * disabled and a tooltip saying why, so a real campaign never silently vanishes (FR-3.2).
 */
export default function ProofMediaTable({ rows, onEditLink, pageSize = 10 }: Props) {
    const isMobile = useMediaQuery("(max-width: 768px)");
    const t = useTranslations("proofOfDisplay");
    const locale = useLocale();

    // keep what the user clicked, but clamp what we actually use
    const [rawPage, setRawPage] = useState(1);

    const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
    const page = Math.min(rawPage, totalPages);

    const pagedRows = useMemo(() => {
        const start = (page - 1) * pageSize;
        return rows.slice(start, start + pageSize);
    }, [rows, page, pageSize]);

    const formatDate = (value: string) =>
        new Date(value).toLocaleDateString(locale === "fr" ? "fr-CA" : "en-CA", {
            year: "numeric",
            month: "long",
            day: "numeric",
        });

    const advertiserName = (row: MediaOwnerProofSubscription) =>
        row.advertiserBusinessName ?? t("table.advertiserUnknown");

    const bundleName = (row: MediaOwnerProofSubscription) =>
        (locale === "fr" ? row.bundleNameFr : row.bundleNameEn) ?? t("table.bundleUnavailable");

    const subscriptionCell = (row: MediaOwnerProofSubscription) => (
        <Stack gap={2}>
            <Group gap="xs" wrap="nowrap">
                <Text fw={600} lineClamp={1}>
                    {advertiserName(row)} · {bundleName(row)}
                </Text>
                {row.subscriptionStatus === "CANCELED" && (
                    <Badge color="gray" variant="light" size="sm">
                        {t("table.ended")}
                    </Badge>
                )}
            </Group>
            <Text size="sm" c="dimmed" lineClamp={1}>
                {row.campaignName ? t("table.campaign", { name: row.campaignName }) : t("table.noCampaign")}
            </Text>
        </Stack>
    );

    const statusCell = (row: MediaOwnerProofSubscription) => {
        if (row.proofLink.status === "NOT_APPLICABLE") {
            return (
                <Tooltip label={t("table.notAvailableTooltip")} multiline w={260} withArrow>
                    <Badge color="gray" variant="outline">
                        {t("table.notAvailable")}
                    </Badge>
                </Tooltip>
            );
        }
        if (row.proofLink.status === "NOT_SET" || !row.proofLink.url) {
            return (
                <Badge color="orange" variant="light">
                    {t("table.notSet")}
                </Badge>
            );
        }
        return (
            <Stack gap={2}>
                <Group gap={6} wrap="nowrap">
                    <Badge color="green" variant="light">
                        {t("table.set")}
                    </Badge>
                    <Tooltip label={t("table.openLink")} withArrow>
                        <ActionIcon
                            component="a"
                            href={row.proofLink.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            variant="subtle"
                            size="sm"
                            aria-label={t("table.openLink")}
                        >
                            <IconExternalLink size={16} />
                        </ActionIcon>
                    </Tooltip>
                </Group>
                {row.linkUpdatedAt && (
                    <Text size="xs" c="dimmed">
                        {t("table.lastUpdated", { date: formatDate(row.linkUpdatedAt) })}
                    </Text>
                )}
            </Stack>
        );
    };

    /**
     * A disabled Mantine Button swallows the hover its Tooltip needs, so the blocked cases use
     * `data-disabled` and ignore the click instead (Mantine's documented pattern).
     */
    const actionCell = (row: MediaOwnerProofSubscription) => {
        const label = row.proofLink.status === "SET" ? t("table.editLink") : t("table.addLink");
        const blockedReason =
            row.proofLink.status === "NOT_APPLICABLE"
                ? t("table.notAvailableTooltip")
                : row.subscriptionStatus === "CANCELED"
                  ? t("table.endedTooltip")
                  : null;

        if (blockedReason) {
            return (
                <Tooltip label={blockedReason} multiline w={260} withArrow>
                    <Button size="xs" variant="light" data-disabled onClick={(event) => event.preventDefault()}>
                        {row.proofLink.status === "NOT_APPLICABLE" ? t("table.notAvailable") : label}
                    </Button>
                </Tooltip>
            );
        }
        return (
            <Button size="xs" variant="light" onClick={() => onEditLink(row)}>
                {label}
            </Button>
        );
    };

    const pagination = rows.length > pageSize && (
        <Pagination
            total={totalPages}
            value={page}
            onChange={setRawPage}
            color="dark"
            variant="filled"
            getControlProps={(control) => {
                if (control === "previous") return { "aria-label": t("pagination.previous") };
                if (control === "next") return { "aria-label": t("pagination.next") };
                if (control === "first") return { "aria-label": t("pagination.first") };
                if (control === "last") return { "aria-label": t("pagination.last") };
                return {};
            }}
            getItemProps={(item) => ({
                "aria-label": t("pagination.page", { page: item }),
                "aria-current": item === page ? "page" : undefined,
            })}
        />
    );

    if (rows.length === 0) {
        return (
            <Paper shadow="sm" radius="lg" p="xl">
                <Text ta="center" c="dimmed">
                    {t("page.empty")}
                </Text>
            </Paper>
        );
    }

    if (isMobile) {
        return (
            <Stack gap="md">
                {pagedRows.map((row) => (
                    <Card key={row.subscriptionId} shadow="sm" radius="lg">
                        <Stack gap="sm">
                            {subscriptionCell(row)}
                            <Text size="sm" c="dimmed">
                                {row.screenNames.join(", ")}
                            </Text>
                            <Group justify="space-between" align="flex-start">
                                {statusCell(row)}
                                {actionCell(row)}
                            </Group>
                        </Stack>
                    </Card>
                ))}

                {pagination && <Group justify="center">{pagination}</Group>}
            </Stack>
        );
    }

    return (
        <Paper shadow="sm" radius="lg">
            <ScrollArea>
                <Table striped highlightOnHover verticalSpacing="md" horizontalSpacing="lg" layout="fixed">
                    <Table.Thead>
                        <Table.Tr>
                            <Table.Th>{t("table.subscription")}</Table.Th>
                            <Table.Th w={220}>{t("table.screens")}</Table.Th>
                            <Table.Th w={190}>{t("table.status")}</Table.Th>
                            <Table.Th w={160} ta="right">
                                {t("table.action")}
                            </Table.Th>
                        </Table.Tr>
                    </Table.Thead>

                    <Table.Tbody>
                        {pagedRows.map((row) => (
                            <Table.Tr key={row.subscriptionId}>
                                <Table.Td>{subscriptionCell(row)}</Table.Td>
                                <Table.Td w={220}>
                                    <Text size="sm" lineClamp={2}>
                                        {row.screenNames.join(", ")}
                                    </Text>
                                </Table.Td>
                                <Table.Td w={190}>{statusCell(row)}</Table.Td>
                                <Table.Td w={160} ta="right">
                                    {actionCell(row)}
                                </Table.Td>
                            </Table.Tr>
                        ))}
                    </Table.Tbody>
                </Table>
            </ScrollArea>

            {pagination && (
                <Group justify="flex-end" p="sm">
                    {pagination}
                </Group>
            )}
        </Paper>
    );
}
