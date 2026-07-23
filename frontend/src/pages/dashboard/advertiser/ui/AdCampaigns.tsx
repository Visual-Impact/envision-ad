"use client";

import React, {useCallback, useEffect, useState} from "react";
import {Button, Group, SimpleGrid, Stack, Title} from "@mantine/core";
import {IconAd, IconMovie, IconPhoto, IconSpeakerphone} from "@tabler/icons-react";
import {notifications} from "@mantine/notifications";
import {useTranslations} from 'next-intl';

import {AdRequestDTO} from "@/entities/ad";
import {AdCampaign, AdCampaignRequestDTO} from "@/entities/ad-campaign";
import {
    addAdToCampaign,
    createAdCampaign, deleteAdCampaign,
    deleteAdFromCampaign,
    getAllAdCampaigns
} from "@/features/ad-campaign-management/api";
import {AdCampaignsTable} from "@/pages/dashboard/advertiser/ui/tables/AdCampaignsTable";
import {AddAdModal} from "@/pages/dashboard/advertiser/ui/modals/AddAdModal";
import {CreateCampaignModal} from "@/pages/dashboard/advertiser/ui/modals/CreateCampaignModal";
import {ConfirmationModal} from "@/shared/ui/ConfirmationModal";
import {MetricCard} from "@/widgets/Cards/MetricCard";
import {useOrganization} from "@/app/providers";

export default function AdCampaigns() {
    const t = useTranslations('adCampaigns');

    const [campaigns, setCampaigns] = useState<AdCampaign[]>([]);
    const [refreshCount, setRefreshCount] = useState(0);
    const { organization } = useOrganization();

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

    useEffect(() => {
        if (!organization) return;

        let ignored = false;

        const fetchCampaigns = async () => {
            try {
                const data = await getAllAdCampaigns(organization.businessId);
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
    }, [organization, t, refreshCount]);

    const handleOpenAddAd = (campaignId: string) => {
        setTargetCampaignId(campaignId);
        setIsAddAdModalOpen(true);
    };

    const handleSuccessAddAd = async (payload: AdRequestDTO) => {
        if (!targetCampaignId) return;

        try {
            await addAdToCampaign(organization!.businessId, targetCampaignId, payload);
            notifications.show({
                title: t('notifications.addAd.success.title'),
                message: t('notifications.addAd.success.message'),
                color: 'green'
            });
            setIsAddAdModalOpen(false);
            refreshCampaigns();
        } catch (error) {
            const err = error as { response?: { status?: number; data?: { message?: string } } };
            const status = err.response?.status;
            const serverMessage = err.response?.data?.message;

            let messageToShow: string;
            if (status === 409) {
                messageToShow = t('notifications.addAd.error.tiedReservationMessage');
            } else if (serverMessage) {
                messageToShow = serverMessage;
            } else {
                messageToShow = t('notifications.addAd.error.genericMessage');
            }

            notifications.show({
                title: t('notifications.addAd.error.title'),
                message: messageToShow,
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
        if (!adToDelete) return;

        try {
            await deleteAdFromCampaign(adToDelete.campaignId, adToDelete.adId);
            notifications.show({
                title: t('notifications.deleteAd.success.title'),
                message: t('notifications.deleteAd.success.message'),
                color: 'green'
            });
            refreshCampaigns();
            setConfirmDeleteAdOpen(false);
            setAdToDelete(null);
        } catch (error) {
            const err = error as { response?: { status?: number; data?: { message?: string } } };
            const status = err.response?.status;
            const serverMessage = err.response?.data?.message;

            let messageToShow: string;
            if (status === 409) {
                messageToShow = t('notifications.deleteAd.error.tiedReservationMessage');
            } else if (serverMessage) {
                messageToShow = serverMessage;
            } else {
                messageToShow = t('notifications.deleteAd.error.genericMessage');
            }

            notifications.show({
                title: t('notifications.deleteAd.error.title'),
                message: messageToShow,
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
            const err = error as { response?: { status?: number; data?: { message?: string } } };
            const status = err.response?.status;
            const serverMessage = err.response?.data?.message;

            let messageToShow: string;
            if (status === 409) {
                messageToShow = t('notifications.deleteCampaign.error.tiedReservationMessage');
            } else if (serverMessage) {
                messageToShow = serverMessage;
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
                onDeleteAd={handleDeleteAd}
                onDeleteAdCampaign={handleDeleteAdCampaign}
                onOpenAddAd={handleOpenAddAd}
            />

            <AddAdModal
                opened={isAddAdModalOpen}
                onClose={() => setIsAddAdModalOpen(false)}
                onSuccess={handleSuccessAddAd}
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