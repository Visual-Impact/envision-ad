"use client";

import { Button, Group, Loader, Stack, Title } from "@mantine/core";
import { IconPlus } from "@tabler/icons-react";
import { notifications } from "@mantine/notifications";
import { useLocale, useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { Bundle, BundleRequestDTO } from "@/entities/bundle";
import { Venue } from "@/entities/venue";
import {
    createBundle,
    deleteBundle,
    getAllBundles,
    updateBundle,
} from "@/features/bundle-management/api";
import { getAllVenues } from "@/features/venue-management/api";
import { BundleTable } from "@/pages/dashboard/admin/ui/tables/BundleTable";
import { BundleFormModal } from "@/pages/dashboard/admin/ui/modals/BundleFormModal";
import { BundleDeleteModal } from "@/pages/dashboard/admin/ui/modals/BundleDeleteModal";
import { BundleExclusionsModal } from "@/pages/dashboard/admin/ui/modals/BundleExclusionsModal";

export default function BundleManagementPage() {
    const t = useTranslations("bundleManagement");
    const locale = useLocale();

    const [bundles, setBundles] = useState<Bundle[]>([]);
    const [venues, setVenues] = useState<Venue[]>([]);
    const [loading, setLoading] = useState(true);

    const [formModalOpen, setFormModalOpen] = useState(false);
    const [editingBundle, setEditingBundle] = useState<Bundle | null>(null);

    const [deleteModalOpen, setDeleteModalOpen] = useState(false);
    const [deletingBundle, setDeletingBundle] = useState<Bundle | null>(null);

    const [exclusionsModalOpen, setExclusionsModalOpen] = useState(false);
    const [exclusionsBundle, setExclusionsBundle] = useState<Bundle | null>(null);

    // The admin view shows deactivated bundles too, hence active=false.
    const refreshBundles = useCallback(async () => {
        try {
            const data = await getAllBundles(undefined, false);
            setBundles(data);
        } catch {
            notifications.show({ title: t("notifications.loadFailed"), message: "", color: "red" });
        } finally {
            setLoading(false);
        }
    }, [t]);

    // Initial load is inlined (rather than calling `refreshBundles`) so the effect's
    // own state updates stay local to it, with proper unmount cancellation —
    // matching VenueManagementPage and the react-hooks/set-state-in-effect rule.
    useEffect(() => {
        let cancelled = false;

        (async () => {
            try {
                const [bundleData, venueData] = await Promise.all([
                    getAllBundles(undefined, false),
                    getAllVenues(locale),
                ]);
                if (!cancelled) {
                    setBundles(bundleData);
                    setVenues(venueData);
                }
            } catch {
                if (!cancelled) {
                    notifications.show({ title: t("notifications.loadFailed"), message: "", color: "red" });
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [locale, t]);

    const handleCreate = () => {
        setEditingBundle(null);
        setFormModalOpen(true);
    };

    const handleEdit = (bundle: Bundle) => {
        setEditingBundle(bundle);
        setFormModalOpen(true);
    };

    const handleManageExclusions = (bundle: Bundle) => {
        setExclusionsBundle(bundle);
        setExclusionsModalOpen(true);
    };

    const handleDeleteClick = (bundle: Bundle) => {
        setDeletingBundle(bundle);
        setDeleteModalOpen(true);
    };

    const handleSave = async (data: BundleRequestDTO) => {
        try {
            if (editingBundle) {
                await updateBundle(editingBundle.bundleId, data);
                notifications.show({ title: t("notifications.updated"), message: "", color: "green" });
            } else {
                await createBundle(data);
                notifications.show({ title: t("notifications.created"), message: "", color: "green" });
            }
            setFormModalOpen(false);
            await refreshBundles();
        } catch {
            notifications.show({
                title: editingBundle ? t("notifications.updateFailed") : t("notifications.createFailed"),
                message: "",
                color: "red",
            });
        }
    };

    const handleDeleteConfirm = async () => {
        if (!deletingBundle) return;
        try {
            await deleteBundle(deletingBundle.bundleId);
            notifications.show({ title: t("notifications.deleted"), message: "", color: "green" });
            setDeleteModalOpen(false);
            setDeletingBundle(null);
            await refreshBundles();
        } catch {
            // Most likely a 409 from the active-subscription guard; the modal already
            // warns when activeSubscriptionCount is non-zero, so this covers the race
            // where a subscription is created between load and delete.
            notifications.show({ title: t("notifications.deleteFailed"), message: "", color: "red" });
        }
    };

    return (
        <Stack component="main" gap="md" p="md" style={{ flex: 1, minWidth: 0 }}>
            <Group justify="space-between" align="center">
                <Title order={1}>{t("title")}</Title>
                <Button variant="gradient" leftSection={<IconPlus size={18} />} onClick={handleCreate}>
                    {t("addBundle")}
                </Button>
            </Group>

            {loading ? (
                <Loader />
            ) : (
                <BundleTable
                    bundles={bundles}
                    onEdit={handleEdit}
                    onManageExclusions={handleManageExclusions}
                    onDelete={handleDeleteClick}
                />
            )}

            <BundleFormModal
                opened={formModalOpen}
                onClose={() => setFormModalOpen(false)}
                onSave={handleSave}
                bundle={editingBundle}
                venues={venues}
            />

            <BundleDeleteModal
                opened={deleteModalOpen}
                onClose={() => {
                    setDeleteModalOpen(false);
                    setDeletingBundle(null);
                }}
                onConfirm={handleDeleteConfirm}
                bundle={deletingBundle}
            />

            <BundleExclusionsModal
                opened={exclusionsModalOpen}
                onClose={() => {
                    setExclusionsModalOpen(false);
                    setExclusionsBundle(null);
                }}
                onChanged={refreshBundles}
                bundle={exclusionsBundle}
            />
        </Stack>
    );
}
