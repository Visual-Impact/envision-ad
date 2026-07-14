import { HomePage } from "@/pages/home";
import { parseGalleryImages } from "@/features/app-settings/api";

interface HomepageStats {
    activeScreens: number;
    citiesCovered: number;
    venueTypes: number;
    monthlyBroadcasts: number;
}

async function getBookMeetingUrl(): Promise<string | null> {
    const baseUrl = process.env.DOCKER === "true"
        ? process.env.WEBSERVICE_API_URL
        : process.env.NEXT_PUBLIC_API_URL;
    try {
        const res = await fetch(`${baseUrl}/settings/book-meeting-url`, { cache: "no-store" });
        if (res.ok) {
            const data = await res.json();
            return data.value ?? null;
        }
    } catch {
        // leave null — HomePage will show a toast when the user clicks Book Meeting
    }
    return null;
}

async function getHomepageStats(): Promise<HomepageStats | null> {
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

async function getGalleryImages(): Promise<string[]> {
    const baseUrl = process.env.DOCKER === "true"
        ? process.env.WEBSERVICE_API_URL
        : process.env.NEXT_PUBLIC_API_URL;
    try {
        const res = await fetch(`${baseUrl}/settings/homepage-gallery`, { cache: "no-store" });
        if (res.ok) {
            const data = await res.json();
            // parseGalleryImages tolerates malformed/missing values (returns []).
            return parseGalleryImages(data.value).map((img) => img.url);
        }
    } catch {
        // fall through — DisplayGallery renders the bundled default images
    }
    return [];
}

export default async function Page() {
    const [bookMeetingUrl, stats, galleryImages] = await Promise.all([
        getBookMeetingUrl(),
        getHomepageStats(),
        getGalleryImages(),
    ]);
    return <HomePage bookMeetingUrl={bookMeetingUrl} stats={stats} galleryImages={galleryImages} />;
}
