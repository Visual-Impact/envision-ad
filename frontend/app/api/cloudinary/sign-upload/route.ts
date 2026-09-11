import { v2 as cloudinary } from 'cloudinary';
import { NextResponse } from 'next/server';
import { auth0 } from "@/shared/api/index.server";

// Allowed keys for a standard upload to prevent signing unauthorized params
const ALLOWED_CLOUDINARY_KEYS = ['timestamp', 'source', 'upload_preset', 'public_id', 'folder', 'custom_coordinates'];
export async function POST(request: Request) {
    // 1. Authorization Guard: Ensure the user is logged in
    const session = await auth0.getSession();
    if (!session || !session.user) {
        return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    try {
        const body = await request.json();
        const { paramsToSign } = body as { paramsToSign: Record<string, string> };

        if (!paramsToSign || typeof paramsToSign !== 'object') {
            return NextResponse.json({ error: 'Invalid paramsToSign' }, { status: 400 });
        }

        // 2. Input Validation: Filter out any keys not in our whitelist
        const filteredParams: Record<string, string> = {};
        for (const key of ALLOWED_CLOUDINARY_KEYS) {
            if (paramsToSign[key] !== undefined) {
                // Sanitize: Ensure the value is a string and not an object/array
                filteredParams[key] = String(paramsToSign[key]);
            }
        }

        const apiSecret = process.env.CLOUDINARY_API_SECRET;
        if (!apiSecret) {
            console.error("CLOUDINARY_API_SECRET is missing");
            return NextResponse.json({ error: "Server configuration error" }, { status: 500 });
        }

        // 3. Signing: Only sign the validated/filtered parameters
        const signature = cloudinary.utils.api_sign_request(filteredParams, apiSecret);

        return NextResponse.json({ signature });
    } catch (err) {
        console.error("Signature error:", err);
        return NextResponse.json({ error: 'Failed to generate signature' }, { status: 500 });
    }
}