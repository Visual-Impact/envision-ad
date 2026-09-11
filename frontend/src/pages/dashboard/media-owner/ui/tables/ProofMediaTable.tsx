"use client";

import type { MediaRowData } from "./MediaRow";
import {
    Paper,
    ScrollArea,
    Table,
    Text,
    Button,
    Avatar,
    Group,
    Card,
    Stack,
    Pagination,
} from "@mantine/core";
import { useMediaQuery } from "@mantine/hooks";
import { useEffect, useMemo, useState } from "react";
import { useTranslations } from "next-intl";

type Props = {
    rows: MediaRowData[];
    onAddProof: (row: MediaRowData) => void;
    activeAdsCounts?: Record<string, number>;
    onVisibleRowsChange?: (ids: string[]) => void;
    pageSize?: number;
};

export default function ProofMediaTable({
                                            rows,
                                            onAddProof,
                                            activeAdsCounts = {},
                                            onVisibleRowsChange,
                                            pageSize = 10,
                                        }: Props) {
    const isMobile = useMediaQuery("(max-width: 768px)");
    const t = useTranslations("proofOfDisplay");

    // keep what the user clicked, but clamp what we actually use
    const [rawPage, setRawPage] = useState(1);

    const totalPages = Math.max(1, Math.ceil(rows.length / pageSize));
    const page = Math.min(rawPage, totalPages);

    const pagedRows = useMemo(() => {
        const start = (page - 1) * pageSize;
        return rows.slice(start, start + pageSize);
    }, [rows, page, pageSize]);

    // notify parent: only fetch counts for what is visible
    useEffect(() => {
        if (!onVisibleRowsChange) return;
        onVisibleRowsChange(pagedRows.map((r) => String(r.id)));
    }, [pagedRows, onVisibleRowsChange]);

    const getDisplayedCount = (row: MediaRowData) => {
        const id = String(row.id);
        return activeAdsCounts[id] ?? 0;
    };

    if (isMobile) {
        return (
            <Stack gap="md">
                {pagedRows.length ? (
                    pagedRows.map((row) => (
                        <Card key={String(row.id)} shadow="sm" radius="lg">
                            <Group justify="space-between" align="center" mb="sm">
                                <Group gap="sm">
                                    <Avatar src={row.image} alt={row.name} size="md" radius="md" />
                                    <Text fw={500} lineClamp={1}>
                                        {row.name}
                                    </Text>
                                </Group>

                                <Button size="xs" variant="light" onClick={() => onAddProof(row)} disabled={getDisplayedCount(row) === 0}>
                                    {t("table.addProof")}
                                </Button>
                            </Group>

                            <Group grow>
                                <Stack gap={0} align="center">
                                    <Text size="xs" c="dimmed">
                                        {t("table.displayed")}
                                    </Text>
                                    <Text fw={700} c="blue.9">
                                        {getDisplayedCount(row)}
                                    </Text>
                                </Stack>

                                <Stack gap={0} align="center">
                                    <Text size="xs" c="dimmed">
                                        {t("table.pending")}
                                    </Text>
                                    <Text fw={700} c="dark.9">
                                        {row.pending}
                                    </Text>
                                </Stack>
                            </Group>
                        </Card>
                    ))
                ) : (
                    <Text ta="center" c="dimmed" py="xl">
                        {t("table.noMedia")}
                    </Text>
                )}

                {rows.length > pageSize && (
                    <Group justify="center">
                        <Pagination total={totalPages} value={page} onChange={setRawPage} />
                    </Group>
                )}
            </Stack>
        );
    }

    return (
        <Paper shadow="sm" radius="lg">
            <ScrollArea>
                <Table striped highlightOnHover verticalSpacing="md" horizontalSpacing="lg" layout="fixed">
                    <Table.Thead>
                        <Table.Tr>
                            <Table.Th w={80}>{t("table.image")}</Table.Th>
                            <Table.Th>{t("table.name")}</Table.Th>
                            <Table.Th w={140} ta="center">
                                {t("table.adsDisplayed")}
                            </Table.Th>
                            <Table.Th w={120} ta="center">
                                {t("table.pending")}
                            </Table.Th>
                            <Table.Th w={160} ta="right">
                                {t("table.action")}
                            </Table.Th>
                        </Table.Tr>
                    </Table.Thead>

                    <Table.Tbody>
                        {pagedRows.length ? (
                            pagedRows.map((row) => (
                                <Table.Tr key={String(row.id)}>
                                    <Table.Td w={80}>
                                        <Avatar src={row.image} alt={row.name} size="md" radius="md" />
                                    </Table.Td>

                                    <Table.Td>
                                        <Text fw={500} lineClamp={1}>
                                            {row.name}
                                        </Text>
                                    </Table.Td>

                                    <Table.Td w={140} ta="center">
                                        <Text fw={700} c="blue">
                                            {getDisplayedCount(row)}
                                        </Text>
                                    </Table.Td>

                                    <Table.Td w={120} ta="center">
                                        <Text fw={700} c="dark.9">
                                            {row.pending}
                                        </Text>
                                    </Table.Td>

                                    <Table.Td w={160} ta="right">
                                        <Button size="xs" variant="light" onClick={() => onAddProof(row)} disabled={getDisplayedCount(row) === 0}>

                                            {t("table.addProof")}
                                        </Button>
                                    </Table.Td>
                                </Table.Tr>
                            ))
                        ) : (
                            <Table.Tr>
                                <Table.Td colSpan={5}>
                                    <Text ta="center" c="dimmed" py="xl">
                                        {t("table.noMedia")}
                                    </Text>
                                </Table.Td>
                            </Table.Tr>
                        )}
                    </Table.Tbody>
                </Table>
            </ScrollArea>

            {rows.length > pageSize && (
                <Group justify="flex-end" p="sm">
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
                </Group>
            )}
        </Paper>
    );
}
