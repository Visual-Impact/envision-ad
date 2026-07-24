import { Media } from "@/entities/media";

export interface MediaLocation {
    id: string;
    businessId: string;
    name: string;
    country: string;
    province: string;
    city: string;
    /** Optional free-text region, used for bundle REGION rule matching. */
    region?: string;
    street: string;
    postalCode: string;
    latitude: number;
    longitude: number;
    mediaList?: Media[];
}

export interface MediaLocationRequestDTO {
    name: string;
    country: string;
    province: string;
    city: string;
    region?: string;
    street: string;
    postalCode: string;
    latitude: number;
    longitude: number;
    businessId?: string;
}
