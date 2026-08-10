import { useState, useEffect } from "react";
import { MediaStatusEnum } from "@/entities/media/model/media";
import {
    addMedia,
    getMediaByBusinessId,
    getMediaById,
    updateMedia,
    deleteMedia,
} from "@/features/media-management/api";
import type { MediaRowData } from "@/pages/dashboard/media-owner/ui/tables/MediaRow";
import type { MediaFormState } from "./useMediaForm";
import { MediaRequestDTO } from "@/entities/media";
import { patchMediaStatus } from "@/features/media-management/api/patchMediaStatus";
import { useOrganization } from "@/app/providers";

export function useMediaList() {
    const [media, setMedia] = useState<MediaRowData[]>([]);
    const { organization } = useOrganization();

    useEffect(() => {
        if (!organization) return;

        let ignored = false;

        const fetchMedia = async () => {
            try {
                const data = await getMediaByBusinessId(organization.businessId);
                if (ignored) return;
                const items = (data || []).filter((m) => m.id != null);
                const mapped = items.map((m) => ({
                    id: String(m.id),
                    name: m.title,
                    image: m.imageUrl ?? null,
                    adsDisplayed: 0,
                    pending: 0,
                    status: m.status ?? MediaStatusEnum.PENDING,
                    timeUntil: "-",
                    price: `${Number(m.price)}`,
                    venue: m.venue ?? null
                }));
                setMedia(mapped);
            } catch (err) {
                if (!ignored) console.error("Failed to load media:", err);
            }
        };

        void fetchMedia();

        return () => { ignored = true; };
    }, [organization]);

    const buildScheduleFromForm = (formState: MediaFormState) => {
        const selectedMonths = Object.keys(formState.activeMonths).filter(
            (m) => formState.activeMonths[m]
        );

        const weeklySchedule = [
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
        ].map(day => ({
            dayOfWeek: day.toLowerCase(),
            isActive: formState.activeDaysOfWeek[day],
            startTime: formState.dailyOperatingHours[day]?.start ?? null,
            endTime: formState.dailyOperatingHours[day]?.end ?? null
        }));

        return { selectedMonths, weeklySchedule };
    };

    const addNewMedia = async (formState: MediaFormState) => {
        if (!formState.mediaTitle) throw new Error("Please enter a media name");

        const payload: MediaRequestDTO = {
            title: formState.mediaTitle,
            mediaOwnerName: formState.mediaOwnerName,
            mediaLocationId: formState.mediaLocationId,
            typeOfDisplay: formState.displayType,
            loopDuration: Number(formState.loopDuration),
            resolution: formState.resolution,
            width: Number(formState.widthCm),
            height: Number(formState.heightCm),
            price: Number(formState.weeklyPrice),
            dailyImpressions: Number(formState.dailyImpressions),
            schedule: buildScheduleFromForm(formState),
            imageUrl: formState.imageUrl,
            previewConfiguration: formState.previewConfiguration,
            venueId: formState.venueId
        };

        try {
            const created = await addMedia(payload);
            if (!created || created.id == null) throw new Error('Created media did not return an id');
            const newRow: MediaRowData = {
                id: String(created.id),
                name: formState.mediaTitle,
                image: created.imageUrl ?? null,
                adsDisplayed: 0,
                pending: 0,
                status: created.status ?? MediaStatusEnum.PENDING,
                timeUntil: "-",
                price: formState.weeklyPrice ? `$${Number(formState.weeklyPrice).toFixed(2)}` : "$0.00",
            };
            setMedia((prev) => [newRow, ...prev]);
            return created;
        } catch (err: unknown) {
            console.error("Failed to save media:", err);
            throw err;
        }
    };

    const editMedia = async (id: string | number, formState: MediaFormState) => {
        const payload: MediaRequestDTO = {
            title: formState.mediaTitle,
            mediaOwnerName: formState.mediaOwnerName,
            mediaLocationId: formState.mediaLocationId,
            resolution: formState.resolution,
            loopDuration: Number(formState.loopDuration),
            width: Number(formState.widthCm),
            height: Number(formState.heightCm),
            price: Number(formState.weeklyPrice),
            dailyImpressions: Number(formState.dailyImpressions),
            schedule: buildScheduleFromForm(formState),
            typeOfDisplay: formState.displayType,
            imageUrl: formState.imageUrl,
            previewConfiguration: formState.previewConfiguration,
            venueId: formState.venueId
        };

        try {
            const updated = await updateMedia(String(id), payload);
            setMedia((prev) =>
                prev.map((r) =>
                    String(r.id) === String(id)
                        ? {
                            ...r,
                            name: updated.title,
                            image: updated.imageUrl ?? r.image,
                            status: updated.status ?? r.status,
                            price: updated.price ? `${Number(updated.price).toFixed(2)}` : r.price,
                            venue: updated.venue ?? null
                        }
                        : r
                )
            );
            return updated;
        } catch (err: unknown) {
            console.error("Failed to update media:", err);
            throw err;
        }
    };

    const deleteMediaById = async (id: string | number) => {
        try {
            await deleteMedia(String(id));
            setMedia((prev) => prev.filter((r) => String(r.id) !== String(id)));
        } catch (err: unknown) {
            console.error("Failed to delete media:", err);
            throw err;
        }
    };

    const fetchMediaById = async (id: string | number) => {
        return getMediaById(String(id));
    };

    const toggleMediaStatus = async (
        id: string | number,
        nextStatus?: MediaStatusEnum.ACTIVE | MediaStatusEnum.INACTIVE
    ) => {
        const targetId = String(id);
        const currentRow = media.find((m) => String(m.id) === targetId);
        if (!currentRow) return;

        if (
            currentRow.status === MediaStatusEnum.PENDING ||
            currentRow.status === MediaStatusEnum.REJECTED
        ) {
            console.warn("Cannot toggle status while media is pending approval or rejected");
            return;
        }

        const currentStatus = currentRow.status;
        const finalNextStatus = nextStatus ?? (
            currentStatus === MediaStatusEnum.ACTIVE
                ? MediaStatusEnum.INACTIVE
                : MediaStatusEnum.ACTIVE
        );

        setMedia((prev) =>
            prev.map((m) => String(m.id) === targetId ? { ...m, status: finalNextStatus } : m)
        );

        try {
            const updated = await patchMediaStatus(targetId, { status: finalNextStatus });
            setMedia((prev) =>
                prev.map((m) =>
                    String(m.id) === targetId
                        ? { ...m, status: updated.status ?? finalNextStatus }
                        : m
                )
            );
            return finalNextStatus;
        } catch (err) {
            setMedia((prev) =>
                prev.map((m) => String(m.id) === targetId ? { ...m, status: currentStatus } : m)
            );
            throw err;
        }
    };

    return {
        media,
        addNewMedia,
        editMedia,
        deleteMediaById,
        fetchMediaById,
        toggleMediaStatus,
    };
}