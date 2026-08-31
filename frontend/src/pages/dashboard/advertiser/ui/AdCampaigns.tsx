"use client";

import React, {useCallback, useEffect, useState} from "react";
import {Button, Group, SimpleGrid, Stack, Title} from "@mantine/core";
import {IconAd, IconMovie, IconPhoto, IconSpeakerphone} from "@tabler/icons-react";
import {notifications} from "@mantine/notifications";
import {useLocale, useTranslations} from 'next-intl';

import {Ad, AdRequestDTO} from "@/entities/ad";
import {AdCampaign, AdCampaignRequestDTO} from "@/entities/ad-campaign";
import {Venue} from "@/entities/venue";
import {
    addAdToCampaign,
    createAdCampaign, deleteAdCampaign,
    deleteAdFromCampaign,
    getAllAdCampaigns,
    updateAdVenueTags
} from "@/features/ad-campaign-management/api";
import {getAllVenues} from "@/features/venue-management/api";
import {AdCampaignsTable} from "@/pages/dashboard/advertiser/ui/tables/AdCampaignsTable";
import {AddAdModal} from "@/pages/dashboard/advertiser/ui/modals/AddAdModal";
import {EditAdVenueTagsModal} from "@/pages/dashboard/advertiser/ui/modals/EditAdVenueTagsModal";
import {CreateCampaignModal} from "@/pages/dashboard/advertiser/ui/modals/CreateCampaignModal";
import {ConfirmationModal} from "@/shared/ui/ConfirmationModal";
import {MetricCard} from "@/widgets/Cards/MetricCard";
import {useOrganization} from "@/app/providers";

