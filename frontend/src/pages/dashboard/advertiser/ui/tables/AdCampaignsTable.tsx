import React from "react";
import { Accordion, ActionIcon, Button, Group, ScrollArea, Table, Text, Badge, Image, Box, Flex } from "@mantine/core";
import { IconTrash, IconPlus, IconPhoto, IconMovie, IconTags } from "@tabler/icons-react";
import { Ad } from "@/entities/ad";
import { AdCampaign } from "@/entities/ad-campaign";
import { Venue } from "@/entities/venue";
import {useLocale, useTranslations} from "next-intl";

interface AdCampaignsTableProps {
    campaigns: AdCampaign[];
    /** Full venue list, used to resolve an ad's venueIds to names and colors. */
    venues: Venue[];
    onDeleteAd: (campaignId: string, adId: string) => void;
    onDeleteAdCampaign: (campaignId: string) => void;
    onOpenAddAd: (campaignId: string) => void;
    onEditAdTags: (campaignId: string, ad: Ad) => void;
}

export function AdCampaignsTable({
    campaigns,
    venues,
    onDeleteAd,
    onDeleteAdCampaign,
    onOpenAddAd,
    onEditAdTags
}: AdCampaignsTableProps) {
    const t = useTranslations("adCampaigns.table");
    const locale = useLocale();
    const getIcon = (type: string) => type === "VIDEO" ? <IconMovie size={16} /> : <IconPhoto size={16} />;

    const renderVenueTags = (venueIds: string[]) => {
        // An untagged ad means "suitable for every venue", which is real information —
        // so it gets an explicit badge rather than a blank cell.
        if (venueIds.length === 0) {
            return <Badge color="gray" variant="light">{t("allVenues")}</Badge>;
        }

        return (
            <Group gap={4} wrap="wrap">
                {venueIds.map((venueId) => {
                    const venue = venues.find((v) => v.venueId === venueId);
                    if (!venue) return null;
                    return (
                        <Badge key={venueId} color={venue.colorCode} variant="light">
                            {locale === "fr" ? venue.nameFr : venue.nameEn}
                        </Badge>
                    );
                })}
            </Group>
        );
    };

    // Show empty state when there are no campaigns
    if (campaigns.length === 0) {
        return (
            <Box p="xl">
                <Text ta="center" c="dimmed" size="lg" fw={500}>
                    {t('noCampaigns')}
                </Text>
            </Box>
        );
    }

    return (
        <Accordion variant="separated" multiple>
            {campaigns.map((campaign) => (
                <Accordion.Item key={campaign.campaignId} value={campaign.campaignId}>
                    {/* FLEX container handles layout: Text/Control on left, Button on right */}
                    <Flex align="center" justify="space-between">
                        
                        {/* 1. The Accordion Trigger (Takes up remaining space) */}
                        <Accordion.Control style={{ flex: 1 }}>
                            <Text fw={500}>{campaign.name}</Text>
                        </Accordion.Control>

                        {/* 2. The Create Button (Rendered OUTSIDE the control) */}
                        <Group gap="xs" pr="md">
                            <Button
                                size="xs"
                                variant="light"
                                leftSection={<IconPlus size={14} />}
                                onClick={() => onOpenAddAd(campaign.campaignId)}
                            >
                                {t('newAd')}
                            </Button>
                            <ActionIcon
                                variant="subtle"
                                color="red"
                                aria-label={t('deleteCampaign')}
                                title={t('deleteCampaign')}
                                onClick={(e) => {
                                    e.stopPropagation();
                                    onDeleteAdCampaign(campaign.campaignId);
                                }}
                            >
                                <IconTrash size={16} />
                            </ActionIcon>
                        </Group>
                    </Flex>

                    <Accordion.Panel>
                        <ScrollArea>
                            <Table striped highlightOnHover verticalSpacing="sm">
                                <Table.Thead>
                                    <Table.Tr>
                                        <Table.Th>{t('preview')}</Table.Th>
                                        <Table.Th>{t('name')}</Table.Th>
                                        <Table.Th>{t('type')}</Table.Th>
                                        <Table.Th>{t('venueTags')}</Table.Th>
                                        <Table.Th style={{ textAlign: "right" }}>{t('actions')}</Table.Th>
                                    </Table.Tr>
                                </Table.Thead>
                                <Table.Tbody>
                                    {campaign.ads.length > 0 ? (
                                        campaign.ads.map((ad) => (
                                            <Table.Tr key={ad.adId}>
                                                <Table.Td width={150}>
                                                    <Box
                                                        w={120}
                                                        h={68}
                                                        style={{
                                                            overflow: 'hidden',
                                                            borderRadius: '8px',
                                                            border: '1px solid var(--mantine-color-gray-3)',
                                                            backgroundColor: 'var(--mantine-color-gray-1)'
                                                        }}
                                                    >
                                                        {ad.adType === 'VIDEO' ? (
                                                            <video
                                                                src={ad.adUrl}
                                                                style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                                                                muted
                                                                playsInline
                                                            />
                                                        ) : (
                                                            <Image
                                                                src={ad.adUrl}
                                                                w="100%"
                                                                h="100%"
                                                                fit="cover"
                                                                alt={ad.name}
                                                                fallbackSrc="https://placehold.co/120x68?text=No+Image"
                                                            />
                                                        )}
                                                    </Box>
                                                </Table.Td>
                                                <Table.Td style={{ verticalAlign: 'middle' }}>
                                                    <Text fw={500} size="sm">{ad.name}</Text>
                                                </Table.Td>
                                                <Table.Td style={{ verticalAlign: 'middle' }}>
                                                    <Badge leftSection={getIcon(ad.adType)} color={ad.adType === "VIDEO" ? "blue" : "green"} variant="light">
                                                        {t("types." + ad.adType.toLowerCase())}
                                                    </Badge>
                                                </Table.Td>
                                                <Table.Td style={{ verticalAlign: 'middle' }}>
                                                    {renderVenueTags(ad.venueIds)}
                                                </Table.Td>
                                                <Table.Td style={{ verticalAlign: 'middle' }}>
                                                    <Group justify="flex-end" gap="xs">
                                                        <ActionIcon
                                                            variant="subtle"
                                                            aria-label={t("editTags", { name: ad.name })}
                                                            title={t("editTags", { name: ad.name })}
                                                            onClick={() => onEditAdTags(campaign.campaignId, ad)}
                                                        >
                                                            <IconTags size={16} />
                                                        </ActionIcon>
                                                        <ActionIcon
                                                            color="red"
                                                            variant="subtle"
                                                            aria-label={t("deleteAd", { name: ad.name })}
                                                            title={t("deleteAd", { name: ad.name })}
                                                            onClick={() => onDeleteAd(campaign.campaignId, ad.adId)}
                                                        >
                                                            <IconTrash size={16} />
                                                        </ActionIcon>
                                                    </Group>
                                                </Table.Td>
                                            </Table.Tr>
                                        ))
                                    ) : (
                                        <Table.Tr>
                                            <Table.Td colSpan={5} align="center">
                                                <Text ta="center" c="dimmed" py="xl">
                                                    {t('noAds')}
                                                </Text>
                                            </Table.Td>
                                        </Table.Tr>
                                    )}
                                </Table.Tbody>
                            </Table>
                        </ScrollArea>
                    </Accordion.Panel>
                </Accordion.Item>
            ))}
        </Accordion>
    );
}
