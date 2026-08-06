"use client";

import { useEffect, useState } from "react";
import { getLiveCampaignsForMedia } from "@/features/bundle-subscription";

export function useProofStepper(mediaId: string) {
    const [campaigns, setCampaigns] = useState<{ value: string; label: string }[]>([]);
    const [selectedCampaignId, setSelectedCampaignId] = useState<string | null>(null);
    const [loadingCampaigns, setLoadingCampaigns] = useState(false);

    useEffect(() => {
        if (!mediaId) return;

        const load = async () => {
            setLoadingCampaigns(true);
            try {
                // Already filtered server-side to campaigns on a live bundle subscription that
                // includes this screen, and already de-duplicated — so unlike the reservation
                // version this needs no client-side narrowing.
                const liveCampaigns = await getLiveCampaignsForMedia(mediaId);

                setCampaigns(
                    liveCampaigns.map((campaign) => ({
                        value: campaign.campaignId,
                        label: campaign.campaignName ?? campaign.campaignId,
                    }))
                );
            } catch (err) {
                console.warn("Failed to load live campaigns:", err);
                setCampaigns([]);
            } finally {
                setLoadingCampaigns(false);
            }
        };

        void load();
    }, [mediaId]);

    return { campaigns, selectedCampaignId, setSelectedCampaignId, loadingCampaigns };
}
