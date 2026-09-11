export async function getBookMeetingUrl(): Promise<string | null> {
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