export default function AdCampaigns() {
    const t = useTranslations('adCampaigns');
    const tEditTags = useTranslations('editAdVenueTags');
    const locale = useLocale();

    const [campaigns, setCampaigns] = useState<AdCampaign[]>([]);
    const [venues, setVenues] = useState<Venue[]>([]);
    const [refreshCount, setRefreshCount] = useState(0);
    const { organization } = useOrganization();

    const [isEditTagsModalOpen, setIsEditTagsModalOpen] = useState(false);
    const [adToEditTags, setAdToEditTags] = useState<{ campaignId: string; ad: Ad } | null>(null);

    const [isAddAdModalOpen, setIsAddAdModalOpen] = useState(false);
    const [targetCampaignId, setTargetCampaignId] = useState<string | null>(null);

    const [isCreateCampaignOpen, setIsCreateCampaignOpen] = useState(false);

    const [confirmDeleteAdOpen, setConfirmDeleteAdOpen] = useState(false);
    const [confirmDeleteCampaignOpen, setConfirmDeleteCampaignOpen] = useState(false);
    const [adToDelete, setAdToDelete] = useState<{ campaignId: string; adId: string } | null>(null);
    const [campaignIdToDelete, setCampaignIdToDelete] = useState<string | null>(null);

    const refreshCampaigns = useCallback(() => {
        setRefreshCount(c => c + 1);
    }, []);

    const businessId = organization?.businessId;

    useEffect(() => {
        if (!businessId) return;

        let ignored = false;

        const fetchCampaigns = async () => {
            try {
                const data = await getAllAdCampaigns(businessId);
                if (!ignored) setCampaigns(data);
            } catch (error) {
                if (!ignored) {
                    console.error('Failed to load campaigns', error);
                    notifications.show({
                        title: t('notifications.loadFailed.title'),
                        message: t('notifications.loadFailed.message'),
                        color: 'red'
                    });
                }
            }
        };

        void fetchCampaigns();

        return () => { ignored = true; };
    }, [businessId, t, refreshCount]);

    useEffect(() => {
        let ignored = false;

        // IIFE-nested per react-hooks/set-state-in-effect (see VenueMultiSelectPicker).
        (async () => {
            try {
                const data = await getAllVenues(locale);
                if (!ignored) setVenues(data);
            } catch (error) {
                // Non-fatal: the table falls back to rendering nothing for unresolved tags.
                console.error('Failed to load venues', error);
            }
        })();

        return () => { ignored = true; };
    }, [locale]);

    const handleOpenEditAdTags = (campaignId: string, ad: Ad) => {
        setAdToEditTags({ campaignId, ad });
        setIsEditTagsModalOpen(true);
    };

    const handleSaveAdVenueTags = async (venueIds: string[]) => {
        if (!adToEditTags || !organization) return;

        try {
            await updateAdVenueTags(
                organization.businessId,
                adToEditTags.campaignId,
                adToEditTags.ad.adId,
                venueIds
            );
            notifications.show({
                title: tEditTags('notifications.success.title'),
                message: tEditTags('notifications.success.message', { adName: adToEditTags.ad.name }),
                color: 'green'
            });
            setIsEditTagsModalOpen(false);
            setAdToEditTags(null);
            refreshCampaigns();
        } catch (error) {
            console.error('Failed to update venue tags', error);
            notifications.show({
                title: tEditTags('notifications.error.title'),
                message: tEditTags('notifications.error.genericMessage'),
                color: 'red'
            });
        }
    };

    const handleOpenAddAd = (campaignId: string) => {
        setTargetCampaignId(campaignId);
        setIsAddAdModalOpen(true);
    };

    const handleSuccessAddAd = async (payload: AdRequestDTO) => {
        if (!targetCampaignId || !organization) return;

        try {
            await addAdToCampaign(organization.businessId, targetCampaignId, payload);
            notifications.show({
                title: t('notifications.addAd.success.title'),
                message: t('notifications.addAd.success.message'),
                color: 'green'
            });
            setIsAddAdModalOpen(false);
            refreshCampaigns();
        } catch (error) {
            notifications.show({
                title: t('notifications.addAd.error.title'),
                message: t('notifications.addAd.error.genericMessage'),
                color: 'red'
            });

            throw error;
        }
    };

    const handleDeleteAd = (campaignId: string, adId: string) => {
        setAdToDelete({campaignId, adId});
        setConfirmDeleteAdOpen(true);
    };

    const handleDeleteAdCampaign = (campaignId: string) => {
        setCampaignIdToDelete(campaignId);
        setConfirmDeleteCampaignOpen(true);
    };

    const confirmDeleteAd = async () => {
        if (!adToDelete || !organization) return;

        try {
            await deleteAdFromCampaign(
                organization.businessId,
                adToDelete.campaignId,
                adToDelete.adId
            );
            notifications.show({
                title: t('notifications.deleteAd.success.title'),
                message: t('notifications.deleteAd.success.message'),
                color: 'green'
            });
            refreshCampaigns();
            setConfirmDeleteAdOpen(false);
            setAdToDelete(null);
        } catch (error) {
            console.error('Failed to delete ad', error);
            notifications.show({
                title: t('notifications.deleteAd.error.title'),
                message: t('notifications.deleteAd.error.genericMessage'),
                color: 'red'
            });
        }
    };

    const confirmDeleteCampaign = async () => {
        if (!campaignIdToDelete || !organization) return;

        try {
            await deleteAdCampaign(organization.businessId, campaignIdToDelete);
            notifications.show({
                title: t('notifications.deleteCampaign.success.title'),
                message: t('notifications.deleteCampaign.success.message'),
                color: 'green'
            });
            refreshCampaigns();
            setConfirmDeleteCampaignOpen(false);
            setCampaignIdToDelete(null);
        } catch (error) {
            console.error('Failed to delete campaign', error);
            const err = error as { response?: { status?: number } };
            const status = err.response?.status;

            let messageToShow: string;
            if (status === 409) {
                messageToShow = t('notifications.deleteCampaign.error.tiedReservationMessage');
            } else {
                messageToShow = t('notifications.deleteCampaign.error.genericMessage');
            }

            notifications.show({
                title: t('notifications.deleteCampaign.error.title'),
                message: messageToShow,
                color: 'red'
            });
        }
    };

    const handleCreateCampaign = async (payload: AdCampaignRequestDTO) => {
        if (!organization) return;

        try {
            await createAdCampaign(organization.businessId, payload);
            notifications.show({
                title: t('notifications.createCampaign.success.title'),
                message: t('notifications.createCampaign.success.message'),
                color: 'green'
            });
            setIsCreateCampaignOpen(false);
            refreshCampaigns();
        } catch (error) {
            console.error('Failed to create campaign', error);
            notifications.show({
                title: t('notifications.createCampaign.error.title'),
                message: t('notifications.createCampaign.error.message'),
                color: 'red'
            });
        }
    };

    const allAds = campaigns.flatMap((c) => c.ads);
    const imageAdsCount = allAds.filter((ad) => ad.adType === "IMAGE").length;
    const videoAdsCount = allAds.filter((ad) => ad.adType === "VIDEO").length;

    const stats = [
        { title: t('stats.totalCampaigns'), value: campaigns.length.toString(), icon: IconSpeakerphone, color: "blue" },
        { title: t('stats.totalAds'), value: allAds.length.toString(), icon: IconAd, color: "orange" },
        { title: t('stats.imageAds'), value: imageAdsCount.toString(), icon: IconPhoto, color: "teal" },
        { title: t('stats.videoAds'), value: videoAdsCount.toString(), icon: IconMovie, color: "grape" },
    ];

    return (
        <Stack gap="md" p="md">
            <Group justify="space-between">
                <Title order={1}>{t('page.title')}</Title>
                <Button variant="gradient" onClick={() => setIsCreateCampaignOpen(true)}>
                    {t('page.createButton')}
                </Button>
            </Group>

            <SimpleGrid cols={{ base: 1, sm: 2, lg: 4 }}>
                {stats.map((stat) => (
                    <MetricCard
                        key={stat.title}
                        label={stat.title}
                        value={stat.value}
                        color={stat.color}
                        icon={<stat.icon size="1.4rem" stroke={1.5} />}
                    />
                ))}
            </SimpleGrid>

            <AdCampaignsTable
                campaigns={campaigns}
                venues={venues}
                onDeleteAd={handleDeleteAd}
                onDeleteAdCampaign={handleDeleteAdCampaign}
                onOpenAddAd={handleOpenAddAd}
                onEditAdTags={handleOpenEditAdTags}
            />

            <AddAdModal
                opened={isAddAdModalOpen}
                onClose={() => setIsAddAdModalOpen(false)}
                onSuccess={handleSuccessAddAd}
            />

            <EditAdVenueTagsModal
                opened={isEditTagsModalOpen}
                onClose={() => setIsEditTagsModalOpen(false)}
                onSave={handleSaveAdVenueTags}
                ad={adToEditTags?.ad ?? null}
            />

            <CreateCampaignModal
                opened={isCreateCampaignOpen}
                onClose={() => setIsCreateCampaignOpen(false)}
                onSuccess={handleCreateCampaign}
            />

            <ConfirmationModal
                opened={confirmDeleteAdOpen}
                title={t('confirmations.deleteAd.title')}
                message={t('confirmations.deleteAd.message')}
                confirmLabel={t('confirmations.delete.confirm')}
                cancelLabel={t('confirmations.delete.cancel')}
                confirmColor="red"
                onConfirm={confirmDeleteAd}
                onCancel={() => setConfirmDeleteAdOpen(false)}
            />
            <ConfirmationModal
                opened={confirmDeleteCampaignOpen}
                title={t('confirmations.deleteCampaign.title')}
                message={t('confirmations.deleteCampaign.message')}
                confirmLabel={t('confirmations.delete.confirm')}
                cancelLabel={t('confirmations.delete.cancel')}
                confirmColor="red"
                onConfirm={confirmDeleteCampaign}
                onCancel={() => setConfirmDeleteCampaignOpen(false)}
            />
        </Stack>
    );
}
