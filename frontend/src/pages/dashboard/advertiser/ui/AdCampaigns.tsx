"use client";

import React, {useCallback, useEffect, useState} from "react";
import axios from "axios";
import {Button, Group, SimpleGrid, Stack, Switch, Title} from "@mantine/core";
import {IconAd, IconMovie, IconPhoto, IconSpeakerphone} from "@tabler/icons-react";
import {notifications} from "@mantine/notifications";
import {useLocale, useTranslations} from 'next-intl';

import {Ad, AdRequestDTO} from "@/entities/ad";
import {ActiveCampaignSummary, AdCampaign, AdCampaignRequestDTO} from "@/entities/ad-campaign";
import {Venue} from "@/entities/venue";
import {
    addAdToCampaign,
    archiveCampaign,
    createAdCampaign, deleteAdCampaign,
    deleteAdFromCampaign,
    getActiveCampaign,
    getAllAdCampaigns,
    notifyMediaOwners,
    unarchiveCampaign,
    updateAdVenueTags
} from "@/features/ad-campaign-management";
import {getBundleSubscriptions} from "@/features/bundle-subscription";
import {getAllVenues} from "@/features/venue-management";
import {AdCampaignsTable} from "@/pages/dashboard/advertiser/ui/tables/AdCampaignsTable";
import {AddAdModal} from "@/pages/dashboard/advertiser/ui/modals/AddAdModal";
import {EditAdVenueTagsModal} from "@/pages/dashboard/advertiser/ui/modals/EditAdVenueTagsModal";
import {CreateCampaignModal} from "@/pages/dashboard/advertiser/ui/modals/CreateCampaignModal";
import {SwapCampaignModal} from "@/pages/dashboard/advertiser/ui/modals/SwapCampaignModal";
import {ActiveCampaignSlot, ActiveCampaignSlotState} from "@/pages/dashboard/advertiser/ui/ActiveCampaignSlot";
import {useCooldownSeconds} from "@/pages/dashboard/advertiser/model/useCooldownSeconds";
import {SUPPORT_EMAIL} from "@/shared/config";
import {ConfirmationModal} from "@/shared/ui";
import {MetricCard} from "@/shared/ui";
import { useOrganization } from "@/entities/organization";

interface ActiveCampaignState {
    loading: boolean;
    summary: ActiveCampaignSummary | null;
    hasLiveSubscription: boolean;
}

/** A 429 from swap or notify, which carries how long the shared cooldown still has to run. */
function retryAfterSecondsOf(error: unknown): number | null {
    if (!axios.isAxiosError(error)) return null;
    const data = error.response?.data;
    return data?.code === "SWAP_DEBOUNCED" && typeof data.retryAfterSeconds === "number"
        ? data.retryAfterSeconds
        : null;
}

