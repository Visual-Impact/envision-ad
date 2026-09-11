import { HomePage, getBookMeetingUrl, getHomepageGalleryImages, getHomepageStats } from "@/pages/home";

export default async function Page() {
    const [bookMeetingUrl, stats, galleryImages] = await Promise.all([
        getBookMeetingUrl(),
        getHomepageStats(),
        getHomepageGalleryImages(),
    ]);
    return <HomePage bookMeetingUrl={bookMeetingUrl} stats={stats} galleryImages={galleryImages} />;
}
