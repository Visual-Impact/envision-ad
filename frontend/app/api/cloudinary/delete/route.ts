import { v2 as cloudinary } from 'cloudinary';
import { NextResponse } from 'next/server';
import { auth0 } from "@/shared/api/auth0/auth0";

const GALLERY_KEY = 'homepage-gallery';

function backendBaseUrl(): string | undefined {
    return process.env.DOCKER === "true"
        ? process.env.WEBSERVICE_API_URL
        : process.env.NEXT_PUBLIC_API_URL;
}

// Pull the stored public_ids out of the gallery setting JSON. Folder-mode
// agnostic: we only ever destroy an id we ourselves persisted, so this works
// whether Cloudinary returns "homepage-gallery/abc" or a bare "abc".
function galleryPublicIds(value: unknown): string[] {
    if (typeof value !== "string" || !value) return [];
    try {
        const parsed = JSON.parse(value);
        if (!Array.isArray(parsed)) return [];
        return parsed
            .filter((i) => i && typeof i.publicId === "string")
            .map((i) => i.publicId as string);
    } catch {
        return [];
    }
}

export async function POST(request: Request) {
    // Authorization guard — mirror the sign-upload route (must be logged in).
    const session = await auth0.getSession();
    if (!session || !session.user) {
        return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    let publicId: string;
    try {
        const body = await request.json();
        publicId = String((body as { publicId?: string }).publicId ?? '');
    } catch {
        return NextResponse.json({ error: 'Invalid request body' }, { status: 400 });
    }
    if (!publicId) {
        return NextResponse.json({ error: 'publicId is required' }, { status: 400 });
    }

    // Security guard: only assets currently referenced by the homepage gallery
    // may be destroyed. The client deletes BEFORE persisting the removal, so the
    // id is still present in the setting at this point.
    try {
        const res = await fetch(`${backendBaseUrl()}/settings/${GALLERY_KEY}`, { cache: "no-store" });
        const value = res.ok ? (await res.json()).value : null;
        if (!galleryPublicIds(value).includes(publicId)) {
            return NextResponse.json({ error: 'publicId is not a current homepage gallery asset' }, { status: 403 });
        }
    } catch (err) {
        console.error("[gallery-delete] could not verify gallery membership:", err);
        return NextResponse.json({ error: 'Could not verify gallery membership' }, { status: 502 });
    }

    const cloudName = process.env.CLOUDINARY_CLOUD_NAME ?? process.env.NEXT_PUBLIC_CLOUDINARY_CLOUD_NAME;
    const apiKey = process.env.CLOUDINARY_API_KEY ?? process.env.NEXT_PUBLIC_CLOUDINARY_API_KEY;
    const apiSecret = process.env.CLOUDINARY_API_SECRET;
    if (!cloudName || !apiKey || !apiSecret) {
        console.error("Cloudinary server credentials are missing (cloud name / api key / api secret)");
        return NextResponse.json({ error: "Server configuration error" }, { status: 500 });
    }

    cloudinary.config({ cloud_name: cloudName, api_key: apiKey, api_secret: apiSecret });

    try {
        const result = await cloudinary.uploader.destroy(publicId, { invalidate: true });
        console.log(`[gallery-delete] destroy "${publicId}" -> ${result.result}`);
        // "not found" is safe here (membership was verified): it means the asset
        // was already destroyed, so a retry is idempotent. Any other status is a
        // real failure worth surfacing rather than masking as success.
        if (result.result !== "ok" && result.result !== "not found") {
            return NextResponse.json({ error: `Cloudinary returned "${result.result}"` }, { status: 502 });
        }
        return NextResponse.json({ result: result.result });
    } catch (err) {
        console.error("Cloudinary destroy error:", err);
        return NextResponse.json({ error: 'Failed to delete image' }, { status: 500 });
    }
}
