"use client";

import React, { useMemo, useState, useEffect } from "react";
import { MediaModal } from "@/pages/dashboard/media-owner/ui/modals/MediaModal";
import { MediaTable } from "@/pages/dashboard/media-owner/ui/tables/MediaTable";
import { useMediaList } from "@/pages/dashboard/media-owner/hooks/useMediaList";
import { useMediaForm } from "@/pages/dashboard/media-owner/hooks/useMediaForm";
import { useTranslations } from "next-intl";
import {Alert, Button, Group, Loader, Pagination, Stack, Title, VisuallyHidden} from "@mantine/core";
import { modals } from "@mantine/modals";
import { notifications } from "@mantine/notifications";
import { WeeklyScheduleModel } from "@/entities/media";
import { IconCheck, IconAlertTriangle } from "@tabler/icons-react";
import { getStripeAccountStatus } from "@/features/payment";
import { useOrganization } from "@/app/providers";
import Link from "next/link";

const ITEMS_PER_PAGE = 20;

interface StripeStatus {
    connected: boolean;
    onboardingComplete: boolean;
    chargesEnabled: boolean;
    payoutsEnabled: boolean;
}

export default function MediaOwnerPage() {
    const { media, addNewMedia, editMedia, deleteMediaById, fetchMediaById, toggleMediaStatus } = useMediaList();
    const { formState, updateField, updateDayTime, resetForm, setFormState } = useMediaForm();
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingId, setEditingId] = useState<string | null>(null);
    const [activePage, setActivePage] = useState(1);
    const t = useTranslations("media");
    const { organization } = useOrganization();

    const [stripeStatus, setStripeStatus] = useState<StripeStatus | null>(null);
    const [isStripeLoading, setIsStripeLoading] = useState(true);

    useEffect(() => {
        if (!organization) return;
        let cancelled = false;

        (async () => {
            setIsStripeLoading(true);
            try {
                const status = await getStripeAccountStatus(organization.businessId);
                if (!cancelled) setStripeStatus(status);
            } catch (e) {
                console.error("Failed to fetch Stripe status", e);
            } finally {
                if (!cancelled) setIsStripeLoading(false);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [organization]);

    const isStripeOnboarded = stripeStatus?.onboardingComplete && stripeStatus?.chargesEnabled && stripeStatus?.payoutsEnabled;

    const handleSave = async () => {
        try {
            if (editingId) {
                await editMedia(editingId, formState);
                notifications.show({ title: t("success.title"), message: t("success.update"), color: "green", icon: <IconCheck size="1.1rem" /> });
            } else {
                await addNewMedia(formState);
                notifications.show({ title: t("success.title"), message: t("success.create"), color: "green", icon: <IconCheck size="1.1rem" /> });
            }
            setIsModalOpen(false);
            resetForm();
            setEditingId(null);
        } catch (error) {
            console.error("Failed to save media", error);
            notifications.show({ title: t("errors.error"), message: t("errors.saveFailed"), color: "red" });
        }
    };

    const handleEdit = async (id: string | number) => {
        try {
            const backend = await fetchMediaById(id);
            const schedule = backend.schedule || { selectedMonths: [], weeklySchedule: [] };

            const activeDaysOfWeek: Record<string, boolean> = {
                Monday: false, Tuesday: false, Wednesday: false, Thursday: false,
                Friday: false, Saturday: false, Sunday: false,
            };
            const dailyOperatingHours: Record<string, { start: string; end: string }> = {
                Monday: { start: "00:00", end: "00:00" }, Tuesday: { start: "00:00", end: "00:00" },
                Wednesday: { start: "00:00", end: "00:00" }, Thursday: { start: "00:00", end: "00:00" },
                Friday: { start: "00:00", end: "00:00" }, Saturday: { start: "00:00", end: "00:00" },
                Sunday: { start: "00:00", end: "00:00" },
            };

            schedule.weeklySchedule?.forEach((entry: WeeklyScheduleModel) => {
                const dayKey = entry.dayOfWeek.charAt(0).toUpperCase() + entry.dayOfWeek.slice(1);
                if (dayKey in activeDaysOfWeek) {
                    activeDaysOfWeek[dayKey] = entry.isActive;
                    dailyOperatingHours[dayKey] = { start: entry.startTime ?? "00:00", end: entry.endTime ?? "00:00" };
                }
            });

            const months = ["January","February","March","April","May","June","July","August","September","October","November","December"];
            const activeMonths: Record<string, boolean> = {};
            months.forEach((m) => (activeMonths[m] = (schedule.selectedMonths || []).includes(m)));

            setFormState({
                mediaTitle: backend.title ?? "",
                mediaOwnerName: backend.mediaOwnerName ?? "",
                mediaLocationId: backend.mediaLocation.id ?? "",
                resolution: backend.resolution ?? "",
                displayType: backend.typeOfDisplay ?? null,
                loopDuration: backend.loopDuration != null ? String(backend.loopDuration) : "",
                widthCm: backend.width != null ? String(backend.width) : "",
                heightCm: backend.height != null ? String(backend.height) : "",
                weeklyPrice: backend.price != null ? Number(backend.price).toFixed(2) : "",
                dailyImpressions: backend.dailyImpressions != null ? String(backend.dailyImpressions) : "",
                activeDaysOfWeek,
                dailyOperatingHours,
                activeMonths,
                errors: {},
                imageUrl: backend.imageUrl ?? null,
                previewConfiguration: backend.previewConfiguration ?? null,
                venueId: backend.venue?.venueId ?? null
            });

            setEditingId(String(id));
            setIsModalOpen(true);
        } catch (error) {
            console.error("Failed to load media", error);
            notifications.show({ title: t("errors.error"), message: t("errors.loadFailed"), color: "red" });
        }
    };

    const handleDelete = (id: string | number) => {
        modals.openConfirmModal({
            title: t("deleteConfirm.title"),
            centered: true,
            children: t("deleteConfirm.message"),
            labels: { confirm: t("deleteConfirm.confirm"), cancel: t("deleteConfirm.cancel") },
            confirmProps: { color: "red" },
            onConfirm: async () => {
                try {
                    await deleteMediaById(id);
                    notifications.show({ title: t("success.title"), message: t("success.delete"), color: "green" });
                } catch (error) {
                    console.error("Failed to delete media", error);
                    notifications.show({ title: "Error", message: t("errors.deleteFailed"), color: "red" });
                }
            },
        });
    };

    const handleToggleStatus = async (id: string | number) => {
        try {
            const newStatus = await toggleMediaStatus(id);
            if (newStatus) {
                notifications.show({
                    title: t("success.title"),
                    message: newStatus === "ACTIVE" ? t("success.active") : t("success.inactive"),
                    color: "green",
                });
            }
        } catch (error) {
            console.error("Failed to toggle media status", error);
            notifications.show({ title: t("errors.error"), message: t("errors.toggleFailed"), color: "red" });
        }
    };

    const totalPages = Math.ceil(media.length / ITEMS_PER_PAGE);
    const paginatedMedia = useMemo(() => {
        const start = (activePage - 1) * ITEMS_PER_PAGE;
        return media.slice(start, start + ITEMS_PER_PAGE);
    }, [media, activePage]);

    return (
        <Stack gap="md" p="md" style={{ flex: 1, minWidth: 0 }}>
            <Group justify="space-between">
                <VisuallyHidden>
                    <Title order={1}>{t('page.title')}</Title>
                </VisuallyHidden>
                {isStripeLoading ? (
                    <Loader />
                ) : isStripeOnboarded ? (
                    <Button variant="gradient" onClick={() => { setEditingId(null); resetForm(); setIsModalOpen(true); }}>
                        {t('newMedia')}
                    </Button>
                ) : (
                    <Alert icon={<IconAlertTriangle size="1rem" />} title={t('stripe.requiredTitle')} color="orange" style={{ width: '100%' }}>
                        {t('stripe.requiredMessage')}
                        <Link href="/dashboard/stripe" style={{ color: 'var(--mantine-color-orange-7)', textDecoration: 'underline' }}>
                            {t('stripe.requiredLink')}
                        </Link>
                    </Alert>
                )}
            </Group>

            <MediaModal
                opened={isModalOpen}
                onClose={() => { setIsModalOpen(false); setEditingId(null); resetForm(); }}
                onSave={handleSave}
                isEditing={!!editingId}
                formState={formState}
                onFieldChange={updateField}
                onDayTimeChange={updateDayTime}
            />

            <MediaTable rows={paginatedMedia} onEdit={handleEdit} onDelete={handleDelete} onToggleStatus={handleToggleStatus} />

            {totalPages > 1 && (
                <Group justify="center" mt="md">
                    <Pagination.Root total={totalPages} value={activePage} onChange={setActivePage}>
                        <Group gap="xs">
                            <Pagination.First aria-label={t("pagination.first")} />
                            <Pagination.Previous aria-label={t("pagination.prev")} />
                            <Pagination.Items />
                            <Pagination.Next aria-label={t("pagination.next")} />
                            <Pagination.Last aria-label={t("pagination.last")} />
                        </Group>
                    </Pagination.Root>
                </Group>
            )}
        </Stack>
    );
}