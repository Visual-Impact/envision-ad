"use client";

import { Button, Group, Loader, Stack, Title } from "@mantine/core";
import { IconPlus } from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import { useLocale } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { notifications } from "@mantine/notifications";
import { Venue, VenueRequestDTO } from "@/entities/venue";
import { getAllVenues, createVenue, updateVenue, deleteVenue } from "@/features/venue-management/api";
import { VenueTable } from "@/pages/dashboard/admin/ui/tables/VenueTable";
import { VenueFormModal } from "@/pages/dashboard/admin/ui/modals/VenueFormModal";
import { VenueDeleteModal } from "@/pages/dashboard/admin/ui/modals/VenueDeleteModal";

export default function VenueManagementPage() {
    const t = useTranslations("venueManagement");
    const locale = useLocale();

    const [venues, setVenues] = useState<Venue[]>([]);
    const [loading, setLoading] = useState(true);

    const [formModalOpen, setFormModalOpen] = useState(false);
    const [editingVenue, setEditingVenue] = useState<Venue | null>(null);

    const [deleteModalOpen, setDeleteModalOpen] = useState(false);
    const [deletingVenue, setDeletingVenue] = useState<Venue | null>(null);

    const refreshVenues = useCallback(async () => {
        try {
            const data = await getAllVenues(locale);
            setVenues(data);
        } catch {
            notifications.show({ title: t("notifications.loadFailed"), message: "", color: "red" });
        } finally {
            setLoading(false);
        }
    }, [locale, t]);

    // Initial load is inlined (rather than calling `refreshVenues`) so the
    // effect's own state updates stay local to the effect, with proper
    // unmount cancellation. `refreshVenues` remains for the post-mutation
    // refreshes below, which aren't run from an effect.
    useEffect(() => {
        let cancelled = false;

        (async () => {
            try {
                const data = await getAllVenues(locale);
                if (!cancelled) setVenues(data);
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
        setEditingVenue(null);
        setFormModalOpen(true);
    };

    const handleEdit = (venue: Venue) => {
        setEditingVenue(venue);
        setFormModalOpen(true);
    };

    const handleDeleteClick = (venue: Venue) => {
        setDeletingVenue(venue);
        setDeleteModalOpen(true);
    };

    const handleSave = async (data: VenueRequestDTO) => {
        try {
            if (editingVenue) {
                await updateVenue(editingVenue.venueId, data);
                notifications.show({ title: t("notifications.updated"), message: "", color: "green" });
            } else {
                await createVenue(data);
                notifications.show({ title: t("notifications.created"), message: "", color: "green" });
            }
            setFormModalOpen(false);
            await refreshVenues();
        } catch {
            notifications.show({
                title: editingVenue ? t("notifications.updateFailed") : t("notifications.createFailed"),
                message: "",
                color: "red",
            });
        }
    };

    const handleDeleteConfirm = async () => {
        if (!deletingVenue) return;
        try {
            await deleteVenue(deletingVenue.venueId);
            notifications.show({ title: t("notifications.deleted"), message: "", color: "green" });
            setDeleteModalOpen(false);
            setDeletingVenue(null);
            await refreshVenues();
        } catch {
            notifications.show({ title: t("notifications.deleteFailed"), message: "", color: "red" });
        }
    };

    return (
        <Stack component="main" gap="md" p="md" style={{ flex: 1, minWidth: 0 }}>
            <Group justify="space-between" align="center">
                <Title order={1}>{t("title")}</Title>
                <Button variant="gradient" leftSection={<IconPlus size={18} />} onClick={handleCreate}>
                    {t("addVenue")}
                </Button>
            </Group>

            {loading ? (
                <Loader />
            ) : (
                <VenueTable
                    venues={venues}
                    onEdit={handleEdit}
                    onDelete={handleDeleteClick}
                />
            )}

            <VenueFormModal
                opened={formModalOpen}
                onClose={() => setFormModalOpen(false)}
                onSave={handleSave}
                venue={editingVenue}
            />

            <VenueDeleteModal
                opened={deleteModalOpen}
                onClose={() => { setDeleteModalOpen(false); setDeletingVenue(null); }}
                onConfirm={handleDeleteConfirm}
                venue={deletingVenue}
            />
        </Stack>
    );
}
