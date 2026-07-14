import { getAppSetting } from "./getAppSetting";
import { updateAppSetting } from "./updateAppSetting";

export const HOMEPAGE_GALLERY_KEY = "homepage-gallery";

export interface GalleryImage {
    /** Cloudinary secure_url, rendered directly in the gallery. */
    url: string;
    /** Cloudinary public_id, used to delete the asset. Always under the
     *  `homepage-gallery/` folder for admin-uploaded images. */
    publicId: string;
}

/** Parse the stored JSON value into a clean, validated list. Malformed or
 *  missing values yield an empty list rather than throwing. */
export function parseGalleryImages(value: string | null | undefined): GalleryImage[] {
    if (!value) return [];
    try {
        const parsed = JSON.parse(value);
        if (!Array.isArray(parsed)) return [];
        return parsed
            .filter((i): i is GalleryImage =>
                i && typeof i.url === "string" && typeof i.publicId === "string")
            .map((i) => ({ url: i.url, publicId: i.publicId }));
    } catch {
        return [];
    }
}

/**
 * Bake the admin's crop into the Cloudinary delivery URL.
 *
 * Signed uploads don't physically crop the stored master, so the widget's crop
 * selection is only honoured if we apply it on delivery. When the widget returns
 * crop coordinates ([x, y, w, h]) we insert a `c_crop` transformation for that
 * exact region; otherwise we fall back to a smart 3:4 fill so the card ratio is
 * always respected. `insertAt` sits right after `/upload/`, before any version.
 */
export function applyGalleryCrop(secureUrl: string, coords?: number[] | null): string {
    const marker = "/upload/";
    const idx = secureUrl.indexOf(marker);
    if (idx === -1) return secureUrl;
    const insertAt = idx + marker.length;

    let transform: string;
    if (coords && coords.length === 4 && coords.every((n) => Number.isFinite(n))) {
        const [x, y, w, h] = coords.map((n) => Math.max(0, Math.round(n)));
        transform = `c_crop,x_${x},y_${y},w_${w},h_${h}/c_fill,ar_3:4,w_800`;
    } else {
        transform = "c_fill,ar_3:4,w_800,g_auto";
    }

    return secureUrl.slice(0, insertAt) + transform + "/" + secureUrl.slice(insertAt);
}

/** Client-side read of the current gallery (admin dashboard). */
export async function getGalleryImages(): Promise<GalleryImage[]> {
    const value = await getAppSetting(HOMEPAGE_GALLERY_KEY);
    return parseGalleryImages(value);
}

/** Persist the ordered gallery list. */
export async function saveGalleryImages(images: GalleryImage[]): Promise<void> {
    await updateAppSetting(HOMEPAGE_GALLERY_KEY, JSON.stringify(images));
}

/** Permanently delete a gallery asset from Cloudinary via the signed route. */
export async function deleteGalleryImageAsset(publicId: string): Promise<void> {
    const res = await fetch("/api/cloudinary/delete", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ publicId }),
    });
    if (!res.ok) {
        throw new Error(`Failed to delete image asset (${res.status})`);
    }
}
