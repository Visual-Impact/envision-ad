import { Page } from "@playwright/test";

import HomePage from "../../../support/page-object/pages/home.page";
import MediaDashboardPage from "../../../support/page-object/pages/mediaDashboard.page";

export type MediaStatus = "ACTIVE" | "PENDING" | "REJECTED" | "INACTIVE";

export interface MockMedia {
    id: string;
    title: string;
    status: MediaStatus;
    price: number;
    dailyImpressions: number;
    imageUrl: string;
    mediaOwnerName: string;
    mediaLocationId: string;
    typeOfDisplay: "DIGITAL" | "POSTER";
    loopDuration: number;
    resolution: string;
    width: number;
    height: number;
    schedule: {
        selectedMonths: string[];
        weeklySchedule: Array<{
            dayOfWeek: string;
            isActive: boolean;
            startTime: string | null;
            endTime: string | null;
        }>;
    };
    previewConfiguration: string;
}

export interface MockLocation {
    id: string;
    businessId: string;
    name: string;
    country: string;
    province: string;
    city: string;
    street: string;
    postalCode: string;
    latitude: number;
    longitude: number;
}

export interface MediaCrudMockState {
    locationStore: MockLocation[];
    mediaStore: MockMedia[];
    capturedCreateLocationPayload: Record<string, unknown> | null;
    capturedUpdateLocationPayload: Record<string, unknown> | null;
    capturedDeletedLocationIds: string[];
    capturedCreatePayload: Record<string, unknown> | null;
    capturedUpdatePayload: Record<string, unknown> | null;
    capturedDeletedIds: string[];
}

interface MediaCrudMockOptions {
    seedLocations?: boolean;
    seedMedia?: boolean;
}

interface MediaCrudRouteOptions {
    tokenPermissions?: string[];
    invalidAddressOnCreateLocation?: boolean;
    denyReadMediaLocations?: boolean;
    invalidPayloadOnUpdate?: boolean;
}

export const BUSINESS_ID = "biz-visual-impact";
export const DEFAULT_MEDIA_OWNER = "Visual Impact";
const E2E_MOCK_PERMISSIONS = [
    "read:media",
    "create:media",
    "read:campaign",
    "read:employee",
];

const createMockJwt = (permissions: string[]) => {
    const header = Buffer.from(JSON.stringify({ alg: "none", typ: "JWT" })).toString("base64url");
    const payload = Buffer.from(
        JSON.stringify({
            sub: "auth0|e2e-media-user",
            permissions,
            scope: permissions.join(" "),
            iat: Math.floor(Date.now() / 1000),
            exp: Math.floor(Date.now() / 1000) + 60 * 60,
        })
    ).toString("base64url");

    return `${header}.${payload}.signature`;
};

export const createSeedLocations = (): MockLocation[] => [
    {
        id: "loc-montreal",
        businessId: BUSINESS_ID,
        name: "Champlain College Saint-Lambert",
        country: "Canada",
        province: "Quebec",
        city: "Saint-Lambert",
        street: "900 Riverside Dr",
        postalCode: "J4P 3P2",
        latitude: 45.4928,
        longitude: -73.5059,
    },
    {
        id: "loc-brossard",
        businessId: BUSINESS_ID,
        name: "Mail Champlain",
        country: "Canada",
        province: "Quebec",
        city: "Brossard",
        street: "2151 Bd Lapiniere",
        postalCode: "J4W 2T5",
        latitude: 45.4449,
        longitude: -73.4377,
    },
];

