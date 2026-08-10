"use client";

import { useCallback, useEffect, useState } from "react";
import { getLiveCampaignsForMedia } from "@/features/bundle-subscription";

export function useProofStepper(mediaId: string) {
    const [campaigns, setCampaigns] = useState<{ value: string; label: string }[]>([]);
    const [selectedCampaignId, setSelectedCampaignId] = useState<string | null>(null);
    const [loadingCampaigns, setLoadingCampaigns] = useState(false);
    const [loadCampaignsFailed, setLoadCampaignsFailed] = useState(false);
    const [reloadToken, setReloadToken] = useState(0);

    useEffect(() => {
        if (!mediaId) return;

        let cancelled = false;

        (async () => {
            setLoadingCampaigns(true);
            setLoadCampaignsFailed(false);
            try {
                // Already filtered server-side to campaigns on a live bundle subscription that
                // includes this screen, and already de-duplicated — so unlike the reservation
                // version this needs no client-side narrowing.
                const liveCampaigns = await getLiveCampaignsForMedia(mediaId);

                if (cancelled) return;
                setCampaigns(
                    liveCampaigns.map((campaign) => ({
                        value: campaign.campaignId,
                        label: campaign.campaignName ?? campaign.campaignId,
                    }))
                );
            } catch (err) {
                console.warn("Failed to load live campaigns:", err);
                if (cancelled) return;
                setCampaigns([]);
                setLoadCampaignsFailed(true);
            } finally {
                if (!cancelled) setLoadingCampaigns(false);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [mediaId, reloadToken]);

    const retryLoadCampaigns = useCallback(() => {
        setReloadToken((t) => t + 1);
    }, []);

    return {
        campaigns,
        selectedCampaignId,
        setSelectedCampaignId,
        loadingCampaigns,
        loadCampaignsFailed,
        retryLoadCampaigns,
    };
}
