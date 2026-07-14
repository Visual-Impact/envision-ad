"use client";

import { ActionIcon, ColorSwatch, Group, Paper, ScrollArea, Table, Text } from "@mantine/core";
import { IconEdit, IconTrash } from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import { Venue } from "@/entities/venue";

interface VenueTableProps {
    venues: Venue[];
    onEdit: (venue: Venue) => void;
    onDelete: (venue: Venue) => void;
}

export function VenueTable({ venues, onEdit, onDelete }: VenueTableProps) {
    const t = useTranslations("venueManagement.table");

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
                            <Table.Th miw={150}>{t("nameEn")}</Table.Th>
                            <Table.Th miw={150}>{t("nameFr")}</Table.Th>
                            <Table.Th miw={80}>{t("mediaCount")}</Table.Th>
                            <Table.Th miw={100}>{t("actions")}</Table.Th>
                        </Table.Tr>
                    </Table.Thead>

                    <Table.Tbody>
                        {venues.length > 0 ? (
                            venues.map((venue) => (
                                <Table.Tr key={venue.venueId}>
                                    <Table.Td>
                                        <ColorSwatch color={venue.colorCode} size={24} />
                                    </Table.Td>
                                    <Table.Td>
                                        <Text fw={500}>{venue.nameEn}</Text>
                                    </Table.Td>
                                    <Table.Td>
                                        <Text>{venue.nameFr}</Text>
                                    </Table.Td>
                                    <Table.Td>
                                        <Text>{venue.mediaCount}</Text>
                                    </Table.Td>
                                    <Table.Td>
                                        <Group gap="xs">
                                            <ActionIcon
                                                variant="subtle"
                                                color="blue"
                                                onClick={() => onEdit(venue)}
                                            >
                                                <IconEdit size={18} stroke={1.5} />
                                            </ActionIcon>
                                            <ActionIcon
                                                variant="subtle"
                                                color="red"
                                                onClick={() => onDelete(venue)}
                                            >
                                                <IconTrash size={18} stroke={1.5} />
                                            </ActionIcon>
                                        </Group>
                                    </Table.Td>
                                </Table.Tr>
                            ))
                        ) : (
                            <Table.Tr>
                                <Table.Td colSpan={5}>
                                    <Text ta="center" c="dimmed" py="xl">
                                        {t("noVenues")}
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
