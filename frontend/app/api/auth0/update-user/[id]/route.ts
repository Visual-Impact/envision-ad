import { NextRequest, NextResponse } from 'next/server';
import { auth0 } from "@/shared/api/index.server";
import { Auth0ManagementService } from "@/shared/api/index.server";


export async function PATCH(
    request: NextRequest,
    { params }: { params: Promise<{ id: string }> }
) {
    const { id } = await params;

    // Ensure the user is authenticated and is updating their own profile
    const session = await auth0.getSession();
    if (!session || !session.user) {
        return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    // Verify the user is updating themselves (basic security check)
    // Decode ID to handle any URL encoded characters (e.g. pipes in auth0|123)
    const decodedId = decodeURIComponent(id);

    // Validate ID format (basic protection against injection)
    // Auth0 IDs typically look like "provider|id"; allow any non-whitespace characters while disallowing extra pipes in the provider part
    const idRegex = /^[^\s]+\|[^\s]+$/;
    if (!idRegex.test(decodedId)) {
        return NextResponse.json({ error: 'Invalid User ID format' }, { status: 400 });
    }

    if (session.user.sub !== decodedId) {
        return NextResponse.json({ error: 'Forbidden: You can only update your own profile' }, { status: 403 });
    }

    try {
        const body = await request.json();

        // Simple HTML-escaping sanitization to mitigate XSS when these values are rendered in HTML contexts.
        const sanitizeString = (value: string): string =>
            value
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;')
                .replace(/"/g, '&quot;')
                .replace(/'/g, '&#x27;')
                .replace(/\//g, '&#x2F;');

        // Validate that a field is a non-empty string, enforce a max length, and sanitize it.
        const validateAndSanitizeTextField = (value: unknown, maxLength: number): string | undefined => {
            if (typeof value !== 'string') {
                return undefined;
            }
            const trimmed = value.trim();
            if (!trimmed) {
                return undefined;
            }
            if (trimmed.length > maxLength) {
                return undefined;
            }
            return sanitizeString(trimmed);
        };

        const sanitizedBody: Record<string, unknown> = {};

        // Validations for specific fields (snake_case)
        if (body.given_name !== undefined) {
            const sanitized = validateAndSanitizeTextField(body.given_name, 100);
            if (sanitized !== undefined) sanitizedBody.given_name = sanitized;
        }

        if (body.family_name !== undefined) {
            const sanitized = validateAndSanitizeTextField(body.family_name, 100);
            if (sanitized !== undefined) sanitizedBody.family_name = sanitized;
        }

        if (body.nickname !== undefined) {
            const sanitized = validateAndSanitizeTextField(body.nickname, 100);
            if (sanitized !== undefined) sanitizedBody.nickname = sanitized;
        }

        if (body.name !== undefined) {
            const sanitized = validateAndSanitizeTextField(body.name, 200);
            if (sanitized !== undefined) sanitizedBody.name = sanitized;
        }

        if (body.user_metadata && typeof body.user_metadata === 'object') {
            const metadata: Record<string, unknown> = {};
            // Note: Only the 'bio' field from user_metadata is supported and forwarded to Auth0.
            if ('bio' in body.user_metadata) {
                const sanitizedBio = validateAndSanitizeTextField(
                    (body.user_metadata as Record<string, unknown>).bio as string,
                    1000
                );
                if (sanitizedBio !== undefined) {
                    metadata.bio = sanitizedBio;
                }
            }
            if (Object.keys(metadata).length > 0) {
                sanitizedBody.user_metadata = metadata;
            }
        }

        // Update User in Auth0 via Management Service (handles token caching)
        const updatedUserRaw = await Auth0ManagementService.updateUser(decodedId, sanitizedBody);

        return NextResponse.json(updatedUserRaw);
    } catch (err: unknown) {
        console.error("Error updating user:", err);
        return NextResponse.json({ error: (err as Error).message || 'Server error' }, { status: 500 });
    }
}