export const createSeedMedia = (): MockMedia[] => [
    {
        id: "media-1",
        title: "Montreal Downtown Wrap",
        status: "ACTIVE",
        price: 333.77,
        dailyImpressions: 12000,
        imageUrl: "https://res.cloudinary.com/demo/image/upload/v1/media-1.jpg",
        mediaOwnerName: DEFAULT_MEDIA_OWNER,
        mediaLocationId: "loc-montreal",
        typeOfDisplay: "DIGITAL",
        loopDuration: 15,
        resolution: "1920x1080",
        width: 0,
        height: 0,
        schedule: {
            selectedMonths: ["January", "February", "March"],
            weeklySchedule: [
                { dayOfWeek: "monday", isActive: true, startTime: "09:00", endTime: "17:00" },
                { dayOfWeek: "tuesday", isActive: true, startTime: "09:00", endTime: "17:00" },
                { dayOfWeek: "wednesday", isActive: true, startTime: "09:00", endTime: "17:00" },
                { dayOfWeek: "thursday", isActive: true, startTime: "09:00", endTime: "17:00" },
                { dayOfWeek: "friday", isActive: true, startTime: "09:00", endTime: "17:00" },
                { dayOfWeek: "saturday", isActive: false, startTime: null, endTime: null },
                { dayOfWeek: "sunday", isActive: false, startTime: null, endTime: null },
            ],
        },
        previewConfiguration: JSON.stringify({
            tl: { x: 0, y: 0 },
            tr: { x: 100, y: 0 },
            br: { x: 100, y: 100 },
            bl: { x: 0, y: 100 },
        }),
    },
    {
        id: "media-2",
        title: "Brossard Fitness Display",
        status: "PENDING",
        price: 199.99,
        dailyImpressions: 8500,
        imageUrl: "https://res.cloudinary.com/demo/image/upload/v1/media-2.jpg",
        mediaOwnerName: DEFAULT_MEDIA_OWNER,
        mediaLocationId: "loc-brossard",
        typeOfDisplay: "DIGITAL",
        loopDuration: 12,
        resolution: "1080x1920",
        width: 0,
        height: 0,
        schedule: {
            selectedMonths: ["January", "February", "March"],
            weeklySchedule: [
                { dayOfWeek: "monday", isActive: true, startTime: "08:00", endTime: "18:00" },
                { dayOfWeek: "tuesday", isActive: true, startTime: "08:00", endTime: "18:00" },
                { dayOfWeek: "wednesday", isActive: true, startTime: "08:00", endTime: "18:00" },
                { dayOfWeek: "thursday", isActive: true, startTime: "08:00", endTime: "18:00" },
                { dayOfWeek: "friday", isActive: true, startTime: "08:00", endTime: "18:00" },
                { dayOfWeek: "saturday", isActive: false, startTime: null, endTime: null },
                { dayOfWeek: "sunday", isActive: false, startTime: null, endTime: null },
            ],
        },
        previewConfiguration: JSON.stringify({
            tl: { x: 0, y: 0 },
            tr: { x: 100, y: 0 },
            br: { x: 100, y: 100 },
            bl: { x: 0, y: 100 },
        }),
    },
];

export const createMediaCrudMockState = (
    options: MediaCrudMockOptions = {}
): MediaCrudMockState => {
    const seedLocations = options.seedLocations ?? true;
    const seedMedia = options.seedMedia ?? true;
    const locationStore = seedLocations ? createSeedLocations() : [];
    const mediaStore = seedMedia ? createSeedMedia() : [];

    return {
        locationStore,
        mediaStore,
        capturedCreateLocationPayload: null,
        capturedUpdateLocationPayload: null,
        capturedDeletedLocationIds: [],
        capturedCreatePayload: null,
        capturedUpdatePayload: null,
        capturedDeletedIds: [],
    };
};

const toLocationWithMediaResponse = (location: MockLocation, mediaStore: MockMedia[]) => ({
    ...location,
    mediaList: mediaStore
        .filter((media) => media.mediaLocationId === location.id)
        .map((media) => ({
            id: media.id,
            title: media.title,
            status: media.status,
            price: media.price,
            imageUrl: media.imageUrl,
        })),
});

const toMediaResponse = (media: MockMedia, locationStore: MockLocation[]) => {
    const location =
        locationStore.find((item) => item.id === media.mediaLocationId) ?? {
            id: media.mediaLocationId,
            businessId: BUSINESS_ID,
            name: "Unknown Location",
            country: "Canada",
            province: "Quebec",
            city: "Montreal",
            postalCode: "H1H1H1",
            street: "123 Main St",
            latitude: 45.5017,
            longitude: -73.5673,
        };

    return {
        id: media.id,
        title: media.title,
        mediaOwnerName: media.mediaOwnerName,
        mediaLocation: location,
        resolution: media.resolution,
        loopDuration: media.loopDuration,
        width: media.width,
        height: media.height,
        price: media.price,
        dailyImpressions: media.dailyImpressions,
        schedule: media.schedule,
        status: media.status,
        typeOfDisplay: media.typeOfDisplay,
        imageUrl: media.imageUrl,
        previewConfiguration: media.previewConfiguration,
        businessId: BUSINESS_ID,
    };
};

