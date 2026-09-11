import type { ReactNode } from "react";
import { Header } from "@/widgets/app-navigation";
import { Footer } from "@/widgets/footer";

export function AppShell({ bookMeetingUrl, children }: { bookMeetingUrl: string | null; children: ReactNode }) {
    return (
        <>
            <Header bookMeetingUrl={bookMeetingUrl} />
            {/* 20px (navbar top offset) + 56px (navbar height) + 16px breathing room */}
            <div style={{ paddingTop: 92 }}>
                {children}
            </div>
            <Footer bookMeetingUrl={bookMeetingUrl} />
        </>
    );
}
