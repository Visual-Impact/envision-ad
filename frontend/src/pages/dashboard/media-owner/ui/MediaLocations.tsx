"use client";

import React, { useCallback, useEffect, useState } from "react";
import { Alert, Button, Group, Stack, Title } from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { useTranslations } from 'next-intl';
import { useMediaForm } from "@/pages/dashboard/media-owner/hooks/useMediaForm";
import { MediaModal } from "@/pages/dashboard/media-owner/ui/modals/MediaModal";
import { IconAlertTriangle, IconCheck } from "@tabler/icons-react";
import { WeeklyScheduleModel } from "@/entities/media";
import { MediaLocation, MediaLocationRequestDTO } from "@/entities/media-location";
import {
    createMediaLocation,
    deleteMediaLocation,
    getAllMediaLocations,
} from "@/features/media-location-management";
import { useMediaList } from "@/pages/dashboard/media-owner/hooks/useMediaList";
import { MediaLocationsTable } from "@/pages/dashboard/media-owner/ui/tables/MediaLocationsTable";
import { CreateMediaLocationModal } from "@/pages/dashboard/media-owner/ui/modals/CreateMediaLocationModal";
import { EditMediaLocationModal } from "@/pages/dashboard/media-owner/ui/modals/EditMediaLocationModal";
import { ConfirmationModal } from "@/shared/ui";
import { MediaStatusEnum } from "@/entities/media";
import { useOrganization } from "@/entities/organization";
import { getStripeAccountStatus } from "@/features/payment";
import Link from "next/link";

interface StripeStatus {
    connected: boolean;
    onboardingComplete: boolean;
    chargesEnabled: boolean;
    payoutsEnabled: boolean;
}

const getApiErrorMessage = (error: unknown): string | null => {
    if (!error || typeof error !== "object" || !("response" in error)) return null;
    const response = (error as { response?: { data?: unknown } }).response;
    if (!response?.data || typeof response.data !== "object") return null;
    return (response.data as { message?: string }).message ?? null;
};

const getApiErrorStatus = (error: unknown): number | null => {
    if (!error || typeof error !== "object" || !("response" in error)) return null;
    const response = (error as { response?: { status?: unknown } }).response;
    return typeof response?.status === "number" ? response.status : null;
};

const getApiErrorCode = (error: unknown): string | null => {
    if (!error || typeof error !== "object" || !("response" in error)) return null;
    const response = (error as { response?: { data?: unknown } }).response;
    if (!response?.data || typeof response.data !== "object") return null;
    return (response.data as { code?: string }).code ?? null;
};

const hasApiFieldErrors = (error: unknown): boolean => {
    if (!error || typeof error !== "object" || !("response" in error)) return false;
    const response = (error as { response?: { data?: unknown } }).response;
    if (!response?.data || typeof response.data !== "object") return false;
    const data = response.data as { fieldErrors?: unknown };
    if (!data.fieldErrors || typeof data.fieldErrors !== "object") return false;
    return Object.keys(data.fieldErrors as Record<string, unknown>).length > 0;
};

