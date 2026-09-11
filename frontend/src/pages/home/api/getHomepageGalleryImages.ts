import { parseGalleryImages } from "@/features/app-settings";

export async function getHomepageGalleryImages(): Promise<string[]> {
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
