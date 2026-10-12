"use client";

import { Alert, Loader, Stack, Text, Title } from "@mantine/core";
import { IconAlertTriangle } from "@tabler/icons-react";
import { useEffect, useState } from "react";
import { useTranslations } from "next-intl";
import { useOrganization } from "@/entities/organization";
import { getMediaOwnerProofSubscriptions, type MediaOwnerProofSubscription } from "../api";
import ProofMediaTable from "./tables/ProofMediaTable";
import SubmitProofStepperModal from "./modals/SubmitProofStepperModal";

/**
 * The media owner's Proof of Display page (P12): every subscription covering their screens, each
 * with one link to a page they maintain. Replaces the per-screen photo upload, which emailed the
 * advertiser once and kept nothing.
 */
export default function ProofOfDisplayScreen() {
    const t = useTranslations("proofOfDisplay");
    const { organization } = useOrganization();
    const businessId = organization?.businessId;

    const [rows, setRows] = useState<MediaOwnerProofSubscription[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadFailed, setLoadFailed] = useState(false);
    const [editing, setEditing] = useState<MediaOwnerProofSubscription | null>(null);

    useEffect(() => {
        if (!businessId) return;

        let cancelled = false;

        void (async () => {
            setLoading(true);
            setLoadFailed(false);
            try {
                const data = await getMediaOwnerProofSubscriptions(businessId);
                if (cancelled) return;
                setRows(data);
            } catch (error) {
                if (cancelled) return;
                console.error("Failed to load proof-of-display subscriptions", error);
                setLoadFailed(true);
                setRows([]);
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [businessId]);

    return (
        <Stack gap="xs" p="md" style={{ flex: 1, minWidth: 0 }}>
            <Title order={1} mb={0}>
                {t("page.title")}
            </Title>
            <Text size="sm" c="dimmed" mb="sm" maw={760}>
                {t("page.subtitle")}
            </Text>

            {loading ? (
                <Stack align="center" p="xl">
                    <Loader />
                </Stack>
            ) : loadFailed ? (
                <Alert color="red" icon={<IconAlertTriangle size="1rem" />}>
                    {t("page.loadError")}
                </Alert>
            ) : (
                <ProofMediaTable rows={rows} onEditLink={setEditing} />
            )}

            {editing && (
                <SubmitProofStepperModal
                    key={editing.subscriptionId}
                    subscription={editing}
                    onClose={() => setEditing(null)}
                    onSaved={(saved) => {
                        setRows((current) =>
                            current.map((row) =>
                                row.subscriptionId === saved.subscriptionId
                                    ? {
                                          ...row,
                                          proofLink: { status: "SET", url: saved.url },
                                          linkUpdatedAt: saved.updatedAt,
                                      }
                                    : row,
                            ),
                        );
                        setEditing(null);
                    }}
                />
            )}
        </Stack>
    );
}
