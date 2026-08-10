import React from "react";
import { Accordion, ActionIcon, Button, Group, Table, Text, Box, Flex, Tooltip, VisuallyHidden } from "@mantine/core";
import { IconTrash, IconPlus, IconMapPin } from "@tabler/icons-react";
import { MediaLocation } from "@/entities/media-location/model/mediaLocation";
import { useTranslations } from "next-intl";

import { MediaRow } from "@/pages/dashboard/media-owner/ui/tables/MediaRow";
import {MediaStatusEnum} from "@/entities/media/model/media";

interface MediaLocationsTableProps {
    locations: MediaLocation[];
    onDeleteLocation: (id: string) => void;
    onAddMedia: (locationId: string) => void;
    onEditLocation: (location: MediaLocation) => void;
    onEditMedia: (id: string | number) => void;
    onDeleteMedia: (id: string | number) => void;
    onToggleMediaStatus: (
        id: string | number,
        nextStatus: MediaStatusEnum.ACTIVE | MediaStatusEnum.INACTIVE
    ) => void | Promise<void>;
    addMediaDisabled?: boolean;
}

export function MediaLocationsTable({
    locations,
    onDeleteLocation,
    onAddMedia,
    onEditLocation,
    onEditMedia,
    onDeleteMedia,
    onToggleMediaStatus,
    addMediaDisabled = false
}: MediaLocationsTableProps) {
    const t = useTranslations("mediaLocations.table");
    const tMedia = useTranslations("media.table");
    const tStripe = useTranslations("media.stripe");

    if (locations.length === 0) {
        return (
            <Box p="xl">
                <Text ta="center" c="dimmed" size="lg" fw={500}>
                    {t('noLocations')}
                </Text>
            </Box>
        );
    }

    return (
        <Accordion variant="separated" multiple>
            {locations.map((location) => (
                <Accordion.Item key={location.id} value={location.id}>
                    <Flex align="center" justify="space-between" pr="md">
                        <Box style={{ flex: 1 }}>
                            <Accordion.Control>
                                <Group>
                                    <IconMapPin size={20} />
                                    <Text fw={500}>{location.name}</Text>
                                    <Text size="sm" c="dimmed">{location.city}, {location.province}</Text>
                                </Group>
                            </Accordion.Control>
                        </Box>

                        <Group gap="xs" ml="md">
                            <Tooltip label={tStripe('requiredMessage')} disabled={!addMediaDisabled} multiline w={260}>
                                <Button
                                    size="xs"
                                    variant="light"
                                    leftSection={<IconPlus size={14} />}
                                    disabled={addMediaDisabled}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        onAddMedia(location.id);
                                    }}
                                >
                                    {t('addMedia')}
                                </Button>
                            </Tooltip>
                            <Button
                                size="xs"
                                variant="subtle"
                                onClick={(e) => {
                                    e.stopPropagation();
                                    onEditLocation(location);
                                }}
                            >
                                <Text size="sm">{t('edit')}</Text>
                            </Button>
                            <ActionIcon
                                variant="subtle"
                                color="red"
                                aria-label={t("deleteLocationAria", { name: location.name })}
                                title={t("deleteLocationAria", { name: location.name })}
                                onClick={(e) => {
                                    e.stopPropagation();
                                    onDeleteLocation(location.id);
                                }}
                            >
                                <IconTrash size={16} />
                            </ActionIcon>
                        </Group>
                    </Flex>

                    <Accordion.Panel>

                        <Text size="sm" c="dimmed">{location.street}, {location.postalCode}, {location.country}</Text>

                        <Text size="sm" mt="md" fw={500} mb="xs">{t('assignedMedia')}:</Text>
                        {location.mediaList && location.mediaList.length > 0 ? (
                            <Box mt="xs" style={{ overflowX: 'auto' }}>
                                <Table striped highlightOnHover layout="auto" style={{ minWidth: "700px" }}>
                                    <Table.Thead>
                                        <Table.Tr>
                                            <Table.Th w={60}>{tMedia("image")}</Table.Th>
                                            <Table.Th miw={120}> {tMedia("name")}</Table.Th>
                                            <Table.Th w={120} ta="right">{tMedia("displayed")}</Table.Th>
                                            <Table.Th w={120} ta="right">{tMedia("pending")}</Table.Th>
                                            <Table.Th w={120} ta="right">{tMedia("status")}</Table.Th>
                                            <Table.Th w={120} ta="right">{tMedia("update")}</Table.Th>
                                            <Table.Th w={140} ta="right">{tMedia("price")}</Table.Th>
                                            <Table.Th miw={100}>{tMedia("venue")}</Table.Th>
                                            <Table.Th w={60} ta="right">
                                                <VisuallyHidden>{tMedia("actions")}</VisuallyHidden>
                                            </Table.Th>
                                        </Table.Tr>
                                    </Table.Thead>
                                    <Table.Tbody>
                                        {(location.mediaList || []).map((media) => (
                                            <MediaRow
                                                key={media.id}
                                                row={{
                                                    id: media.id ?? "",
                                                    name: media.title,
                                                    image: media.imageUrl,
                                                    adsDisplayed: 0, // Placeholder as backend data might be missing this
                                                    pending: 0,      // Placeholder
                                                    status: media.status ?? MediaStatusEnum.ACTIVE,
                                                    timeUntil: "-",  // Placeholder
                                                    price: media.price?.toString() ?? "0.00",
                                                    venue: media.venue ?? null
                                                }}
                                                onEdit={onEditMedia}
                                                onDelete={onDeleteMedia}
                                                onToggleStatus={onToggleMediaStatus}
                                            />
                                        ))}
                                    </Table.Tbody>
                                </Table>
                            </Box>
                        ) : (
                            <Text size="sm" c="dimmed" fs="italic">{t('noAssignedMedia')}</Text>
                        )}
                    </Accordion.Panel>
                </Accordion.Item>
            ))}
        </Accordion>
    );
}
