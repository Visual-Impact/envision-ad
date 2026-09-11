import {RoleId} from "@/shared/lib/auth";

/**
 * Service for interacting with the Auth0 Management API.
 * Uses Client Credentials Grant to obtain an access token.
 */
export class Auth0ManagementService {
    /**
     * Retrieves an access token for the Auth0 Management API.
     * @returns {Promise<string>} The access token.
     * @throws {Error} If token retrieval fails.
     */
    private static cachedToken: string | null = null;
    private static tokenExpiration: number = 0;

    /**
     * Retrieves an access token for the Auth0 Management API.
     * Caches the token to avoid hitting rate limits.
     * @returns {Promise<string>} The access token.
     * @throws {Error} If token retrieval fails.
     */
    private static async getAccessToken() {
        // Return cached token if valid (providing a 60s buffer)
        if (this.cachedToken && Date.now() < this.tokenExpiration - 60000) {
            return this.cachedToken;
        }

        const domain = process.env.AUTH0_MGMT_DOMAIN;
        const clientId = process.env.AUTH0_MGMT_CLIENT_ID;
        const clientSecret = process.env.AUTH0_MGMT_CLIENT_SECRET;

        if (!domain || !clientId || !clientSecret) {
            throw new Error("Missing Auth0 Management API environment variables");
        }

        try {
            const tokenRes = await fetch(`https://${process.env.AUTH0_MGMT_DOMAIN}/oauth/token`, {
                method: 'POST',
                headers: { 'content-type': 'application/json' },
                body: JSON.stringify({
                    client_id: process.env.AUTH0_MGMT_CLIENT_ID,
                    client_secret: process.env.AUTH0_MGMT_CLIENT_SECRET,
                    audience: `https://${process.env.AUTH0_MGMT_DOMAIN}/api/v2/`,
                    grant_type: 'client_credentials',
                }),
            });

            if (!tokenRes.ok) {
                console.error(`Failed to get Auth0 access token: ${tokenRes.status} ${tokenRes.statusText}`);
                throw new Error(`Failed to get Auth0 access token: ${tokenRes.statusText}`);
            }

            const data = await tokenRes.json();
            if (!data.access_token) {
                console.error("Auth0 token response missing access_token:", data);
                throw new Error("Auth0 token response missing access_token");
            }
            // Cache the token
            this.cachedToken = data.access_token;
            // expires_in is in seconds, convert to ms and set expiration
            // If expires_in is not returned, default to 24 hours (86400s) safely or just don't cache deeply if worried. 
            // Standard Auth0 response includes expires_in.
            const expiresIn = data.expires_in || 3600;
            this.tokenExpiration = Date.now() + (expiresIn * 1000);

            return data.access_token;
        } catch (error) {
            console.error("Error in Auth0ManagementService.getAccessToken:", error);
            throw error;
        }
    }

    private static userCache: Record<string, { data: unknown; expires: number }> = {};
    private static CACHE_DURATION = 300000; // 5 minutes in ms

    /**
     * Retrieves a user's details from Auth0.
     * @param {string} userId - The unique identifier of the user (e.g., "auth0|123456").
     * @returns {Promise<any | null>} The user object if found, or null if not found or an error occurs.
     */
    static async getUser(userId: string): Promise<{ data: unknown; isStale: boolean } | null> {
        // Return cached user if valid
        const cached = this.userCache[userId];
        if (cached && Date.now() < cached.expires) {
            return { data: cached.data, isStale: false };
        }

        try {
            const token = await this.getAccessToken();
            const res = await fetch(
                `https://${process.env.AUTH0_MGMT_DOMAIN}/api/v2/users/${userId}`,
                {
                    headers: {
                        Authorization: `Bearer ${token}`,
                    },
                    // cache: 'no-store' removed to allow implementation of manual caching
                }
            );

            if (!res.ok) {
                // If 429, try to return stale cache if available
                if (res.status === 429 && cached) {
                    console.warn(`Rate limited for user ${userId}, returning stale cache`);
                    return { data: cached.data, isStale: true };
                }
                console.error(`Failed to fetch user ${userId}: ${res.statusText}`);
                throw new Error(`Failed to fetch user ${userId}: ${res.statusText}`);
            }

            const data = await res.json();

            // Update cache
            this.userCache[userId] = {
                data,
                expires: Date.now() + this.CACHE_DURATION
            };

            return { data, isStale: false };
        } catch (error) {
            console.error("Error in Auth0ManagementService.getUserClient:", error);
            // Fallback to stale cache on error if possible
            if (this.userCache[userId]) {
                return { data: this.userCache[userId].data, isStale: true };
            }
            return null;
        }
    }

    /**
     * Updates a user's details in Auth0.
     * @param {string} userId - The unique identifier of the user.
     * @param {any} data - The partial user object containing fields to update.
     * @returns {Promise<any>} The updated user object.
     * @throws {Error} If the update fails.
     */
    static async updateUser(userId: string, data: unknown) {
        const token = await this.getAccessToken();
        const res = await fetch(
            `https://${process.env.AUTH0_MGMT_DOMAIN}/api/v2/users/${userId}`,
            {
                method: 'PATCH',
                headers: {
                    'Content-Type': 'application/json',
                    Authorization: `Bearer ${token}`,
                },
                body: JSON.stringify(data),
            }
        );

        if (!res.ok) {
            let errorMsg = res.statusText || 'Auth0 Update Failed';
            try {
                const errorBody = await res.json();
                errorMsg = errorBody.message || errorBody.error_description || errorBody.error || errorMsg;
            } catch {
                // ignore JSON parse errors
            }
            throw new Error(errorMsg);
        }

        const updatedData = await res.json();

        // Update cache with new data
        this.userCache[userId] = {
            data: updatedData,
            expires: Date.now() + this.CACHE_DURATION
        };

        return updatedData;
    }

    /**
     * Updates a user's preferred language in their metadata.
     * @param {string} userId - The unique identifier of the user.
     * @param {string} locale - The preferred language code (e.g., "en" or "fr").
     * @returns {Promise<any>} The updated user object.
     * @throws {Error} If the update fails.
     */
    static async updateUserLanguage(userId: string, locale: string) {
        return this.updateUser(userId, {
            user_metadata: {
                preferred_language: locale,
            },
        });
    }

    /**
     * Updates a user's role by adding or removing it.
     * @param {string} userId - The unique identifier of the user.
     * @param {RoleId[]} roles - The id of the roles to add or remove (e.g., "rol_fFGTiHiGm6EV36pD").
     * @param {'POST' | 'DELETE'} action - The action to perform: 'post' to assign the role, 'delete' to revoke it.
     * @returns {Promise<void>}
     * @throws {Error} If the role update fails.
     */
    static async updateUserRole(userId: string, roles: RoleId[], action: 'POST' | 'DELETE') {
        const token = await this.getAccessToken();

        await fetch(
            `https://${process.env.AUTH0_DOMAIN}/api/v2/users/${userId}/roles`,
            {
                method: action,
                headers: {
                    Authorization: `Bearer ${token}`,
                    'Content-Type': 'application/json',
                },
                body: JSON.stringify({ roles: roles }),
            }
        );
    }

    /**
     * Retrieves a user's preferred language from their metadata.
     * @param {string} userId - The unique identifier of the user.
     * @returns {Promise<string | undefined>} The preferred language code, or undefined if not set.
     */
    static async getUserLanguage(userId: string): Promise<string | undefined> {
        const user = await this.getUser(userId);
        const data = user?.data as { user_metadata?: { preferred_language?: string } } | null;
        return data?.user_metadata?.preferred_language || undefined;
    }
}
