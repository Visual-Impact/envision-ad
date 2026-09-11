import type { LatLngBounds, LatLngLiteral } from "leaflet";
import {MediaLocation} from "@/entities/media-location/@x/media";
import {Venue} from "@/entities/venue/@x/media";

export enum MediaStatusEnum {
    ACTIVE = "ACTIVE",
    INACTIVE = "INACTIVE",
    PENDING = "PENDING",
    REJECTED = "REJECTED",
}


export interface Media {
    id?: string;
    title: string;
    mediaOwnerName: string;
    mediaLocation: MediaLocation;
    resolution: string;
    loopDuration: number | null;
    width: number | null;
    height: number | null;
    price: number;
    dailyImpressions: number | null;
    schedule: MonthlyScheduleModel;
    status: MediaStatusEnum;
    typeOfDisplay: string;
    imageUrl: string;
    previewConfiguration: string;
    businessId: string;
    businessName: string;
    venue?: Venue | null;
}

export interface MediaRequestDTO {
    title: string;
    mediaOwnerName: string;
    mediaLocationId: string | null;
    typeOfDisplay: string | null;
    loopDuration: number;
    resolution: string;
    width: number;
    height: number;
    price: number;
    dailyImpressions: number;
    schedule: MonthlyScheduleModel | null;
    imageUrl: string | null;
    previewConfiguration: string | null;
    venueId?: string | null;
}

export interface MediaListResponseDTO {
    content: Media[];
    totalElements: number;
    totalPages: number;
    number: number,
    size: number,
    last: boolean;
    first: boolean;
    empty: boolean;
}

export interface FilteredActiveMediaProps {
    title?: string | null,
    businessId?: string | null,
    minPrice?: number | null,
    maxPrice?: number | null,
    minWeeklyImpressions?: number | null,
    sort?: string | null,
    latLng?: LatLngLiteral | null,
    bounds?: LatLngBounds | null,
    excludedId?: string | null,
    venueIds?: string[] | null,
    page?: number,
    size?: number
}

export interface MediaStatusPatchDTO {
    status: MediaStatusEnum;
}

export interface MonthlyScheduleModel {
    selectedMonths: string[];
    weeklySchedule: WeeklyScheduleModel[];
}

export interface WeeklyScheduleModel {
    dayOfWeek: string;
    isActive: boolean;
    startTime: string | null;
    endTime: string | null;
}

export function getJoinedAddress(items: (string | undefined)[]) {
    return items.join(", ");
}

export enum MediaAdStatuses {
    PENDING = "PENDING",
    DISPLAYING = "DISPLAYING",
}

export const MediaAdStatusMap = {
    [MediaAdStatuses.PENDING]: {
        color: "yellow",
        text: "Pending",
    },
    [MediaAdStatuses.DISPLAYING]: {
        color: "blue",
        text: "Displaying",
    },
} as const;

export type MediaStatus = 'idle' | 'loading' | 'success' | 'empty' | 'error';

export interface UseMediaListProps {
    filteredMediaProps: FilteredActiveMediaProps,
    loadingLocation?: boolean
    setMediaStatus?: React.Dispatch<React.SetStateAction<MediaStatus>>
}