export default function MediaLocations() {
    const t = useTranslations('mediaLocations');
    const tMedia = useTranslations('media');
    const { organization } = useOrganization();

    const [locations, setLocations] = useState<MediaLocation[]>([]);
    const [refreshCount, setRefreshCount] = useState(0);

    const [stripeStatus, setStripeStatus] = useState<StripeStatus | null>(null);
    const [isStripeLoading, setIsStripeLoading] = useState(true);

    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const [isEditModalOpen, setIsEditModalOpen] = useState(false);
    const [locationToEdit, setLocationToEdit] = useState<MediaLocation | null>(null);
    const [confirmDeleteOpen, setConfirmDeleteOpen] = useState(false);
    const [locationToDelete, setLocationToDelete] = useState<string | null>(null);

    const { formState, updateField, updateDayTime, resetForm, setFormState } = useMediaForm();
    const { addNewMedia, editMedia, deleteMediaById, toggleMediaStatus, fetchMediaById } = useMediaList();
    const [isMediaModalOpen, setIsMediaModalOpen] = useState(false);
    const [editingMediaId, setEditingMediaId] = useState<string | null>(null);

    const refreshLocations = useCallback(() => {
        setRefreshCount(c => c + 1);
    }, []);

    const businessId = organization?.businessId;

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

    useEffect(() => {
        if (!businessId) return;

        let ignored = false;

        const fetchLocations = async () => {
            try {
                const data = await getAllMediaLocations(businessId);
                if (!ignored) setLocations(data);
            } catch (error) {
                if (!ignored) {
                    console.error("Failed to load media locations", error);
                    notifications.show({
                        title: t('notifications.loadFailed.title'),
                        message: t('notifications.loadFailed.message'),
                        color: "red"
                    });
                }
            }
        };

        void fetchLocations();

        return () => { ignored = true; };
    }, [businessId, t, refreshCount]);

    const handleCreateLocation = async (payload: MediaLocationRequestDTO) => {
        if (!organization) return;
        try {
            await createMediaLocation({ ...payload, businessId: organization.businessId });
            notifications.show({
                title: t('notifications.create.success.title'),
                message: t('notifications.create.success.message'),
                color: "green"
            });
            refreshLocations();
        } catch (error) {
            const apiMessage = getApiErrorMessage(error);
            const message = apiMessage || (hasApiFieldErrors(error)
                ? t('notifications.create.error.addressGuidance')
                : t('notifications.create.error.message'));
            notifications.show({
                title: t('notifications.create.error.title'),
                message,
                color: "red"
            });
            throw error;
        }
    };

    const handleDeleteLocation = (id: string) => {
        setLocationToDelete(id);
        setConfirmDeleteOpen(true);
    };

    const confirmDelete = async () => {
        if (!locationToDelete) return;
        try {
            await deleteMediaLocation(locationToDelete);
            notifications.show({
                title: t('notifications.delete.success.title'),
                message: t('notifications.delete.success.message'),
                color: "green"
            });
            refreshLocations();
        } catch (error) {
            const apiStatus = getApiErrorStatus(error);
            const apiMessage = getApiErrorMessage(error);
            const message = apiStatus === 409
                ? t('notifications.delete.error.activeMedia')
                : (apiMessage || t('notifications.delete.error.message'));
            notifications.show({
                title: t('notifications.delete.error.title'),
                message,
                color: "red"
            });
        } finally {
            setConfirmDeleteOpen(false);
            setLocationToDelete(null);
        }
    };

    const handleAssignMedia = (locationId: string) => {
        if (!isStripeOnboarded) return;
        updateField("mediaLocationId", locationId);
        setIsMediaModalOpen(true);
    };

    const handleSaveMedia = async () => {
        try {
            await addNewMedia(formState);
            notifications.show({
                title: t('notifications.createMedia.success.title'),
                message: t('notifications.createMedia.success.message'),
                color: "green",
                icon: <IconCheck size="1.1rem" />,
            });
            setIsMediaModalOpen(false);
            resetForm();
            refreshLocations();
        } catch (error) {
            console.error("Failed to create media", error);
            const isStripeNotOnboarded = getApiErrorCode(error) === "STRIPE_NOT_ONBOARDED";
            notifications.show({
                title: t('notifications.createMedia.error.title'),
                message: isStripeNotOnboarded
                    ? t("notifications.createMedia.error.stripeNotOnboarded")
                    : t("notifications.createMedia.error.message"),
                color: "red"
            });
        }
    };

    const handleEditMedia = async (id: string | number) => {
        try {
            const backend = await fetchMediaById(id);
            const schedule = backend.schedule || { selectedMonths: [], weeklySchedule: [] as WeeklyScheduleModel[] };

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

            setEditingMediaId(String(id));
            setIsMediaModalOpen(true);
        } catch (error) {
            console.error("Failed to load media", error);
            notifications.show({ title: "Error", message: t("notifications.loadMedia.error.message"), color: "red" });
        }
    };

    const handleSaveMediaSubmit = async () => {
        if (editingMediaId) {
            try {
                await editMedia(editingMediaId, formState);
                notifications.show({
                    title: t("notifications.updateMedia.success.title"),
                    message: t("notifications.updateMedia.success.message"),
                    color: "green",
                    icon: <IconCheck size="1.1rem" />,
                });
                setIsMediaModalOpen(false);
                setEditingMediaId(null);
                resetForm();
                refreshLocations();
            } catch (error) {
                console.error("Failed to update media", error);
                notifications.show({ title: "Error", message: t("notifications.updateMedia.error.message"), color: "red" });
            }
        } else {
            await handleSaveMedia();
        }
    };

    const handleDeleteMedia = async (id: string | number) => {
        try {
            await deleteMediaById(id);
            notifications.show({
                title: t("notifications.deleteMedia.success.title"),
                message: t("notifications.deleteMedia.success.message"),
                color: "green",
            });
            refreshLocations();
        } catch (error) {
            console.error("Failed to delete media", error);
            notifications.show({ title: "Error", message: t("notifications.deleteMedia.error.message"), color: "red" });
        }
    };

    const handleToggleMediaStatus = async (id: string | number, nextStatus: MediaStatusEnum.ACTIVE | MediaStatusEnum.INACTIVE) => {
        try {
            await toggleMediaStatus(id, nextStatus);
            notifications.show({
                title: t("notifications.statusMedia.success.title"),
                message: t("notifications.statusMedia.success.message"),
                color: "green",
            });
            refreshLocations();
        } catch (error) {
            console.error("Failed to toggle status", error);
            notifications.show({ title: "Error", message: t("notifications.statusMedia.error.message"), color: "red" });
        }
    };

    const handleEditLocation = (location: MediaLocation) => {
        setLocationToEdit(location);
        setIsEditModalOpen(true);
    };

    return (
        <Stack gap="md" p="md">
            <Group justify="space-between">
                <Title order={2}>{t('page.title')}</Title>
                <Button variant="gradient" onClick={() => setIsCreateModalOpen(true)}>{t('page.createButton')}</Button>
            </Group>

            {!isStripeLoading && !isStripeOnboarded && (
                <Alert icon={<IconAlertTriangle size="1rem" />} title={tMedia('stripe.requiredTitle')} color="orange">
                    {tMedia('stripe.requiredMessage')}
                    <Link href="/dashboard/stripe" style={{ color: 'var(--mantine-color-orange-7)', textDecoration: 'underline' }}>
                        {tMedia('stripe.requiredLink')}
                    </Link>
                </Alert>
            )}

            <MediaLocationsTable
                locations={locations}
                onDeleteLocation={handleDeleteLocation}
                onAddMedia={handleAssignMedia}
                onEditLocation={handleEditLocation}
                onEditMedia={handleEditMedia}
                onDeleteMedia={handleDeleteMedia}
                onToggleMediaStatus={handleToggleMediaStatus}
                addMediaDisabled={!isStripeOnboarded}
            />

            <CreateMediaLocationModal
                opened={isCreateModalOpen}
                onClose={() => setIsCreateModalOpen(false)}
                onSuccess={handleCreateLocation}
            />

            <EditMediaLocationModal
                opened={isEditModalOpen}
                onClose={() => { setIsEditModalOpen(false); setLocationToEdit(null); }}
                location={locationToEdit}
                onSuccess={refreshLocations}
            />

            <ConfirmationModal
                opened={confirmDeleteOpen}
                title={t('confirmations.delete.title')}
                message={t('confirmations.delete.message')}
                confirmLabel={t('confirmations.delete.confirm')}
                cancelLabel={t('confirmations.delete.cancel')}
                confirmColor="red"
                onConfirm={confirmDelete}
                onCancel={() => setConfirmDeleteOpen(false)}
            />

            <MediaModal
                opened={isMediaModalOpen}
                onClose={() => { setIsMediaModalOpen(false); setEditingMediaId(null); resetForm(); }}
                onSave={handleSaveMediaSubmit}
                formState={formState}
                onFieldChange={updateField}
                onDayTimeChange={updateDayTime}
                isEditing={!!editingMediaId}
            />
        </Stack>
    );
}