const toMediaListResponse = (media: MockMedia) => ({
    id: media.id,
    title: media.title,
    status: media.status,
    price: media.price,
    imageUrl: media.imageUrl,
});

export const attachMediaCrudRoutes = async (
    page: Page,
    state: MediaCrudMockState,
    options: MediaCrudRouteOptions = {}
) => {
    const tokenPermissions = options.tokenPermissions ?? E2E_MOCK_PERMISSIONS;
    const invalidAddressOnCreateLocation = options.invalidAddressOnCreateLocation ?? false;
    const denyReadMediaLocations = options.denyReadMediaLocations ?? false;
    const invalidPayloadOnUpdate = options.invalidPayloadOnUpdate ?? false;

    await page.route("**/api/auth0/token", async (route) => {
        try {
            const response = await route.fetch();
            if (response.ok()) {
                await route.fulfill({ response });
                return;
            }
        } catch {
            // Fall back to a deterministic JWT-shaped token for local test runs.
        }

        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({ accessToken: createMockJwt(tokenPermissions) }),
        });
    });

    await page.route("**/api/cloudinary/sign-upload", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
                signature: "mock-signature",
                timestamp: Math.floor(Date.now() / 1000),
            }),
        });
    });

    await page.route("https://api.cloudinary.com/v1_1/**/image/upload", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify({
                secure_url: "https://res.cloudinary.com/demo/image/upload/v1/new-media.jpg",
                public_id: "new-media-id",
            }),
        });
    });

    await page.route("https://nominatim.openstreetmap.org/search**", async (route) => {
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: JSON.stringify([
                {
                    display_name:
                        "1001 Boul De Maisonneuve E, Montreal, Quebec, Canada, H2L 4P9",
                    lat: "45.5150",
                    lon: "-73.5600",
                    address: {
                        road: "Boul De Maisonneuve E",
                        house_number: "1001",
                        city: "Montreal",
                        state: "Quebec",
                        country: "Canada",
                    },
                },
            ]),
        });
    });

    await page.route("**/api/v1/**", async (route) => {
        const request = route.request();
        const method = request.method();
        const url = new URL(request.url());
        const apiPrefixIndex = url.pathname.indexOf("/api/v1");
        const apiPath =
            apiPrefixIndex >= 0
                ? url.pathname.slice(apiPrefixIndex + "/api/v1".length)
                : url.pathname;

        if (method === "GET" && apiPath.startsWith("/businesses/employee/")) {
            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify({
                    businessId: BUSINESS_ID,
                    name: DEFAULT_MEDIA_OWNER,
                }),
            });
            return;
        }

        if (method === "GET" && apiPath === "/payments/account-status") {
            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify({
                    connected: true,
                    onboardingComplete: true,
                    chargesEnabled: true,
                    payoutsEnabled: true,
                }),
            });
            return;
        }

        if (method === "GET" && apiPath === `/businesses/${BUSINESS_ID}/media`) {
            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify(state.mediaStore.map(toMediaListResponse)),
            });
            return;
        }

        if (method === "GET" && apiPath === "/media-locations") {
            if (denyReadMediaLocations) {
                await route.fulfill({
                    status: 403,
                    contentType: "application/json",
                    body: JSON.stringify({ message: "Forbidden" }),
                });
                return;
            }

            const businessId = url.searchParams.get("businessId");
            const filteredLocations = businessId
                ? state.locationStore.filter((location) => location.businessId === businessId)
                : state.locationStore;

            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify(
                    filteredLocations.map((location) =>
                        toLocationWithMediaResponse(location, state.mediaStore)
                    )
                ),
            });
            return;
        }

        if (method === "POST" && apiPath === "/media-locations") {
            const payload = request.postDataJSON() as Record<string, unknown>;
            state.capturedCreateLocationPayload = payload;

            if (invalidAddressOnCreateLocation) {
                await route.fulfill({
                    status: 400,
                    contentType: "application/json",
                    body: JSON.stringify({
                        message: "Address could not be verified.",
                        fieldErrors: {
                            street: "Verify the street name or number.",
                            city: "Verify the city value.",
                            province: "Verify the province/state value.",
                            country: "Verify the country value.",
                            postalCode: "Verify the postal code value.",
                        },
                    }),
                });
                return;
            }

            const newLocation: MockLocation = {
                id: `loc-created-${Date.now()}`,
                businessId: String(payload.businessId ?? BUSINESS_ID),
                name: String(payload.name ?? "New Location"),
                country: String(payload.country ?? "Canada"),
                province: String(payload.province ?? "Quebec"),
                city: String(payload.city ?? "Montreal"),
                street: String(payload.street ?? "123 Main St"),
                postalCode: String(payload.postalCode ?? "H1H1H1"),
                latitude: Number(payload.latitude ?? 45.5017),
                longitude: Number(payload.longitude ?? -73.5673),
            };

            state.locationStore = [newLocation, ...state.locationStore];

            await route.fulfill({
                status: 201,
                contentType: "application/json",
                body: JSON.stringify(toLocationWithMediaResponse(newLocation, state.mediaStore)),
            });
            return;
        }

        if (method === "PUT" && apiPath.startsWith("/media-locations/")) {
            const id = apiPath.split("/").at(-1) ?? "";
            const payload = request.postDataJSON() as Record<string, unknown>;
            state.capturedUpdateLocationPayload = payload;

            const existingLocation = state.locationStore.find((location) => location.id === id);
            if (!existingLocation) {
                await route.fulfill({
                    status: 404,
                    contentType: "application/json",
                    body: JSON.stringify({ message: "Media location not found" }),
                });
                return;
            }

            const updatedLocation: MockLocation = {
                ...existingLocation,
                businessId: String(payload.businessId ?? existingLocation.businessId),
                name: String(payload.name ?? existingLocation.name),
                country: String(payload.country ?? existingLocation.country),
                province: String(payload.province ?? existingLocation.province),
                city: String(payload.city ?? existingLocation.city),
                street: String(payload.street ?? existingLocation.street),
                postalCode: String(payload.postalCode ?? existingLocation.postalCode),
                latitude: Number(payload.latitude ?? existingLocation.latitude),
                longitude: Number(payload.longitude ?? existingLocation.longitude),
            };

            state.locationStore = state.locationStore.map((location) =>
                location.id === id ? updatedLocation : location
            );

            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify(
                    toLocationWithMediaResponse(updatedLocation, state.mediaStore)
                ),
            });
            return;
        }

        if (method === "DELETE" && apiPath.startsWith("/media-locations/")) {
            const id = apiPath.split("/").at(-1) ?? "";
            const hasAssignedBlockingMedia = state.mediaStore.some(
                (media) =>
                    media.mediaLocationId === id &&
                    (media.status === "ACTIVE" ||
                        media.status === "PENDING" ||
                        media.status === "REJECTED")
            );

            if (hasAssignedBlockingMedia) {
                await route.fulfill({
                    status: 409,
                    contentType: "application/json",
                    body: JSON.stringify({
                        message:
                            "Cannot delete this location because it has active, pending, or rejected media assigned. Please delete the media first.",
                    }),
                });
                return;
            }

            state.capturedDeletedLocationIds.push(id);
            state.locationStore = state.locationStore.filter(
                (location) => location.id !== id
            );

            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify({}),
            });
            return;
        }

        if (method === "GET" && apiPath.startsWith("/media/")) {
            const id = apiPath.split("/").at(-1);
            const item = state.mediaStore.find((media) => media.id === id);
            if (!item) {
                await route.fulfill({
                    status: 404,
                    contentType: "application/json",
                    body: JSON.stringify({ message: "Media not found" }),
                });
                return;
            }
            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify(toMediaResponse(item, state.locationStore)),
            });
            return;
        }

        if (method === "POST" && apiPath === "/media") {
            const payload = request.postDataJSON() as Record<string, unknown>;
            state.capturedCreatePayload = payload;
            const mediaLocationId = String(payload.mediaLocationId ?? "");
            const matchingLocation = state.locationStore.find(
                (location) => location.id === mediaLocationId
            );

            if (!matchingLocation) {
                await route.fulfill({
                    status: 400,
                    contentType: "application/json",
                    body: JSON.stringify({
                        message: "mediaLocationId must reference an existing location",
                    }),
                });
                return;
            }

            const newMedia: MockMedia = {
                id: `media-created-${Date.now()}`,
                title: String(payload.title ?? "Untitled"),
                status: "PENDING",
                price: Number(payload.price ?? 0),
                dailyImpressions: Number(payload.dailyImpressions ?? 0),
                imageUrl: String(payload.imageUrl ?? ""),
                mediaOwnerName: String(payload.mediaOwnerName ?? DEFAULT_MEDIA_OWNER),
                mediaLocationId,
                typeOfDisplay:
                    String(payload.typeOfDisplay ?? "DIGITAL").toUpperCase() === "POSTER"
                        ? "POSTER"
                        : "DIGITAL",
                loopDuration: Number(payload.loopDuration ?? 0),
                resolution: String(payload.resolution ?? ""),
                width: Number(payload.width ?? 0),
                height: Number(payload.height ?? 0),
                schedule: (payload.schedule as MockMedia["schedule"]) ?? {
                    selectedMonths: [],
                    weeklySchedule: [],
                },
                previewConfiguration: String(payload.previewConfiguration ?? ""),
            };

            state.mediaStore = [newMedia, ...state.mediaStore];

            await route.fulfill({
                status: 201,
                contentType: "application/json",
                body: JSON.stringify(toMediaResponse(newMedia, state.locationStore)),
            });
            return;
        }

        if (method === "PUT" && apiPath.startsWith("/media/")) {
            const id = apiPath.split("/").at(-1) ?? "";
            const payload = request.postDataJSON() as Record<string, unknown>;
            state.capturedUpdatePayload = payload;

            if (invalidPayloadOnUpdate) {
                await route.fulfill({
                    status: 400,
                    contentType: "application/json",
                    body: JSON.stringify({ message: "Invalid media payload." }),
                });
                return;
            }

            const existingMedia = state.mediaStore.find((media) => media.id === id);
            if (!existingMedia) {
                await route.fulfill({
                    status: 404,
                    contentType: "application/json",
                    body: JSON.stringify({ message: "Media not found" }),
                });
                return;
            }

            const updatedMedia: MockMedia = {
                ...existingMedia,
                title: String(payload.title ?? existingMedia.title),
                mediaOwnerName: String(payload.mediaOwnerName ?? existingMedia.mediaOwnerName),
                mediaLocationId: String(payload.mediaLocationId ?? existingMedia.mediaLocationId),
                typeOfDisplay:
                    String(payload.typeOfDisplay ?? existingMedia.typeOfDisplay).toUpperCase() ===
                    "POSTER"
                        ? "POSTER"
                        : "DIGITAL",
                loopDuration: Number(payload.loopDuration ?? existingMedia.loopDuration),
                resolution: String(payload.resolution ?? existingMedia.resolution),
                width: Number(payload.width ?? existingMedia.width),
                height: Number(payload.height ?? existingMedia.height),
                price: Number(payload.price ?? existingMedia.price),
                dailyImpressions: Number(payload.dailyImpressions ?? existingMedia.dailyImpressions),
                schedule: (payload.schedule as MockMedia["schedule"]) ?? existingMedia.schedule,
                imageUrl: String(payload.imageUrl ?? existingMedia.imageUrl),
                previewConfiguration: String(
                    payload.previewConfiguration ?? existingMedia.previewConfiguration
                ),
            };

            state.mediaStore = state.mediaStore.map((media) => (media.id === id ? updatedMedia : media));

            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify(toMediaResponse(updatedMedia, state.locationStore)),
            });
            return;
        }

        if (method === "DELETE" && apiPath.startsWith("/media/")) {
            const id = apiPath.split("/").at(-1) ?? "";
            state.capturedDeletedIds.push(id);
            state.mediaStore = state.mediaStore.filter((media) => media.id !== id);

            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: JSON.stringify({}),
            });
            return;
        }

        await route.continue();
    });
};

export const loginAndOpenMediaDashboard = async ({
    page,
    homePage,
    mediaDashboardPage,
    expectedMediaTitle,
}: {
    page: Page;
    homePage: HomePage;
    mediaDashboardPage: MediaDashboardPage;
    expectedMediaTitle?: string;
}) => {
    await homePage.goto();
    await homePage.login("megadoxs", "Password1!");
    await homePage.assertUserLoggedIn("megadoxs");
    await homePage.setLanguageToEnglish();

    await page.goto("/dashboard");
    await mediaDashboardPage.clickMediaLink();
    if (expectedMediaTitle) {
        await mediaDashboardPage.assertMediaVisible(expectedMediaTitle);
    }
};