export default function AdCampaigns() {
    const t = useTranslations('adCampaigns');
    const tSlot = useTranslations('activeCampaignSlot');
    const tEditTags = useTranslations('editAdVenueTags');
    const tArchive = useTranslations('adCampaigns.archive');
    const locale = useLocale();

    const [campaigns, setCampaigns] = useState<AdCampaign[]>([]);
    const [venues, setVenues] = useState<Venue[]>([]);
    const [refreshCount, setRefreshCount] = useState(0);
    const { organization } = useOrganization();

    const [activeCampaign, setActiveCampaign] = useState<ActiveCampaignState>(
        { loading: true, summary: null, hasLiveSubscription: false });
    const [cooldownUntil, setCooldownUntil] = useState<number | null>(null);
    const cooldownSeconds = useCooldownSeconds(cooldownUntil);
    const [notifying, setNotifying] = useState(false);
    const [isSwapModalOpen, setIsSwapModalOpen] = useState(false);
    /** The campaign an ad was just added to or removed from, while owners haven't been told. */
    const [promptCampaignId, setPromptCampaignId] = useState<string | null>(null);

    const [isEditTagsModalOpen, setIsEditTagsModalOpen] = useState(false);
    const [adToEditTags, setAdToEditTags] = useState<{ campaignId: string; ad: Ad } | null>(null);

    const [isAddAdModalOpen, setIsAddAdModalOpen] = useState(false);
    const [targetCampaignId, setTargetCampaignId] = useState<string | null>(null);

    const [isCreateCampaignOpen, setIsCreateCampaignOpen] = useState(false);

    const [confirmDeleteAdOpen, setConfirmDeleteAdOpen] = useState(false);
    const [confirmDeleteCampaignOpen, setConfirmDeleteCampaignOpen] = useState(false);
    const [adToDelete, setAdToDelete] = useState<{ campaignId: string; adId: string } | null>(null);
    const [campaignIdToDelete, setCampaignIdToDelete] = useState<string | null>(null);

    /** Off by default: archiving exists to tidy the list (FR-3b.4). */
    const [showArchived, setShowArchived] = useState(false);
    const [campaignToArchive, setCampaignToArchive] = useState<AdCampaign | null>(null);

    const refreshCampaigns = useCallback(() => {
        setRefreshCount(c => c + 1);
    }, []);

    const businessId = organization?.businessId;

    useEffect(() => {
        if (!businessId) return;

        let ignored = false;

        const fetchCampaigns = async () => {
            try {
                const data = await getAllAdCampaigns(businessId, showArchived);
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
    }, [businessId, t, refreshCount, showArchived]);

    useEffect(() => {
        if (!businessId) return;

        let ignored = false;

        // IIFE-nested per react-hooks/set-state-in-effect (see VenueMultiSelectPicker).
        (async () => {
            try {
                // The subscriptions decide whether the slot exists at all (FR-1.3), and tell a
                // business with nothing on screen yet apart from inconsistent data (FR-1.4).
                const [summary, subscriptions] = await Promise.all([
                    getActiveCampaign(businessId),
                    getBundleSubscriptions(businessId),
                ]);
                if (ignored) return;
                setActiveCampaign({
                    loading: false,
                    summary,
                    hasLiveSubscription: subscriptions.some((s) => s.status === "ACTIVE" || s.status === "PAST_DUE"),
                });
                setCooldownUntil(summary?.swapAvailableAt ? Date.parse(summary.swapAvailableAt) : null);
            } catch (error) {
                console.error('Failed to load the active campaign', error);
                // The campaigns list still works; hide the slot rather than show a wrong one.
                if (!ignored) setActiveCampaign({ loading: false, summary: null, hasLiveSubscription: false });
            }
        })();

        return () => { ignored = true; };
    }, [businessId, refreshCount]);

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

    const slotState: ActiveCampaignSlotState = activeCampaign.loading
        ? { kind: "loading" }
        : !activeCampaign.hasLiveSubscription
            ? { kind: "hidden" }
            : activeCampaign.summary
                ? { kind: "ready", summary: activeCampaign.summary }
                : { kind: "missing" };
    /** On screen right now — the campaign whose edits owners need to hear about. */
    const displayedCampaignId = slotState.kind === "ready" ? slotState.summary.campaignId : null;
    /** Set even without a live subscription: the active campaign is never deletable (FR-3.1). */
    const activeCampaignId = activeCampaign.summary?.campaignId ?? null;

    const handleNotify = async () => {
        if (!businessId) return;

        setNotifying(true);
        try {
            const result = await notifyMediaOwners(businessId);
            if (result.failedCount > 0) {
                notifications.show({
                    title: tSlot('notifications.notify.partial.title'),
                    message: tSlot('notifications.notify.partial.message',
                        { notified: result.notifiedCount, failed: result.failedCount }),
                    color: 'yellow'
                });
            } else {
                notifications.show({
                    title: tSlot('notifications.notify.success.title'),
                    message: tSlot('notifications.notify.success.message', { notified: result.notifiedCount }),
                    color: 'green'
                });
            }
            setPromptCampaignId(null);
            refreshCampaigns();
        } catch (error) {
            console.error('Failed to notify media owners', error);
            const retryAfterSeconds = retryAfterSecondsOf(error);
            if (retryAfterSeconds !== null) setCooldownUntil(Date.now() + retryAfterSeconds * 1000);
            notifications.show({
                title: tSlot('notifications.notify.error.title'),
                message: tSlot(retryAfterSeconds !== null
                    ? 'notifications.notify.error.debounceMessage'
                    : 'notifications.notify.error.genericMessage'),
                color: 'red'
            });
        } finally {
            setNotifying(false);
        }
    };

    const handleSwapped = (_summary: ActiveCampaignSummary, campaignName: string) => {
        setIsSwapModalOpen(false);
        setPromptCampaignId(null);
        notifications.show({
            title: tSlot('notifications.swap.success.title'),
            message: tSlot('notifications.swap.success.message', { campaignName }),
            color: 'green'
        });
        refreshCampaigns();
    };

    const handleCreateFromSwap = () => {
        setIsSwapModalOpen(false);
        setIsCreateCampaignOpen(true);
    };

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
            // Offer to tell owners now, without blocking the next edit (FR-8.3).
            if (targetCampaignId === displayedCampaignId) setPromptCampaignId(targetCampaignId);
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
            if (adToDelete.campaignId === displayedCampaignId) setPromptCampaignId(adToDelete.campaignId);
            refreshCampaigns();
            setConfirmDeleteAdOpen(false);
            setAdToDelete(null);
        } catch (error) {
            console.error('Failed to delete ad', error);
            // The only conflict here: the last creative of the campaign on screen while a
            // subscription is live.
            const status = axios.isAxiosError(error) ? error.response?.status : undefined;
            notifications.show({
                title: t('notifications.deleteAd.error.title'),
                message: status === 409
                    ? t('notifications.deleteAd.error.lastActiveCreativeMessage')
                    : t('notifications.deleteAd.error.genericMessage'),
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
            // The only conflict left since subscriptions stopped referencing campaigns: it is the
            // business's active campaign.
            const status = axios.isAxiosError(error) ? error.response?.status : undefined;
            notifications.show({
                title: t('notifications.deleteCampaign.error.title'),
                message: status === 409
                    ? t('notifications.deleteCampaign.error.isActiveCampaignMessage')
                    : t('notifications.deleteCampaign.error.genericMessage'),
                color: 'red'
            });
        }
    };

    const handleArchiveAdCampaign = (campaignId: string) => {
        setCampaignToArchive(campaigns.find((c) => c.campaignId === campaignId) ?? null);
    };

    const confirmArchiveCampaign = async () => {
        if (!campaignToArchive || !organization) return;

        try {
            await archiveCampaign(organization.businessId, campaignToArchive.campaignId);
            notifications.show({
                title: tArchive('successTitle'),
                message: tArchive('success', { name: campaignToArchive.name }),
                color: 'green'
            });
            setCampaignToArchive(null);
            refreshCampaigns();
        } catch (error) {
            console.error('Failed to archive campaign', error);
            // The only conflict: it became the active campaign after this page loaded.
            const status = axios.isAxiosError(error) ? error.response?.status : undefined;
            notifications.show({
                title: tArchive('errorTitle'),
                message: tArchive(status === 409 ? 'errorActiveCampaign' : 'errorGeneric'),
                color: 'red'
            });
        }
    };

    // No confirmation: restoring a campaign to the list is always safe and changes nothing else.
    const handleUnarchiveAdCampaign = async (campaignId: string) => {
        if (!organization) return;
        const name = campaigns.find((c) => c.campaignId === campaignId)?.name ?? '';

        try {
            await unarchiveCampaign(organization.businessId, campaignId);
            notifications.show({
                title: tArchive('unarchiveSuccessTitle'),
                message: tArchive('unarchiveSuccess', { name }),
                color: 'green'
            });
            refreshCampaigns();
        } catch (error) {
            console.error('Failed to unarchive campaign', error);
            notifications.show({
                title: tArchive('errorTitle'),
                message: tArchive('unarchiveErrorGeneric'),
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

    // The stats describe the campaigns in use, so they don't change when archived ones are shown.
    const listedCampaigns = campaigns.filter((c) => c.archivedAt === null);
    const allAds = listedCampaigns.flatMap((c) => c.ads);
    const imageAdsCount = allAds.filter((ad) => ad.adType === "IMAGE").length;
    const videoAdsCount = allAds.filter((ad) => ad.adType === "VIDEO").length;

    const stats = [
        { title: t('stats.totalCampaigns'), value: listedCampaigns.length.toString(), icon: IconSpeakerphone, color: "blue" },
        { title: t('stats.totalAds'), value: allAds.length.toString(), icon: IconAd, color: "orange" },
        { title: t('stats.imageAds'), value: imageAdsCount.toString(), icon: IconPhoto, color: "teal" },
        { title: t('stats.videoAds'), value: videoAdsCount.toString(), icon: IconMovie, color: "grape" },
    ];

    return (
        <Stack gap="md" p="md">
            <Group justify="space-between">
                <Title order={1}>{t('page.title')}</Title>
                <Group gap="md">
                    <Switch
                        label={tArchive('showArchivedToggle')}
                        checked={showArchived}
                        onChange={(event) => setShowArchived(event.currentTarget.checked)}
                    />
                    <Button variant="gradient" onClick={() => setIsCreateCampaignOpen(true)}>
                        {t('page.createButton')}
                    </Button>
                </Group>
            </Group>

            <ActiveCampaignSlot
                state={slotState}
                cooldownSeconds={cooldownSeconds}
                notifying={notifying}
                supportHref={`mailto:${SUPPORT_EMAIL}`}
                onSwap={() => setIsSwapModalOpen(true)}
                onNotify={handleNotify}
            />

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
                activeCampaignId={activeCampaignId}
                promptCampaignId={promptCampaignId}
                notifying={notifying}
                notifyDisabled={cooldownSeconds > 0}
                onNotifyNow={handleNotify}
                onDismissPrompt={() => setPromptCampaignId(null)}
                onDeleteAd={handleDeleteAd}
                onDeleteAdCampaign={handleDeleteAdCampaign}
                onArchiveAdCampaign={handleArchiveAdCampaign}
                onUnarchiveAdCampaign={handleUnarchiveAdCampaign}
                onOpenAddAd={handleOpenAddAd}
                onEditAdTags={handleOpenEditAdTags}
            />

            {businessId && (
                <SwapCampaignModal
                    opened={isSwapModalOpen}
                    businessId={businessId}
                    onClose={() => setIsSwapModalOpen(false)}
                    onSwapped={handleSwapped}
                    onCooldown={(retryAfterSeconds) => setCooldownUntil(Date.now() + retryAfterSeconds * 1000)}
                    onCreateCampaign={handleCreateFromSwap}
                />
            )}

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
            <ConfirmationModal
                opened={campaignToArchive !== null}
                title={tArchive('confirmTitle')}
                message={tArchive('confirmBody', { name: campaignToArchive?.name ?? '' })}
                confirmLabel={tArchive('confirmAction')}
                cancelLabel={t('confirmations.delete.cancel')}
                confirmColor="blue"
                onConfirm={confirmArchiveCampaign}
                onCancel={() => setCampaignToArchive(null)}
            />
        </Stack>
    );
}
