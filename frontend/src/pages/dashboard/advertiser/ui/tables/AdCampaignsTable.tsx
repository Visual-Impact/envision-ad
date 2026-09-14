import React from "react";
import { Accordion, ActionIcon, Alert, Button, Group, ScrollArea, Table, Text, Badge, Box, Flex, Tooltip } from "@mantine/core";
import { IconTrash, IconPlus, IconPhoto, IconMovie, IconTags, IconBell, IconArchive, IconArchiveOff } from "@tabler/icons-react";
import { Ad } from "@/entities/ad";
import { AdCampaign } from "@/entities/ad-campaign";
import { Venue } from "@/entities/venue";
import {useLocale, useTranslations} from "next-intl";
import { AdThumbnail } from "../AdThumbnail";

interface AdCampaignsTableProps {
    campaigns: AdCampaign[];
    /** Full venue list, used to resolve an ad's venueIds to names and colors. */
    venues: Venue[];
    /** The business's active campaign: badged, and never deletable or archivable (FR-3.1, FR-3b.3). */
    activeCampaignId: string | null;
    /** The campaign whose "notify owners now?" prompt is showing, if any (FR-8.3). */
    promptCampaignId: string | null;
    notifying: boolean;
    notifyDisabled: boolean;
    onNotifyNow: () => void;
    onDismissPrompt: () => void;
    onDeleteAd: (campaignId: string, adId: string) => void;
    onDeleteAdCampaign: (campaignId: string) => void;
    onArchiveAdCampaign: (campaignId: string) => void;
    onUnarchiveAdCampaign: (campaignId: string) => void;
    onOpenAddAd: (campaignId: string) => void;
    onEditAdTags: (campaignId: string, ad: Ad) => void;
}

export function AdCampaignsTable({
    campaigns,
    venues,
    activeCampaignId,
    promptCampaignId,
    notifying,
    notifyDisabled,
    onNotifyNow,
    onDismissPrompt,
    onDeleteAd,
    onDeleteAdCampaign,
    onArchiveAdCampaign,
    onUnarchiveAdCampaign,
    onOpenAddAd,
    onEditAdTags
}: AdCampaignsTableProps) {
    const t = useTranslations("adCampaigns.table");
    const tPrompt = useTranslations("activeCampaignSlot.prompt");
    const tArchive = useTranslations("adCampaigns.archive");
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
            {campaigns.map((campaign) => {
                const isActive = campaign.campaignId === activeCampaignId;
                const isArchived = campaign.archivedAt !== null;
                return (
                <Accordion.Item key={campaign.campaignId} value={campaign.campaignId}>
                    {/* FLEX container handles layout: Text/Control on left, Button on right */}
                    <Flex align="center" justify="space-between">
                        
                        {/* 1. The Accordion Trigger (Takes up remaining space) */}
                        <Accordion.Control style={{ flex: 1, opacity: isArchived ? 0.6 : undefined }}>
                            <Group gap="xs" wrap="nowrap">
                                <Text fw={500}>{campaign.name}</Text>
                                {/* It is also in the slot above; the badge says that is on purpose (FR-1.5). */}
                                {isActive && <Badge color="teal" variant="light" size="sm">{t("activeBadge")}</Badge>}
                                {isArchived && <Badge color="gray" variant="light" size="sm">{tArchive("badge")}</Badge>}
                            </Group>
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
                            {/* The active campaign can be neither archived nor deleted (D20). Offering an
                                action that is guaranteed to fail is worse than explaining why it isn't
                                available. data-disabled keeps the tooltip working. */}
                            <Tooltip
                                label={isActive
                                    ? t("activeCampaignActionsHint")
                                    : tArchive(isArchived ? "unarchiveAction" : "action")}
                                multiline
                                w={isActive ? 260 : undefined}
                            >
                                <ActionIcon
                                    variant="subtle"
                                    color="gray"
                                    aria-label={tArchive(isArchived ? "unarchiveAction" : "action")}
                                    data-disabled={isActive || undefined}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        if (isActive) return;
                                        if (isArchived) onUnarchiveAdCampaign(campaign.campaignId);
                                        else onArchiveAdCampaign(campaign.campaignId);
                                    }}
                                >
                                    {isArchived ? <IconArchiveOff size={16} /> : <IconArchive size={16} />}
                                </ActionIcon>
                            </Tooltip>
                            <Tooltip label={t("activeCampaignActionsHint")} disabled={!isActive} multiline w={260}>
                                <ActionIcon
                                    variant="subtle"
                                    color="red"
                                    aria-label={t('deleteCampaign')}
                                    data-disabled={isActive || undefined}
                                    onClick={(e) => {
                                        e.stopPropagation();
                                        if (!isActive) onDeleteAdCampaign(campaign.campaignId);
                                    }}
                                >
                                    <IconTrash size={16} />
                                </ActionIcon>
                            </Tooltip>
                        </Group>
                    </Flex>

                    {promptCampaignId === campaign.campaignId && (
                        // Inline and dismissible, never a modal: an advertiser adding several
                        // creatives in a row must be able to carry on (FR-8.1).
                        <Alert mx="md" mb="sm" color="orange" variant="light" icon={<IconBell size={16} />}>
                            <Group justify="space-between" gap="sm" wrap="wrap">
                                <Text size="sm">{tPrompt("message")}</Text>
                                <Group gap="xs">
                                    <Button size="xs" color="orange" onClick={onNotifyNow} loading={notifying} disabled={notifyDisabled}>
                                        {tPrompt("notifyNow")}
                                    </Button>
                                    <Button size="xs" variant="subtle" color="gray" onClick={onDismissPrompt}>
                                        {tPrompt("later")}
                                    </Button>
                                </Group>
                            </Group>
                        </Alert>
                    )}

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
                                                    <AdThumbnail ad={ad} />
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
                );
            })}
        </Accordion>
    );
}
