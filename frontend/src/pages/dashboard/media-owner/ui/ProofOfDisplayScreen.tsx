"use client";

import { Stack, Title } from "@mantine/core";
import { useEffect, useMemo, useRef, useState } from "react";
import { useMediaList } from "@/pages/dashboard/media-owner/model/useMediaList";
import ProofMediaTable from "./tables/ProofMediaTable";
import SubmitProofStepperModal from "./modals/SubmitProofStepperModal";
import { getLiveCampaignsForMedia } from "@/features/bundle-subscription";
import { useTranslations } from "next-intl";

type CountsMap = Record<string, number>;

function chunk<T>(arr: T[], size: number): T[][] {
    const res: T[][] = [];
    for (let i = 0; i < arr.length; i += size) res.push(arr.slice(i, i + size));
    return res;
}

export default function ProofOfDisplayScreen() {
    const { media } = useMediaList();

    const [opened, setOpened] = useState(false);
    const [selectedMedia, setSelectedMedia] = useState<{ id: string; name: string } | null>(null);

    const [activeAdsCounts, setActiveAdsCounts] = useState<CountsMap>({});

    // cache counts across renders so it doesnt refetch for the same mediaId
    const countsCacheRef = useRef<CountsMap>({});

    const mediaIds = useMemo(
        () => (media ?? []).map((m) => String(m.id)).filter(Boolean),
        [media]
    );

    const t = useTranslations("proofOfDisplay");

    useEffect(() => {
        if (mediaIds.length === 0) return;

        let cancelled = false;

        const loadCounts = async () => {
            // only fetch ids that have not been already cached
            const missingIds = mediaIds.filter((id) => countsCacheRef.current[id] === undefined);
            if (missingIds.length === 0) {
                setActiveAdsCounts({ ...countsCacheRef.current });
                return;
            }

            const CONCURRENCY = 5;
            const batches = chunk(missingIds, CONCURRENCY);

            for (const batch of batches) {
                const results = await Promise.all(
                    batch.map(async (id) => {
                        try {
                            // The endpoint returns only campaigns on a live subscription
                            // covering this screen, already distinct — the status filtering and
                            // de-duplication this used to do client-side now happen server-side.
                            const liveCampaigns = await getLiveCampaignsForMedia(id);

                            return [id, liveCampaigns.length] as const;
                        } catch {
                            return [id, 0] as const;
                        }
                    })
                );

                if (cancelled) return;

                // update cache and state incrementally
                const nextCache = { ...countsCacheRef.current };
                for (const [id, count] of results) nextCache[id] = count;

                countsCacheRef.current = nextCache;

                // only keep counts for ids that still exist in the current media list
                const filteredForCurrentMedia: CountsMap = {};
                for (const id of mediaIds) {
                    if (nextCache[id] !== undefined) filteredForCurrentMedia[id] = nextCache[id];
                }

                setActiveAdsCounts(filteredForCurrentMedia);
            }
        };

        void loadCounts();

        return () => {
            cancelled = true;
        };
    }, [mediaIds]);

    return (
        <Stack gap="xs" p="md" style={{ flex: 1, minWidth: 0 }}>
            <Title order={1} mb={0}>
                {t("page.title")}
            </Title>

            <ProofMediaTable
                rows={media}
                activeAdsCounts={activeAdsCounts}
                onAddProof={(row) => {
                    setSelectedMedia({ id: String(row.id), name: row.name });
                    setOpened(true);
                }}
            />

            {selectedMedia && (
                <SubmitProofStepperModal
                    opened={opened}
                    onClose={() => setOpened(false)}
                    mediaId={selectedMedia.id}
                    mediaName={selectedMedia.name}
                />
            )}
        </Stack>
    );
}
