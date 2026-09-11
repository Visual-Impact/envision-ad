import { Media } from "@/entities/media/@x/media-location";
import { AddressDetails } from "@/shared/lib/geolocation";

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
    /** True when latitude/longitude came from the media owner dropping a pin manually. */
    manualCoordinates?: boolean;
}

export type MediaLocationAddressFields = Pick<
    MediaLocationRequestDTO,
    "street" | "city" | "province" | "country" | "postalCode" | "latitude" | "longitude"
>;

/**
 * Maps a chosen Nominatim autocomplete result onto the address fields of a
 * media location form. Since the fields come straight from the geocoder's own
 * labeling, the backend can trust them without re-validating field-by-field.
 */
export function addressDetailsToLocationFields(details: AddressDetails): MediaLocationAddressFields {
    const address = details.address ?? {};
    const street = [address.house_number, address.road].filter(Boolean).join(" ");
    const city = address.city ?? address.town ?? address.village ?? address.suburb ?? address.county ?? "";
    const province = address.state ?? address.province ?? "";

    return {
        street,
        city,
        province,
        country: address.country ?? "",
        postalCode: address.postcode ?? "",
        latitude: details.lat,
        longitude: details.lng,
    };
}
