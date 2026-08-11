import "@mantine/core/styles.css";
import "@mantine/charts/styles.css";
import "@mantine/dates/styles.css";
import "@mantine/notifications/styles.css";
import {ColorSchemeScript, mantineHtmlProps, MantineProvider} from "@mantine/core";
import {josefinSans, lato, theme} from "@/app/theme";
import type {ReactNode} from "react";
import Footer from "@/widgets/Footer/Footer";
import {getMessages, getTranslations} from "next-intl/server";
import {Notifications} from "@mantine/notifications";
import {Header} from "@/widgets/Header/Header";
import {ModalsProvider} from "@mantine/modals";
import {auth0} from "@/shared/api/auth0/auth0";
import {Auth0Provider} from "@auth0/nextjs-auth0";
import {IntlErrorProvider, OrganizationProvider, PermissionsProvider} from "@/app/providers";
import {Metadata, Viewport} from "next";

export const viewport: Viewport = {
    width: "device-width",
    initialScale: 1,
    // maximumScale: 1, // Removed this to allow users to zoom if needed for accessibility
    // userScalable: false,
}

export async function generateMetadata({
                                           params,
                                       }: {
    params: Promise<{ locale: string }>;
}): Promise<Metadata> {
    const {locale} = await params;
    const t = await getTranslations({locale, namespace: "metadata"});

    return {
        title: t("title"),
        description: t("description"),
    };
}

export default async function RootLayout({
                                             children,
                                             params,
                                         }: {
    children: ReactNode;
    params: Promise<{ locale: string }>;
}) {
    const {locale} = await params;
    const messages = await getMessages({locale});

    const session = await auth0.getSession();
    const user = session?.user;

    const baseUrl = process.env.DOCKER === "true"
        ? process.env.WEBSERVICE_API_URL
        : process.env.NEXT_PUBLIC_API_URL;

    let bookMeetingUrl: string | null = null;
    try {
        const res = await fetch(`${baseUrl}/settings/book-meeting-url`, { cache: "no-store" });
        if (res.ok) {
            const data = await res.json();
            bookMeetingUrl = data.value ?? null;
        }
    } catch {
        // leave null — Header will show a toast when admin clicks Book Meeting
    }

    return (
            <html
                lang={locale}
                {...mantineHtmlProps}
                className={`${lato.variable} ${josefinSans.variable}`}
            >
            <head>
                {/* The title is handled by Next.js*/}
                <ColorSchemeScript defaultColorScheme="light"/>
            </head>
            <body style={{ minHeight: "100vh", display: "flex", flexDirection: "column" }}>
            <IntlErrorProvider messages={messages} locale={locale}>
                <Auth0Provider user={user}>
                    <PermissionsProvider>
                        <OrganizationProvider>
                            <MantineProvider theme={theme}>
                                <ModalsProvider>
                                    <Notifications/>
                                    <Header bookMeetingUrl={bookMeetingUrl} />
                                    {/* 20px (navbar top offset) + 56px (navbar height) + 16px breathing room */}
                                    <div style={{ paddingTop: 92 }}>
                                        {children}
                                    </div>
                                    <Footer bookMeetingUrl={bookMeetingUrl} />
                                </ModalsProvider>
                            </MantineProvider>
                        </OrganizationProvider>
                    </PermissionsProvider>
                </Auth0Provider>
            </IntlErrorProvider>
            </body>
        </html>
    );
}