export interface HomepageStats {
    activeScreens: number;
    citiesCovered: number;
    venueTypes: number;
    monthlyBroadcasts: number;
}

export async function getHomepageStats(): Promise<HomepageStats | null> {
    const baseUrl = process.env.DOCKER === "true"
        ? process.env.WEBSERVICE_API_URL
        : process.env.NEXT_PUBLIC_API_URL;
    try {
        const res = await fetch(`${baseUrl}/public/stats`, { next: { revalidate: 3600 } });
        if (res.ok) return res.json();
    } catch {
        // fall through to null — HomePage uses fallback values
    }
    return null;
}
