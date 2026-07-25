"use client";

import {
    Group,
    Stack,
    Button,
    Text,
    Box,
    Drawer,
    ScrollArea,
    Divider,
    Burger,
    Menu, NavLink,
} from "@mantine/core";
import { notifications } from "@mantine/notifications";
import React, { useEffect } from "react";
import Image from "next/image";
import { useLocale, useTranslations } from "next-intl";
import { useDisclosure } from "@mantine/hooks";
import { LanguagePicker } from "./LanguagePicker";
import { Link, usePathname } from "@/shared/lib/i18n/navigation";
import { useUser } from "@auth0/nextjs-auth0/client";
import { IconChevronDown, IconHome, IconLayoutDashboard, IconLogout, IconSearch, IconUser } from "@tabler/icons-react";
import { useOrganization, usePermissions } from "@/app/providers";
import SideBar from "@/widgets/SideBar/SideBar";
import styles from "./Header.module.css";

// 1px border + 8px padding + 38px logo image + 8px padding + 1px border = 56px
const CAPSULE_HEIGHT = 56;

const NAV_ICONS: Record<string, React.ReactNode> = {
    "/": <IconHome size={18} aria-hidden="true" />,
    "/dashboard": <IconLayoutDashboard size={18} aria-hidden="true" />,
    "/browse": <IconSearch size={18} aria-hidden="true" />,
};

const CAPSULE_STYLE = {
    border: "1px solid var(--mantine-color-gray-3)",
    borderRadius: 9999,
    background: "var(--mantine-color-body)",
    boxShadow: "0 1px 4px rgba(0,0,0,0.08)",
    height: CAPSULE_HEIGHT,
    boxSizing: "border-box" as const,
};

export function Header({ bookMeetingUrl }: { bookMeetingUrl: string | null }) {
    const locale = useLocale();
    const t = useTranslations("nav");
    const pathname = usePathname();
    const { user } = useUser();
    const { organization } = useOrganization();
    const { permissions } = usePermissions();

    const isDashboardRoute = pathname === "/dashboard" || pathname.startsWith("/dashboard/");
    const isAdmin =
        permissions.includes("patch:media_status") &&
        permissions.includes("readAll:verification") &&
        permissions.includes("update:verification");
    const showDashboardNav = isDashboardRoute && (!!organization || isAdmin);

    const links: Array<{
        link: "/" | "/dashboard" | "/browse";
        label: string;
        authRequired?: boolean;
    }> = [
            { link: "/", label: t("home"), authRequired: false },
            { link: "/dashboard", label: t("dashboard"), authRequired: true },
            { link: "/browse", label: t("browse"), authRequired: false },
        ];

    const filteredLinks = links.filter((link) => {
        return !(link.authRequired && !user);
    });

    const [drawerOpened, { toggle: toggleDrawer, close: closeDrawer }] =
        useDisclosure(false);

    useEffect(() => {
        closeDrawer();
    }, [pathname, closeDrawer]);

    const mobileItems = filteredLinks.map((link) => {
        const active = pathname === link.link || (link.link !== "/" && pathname.startsWith(link.link + '/'));
        return (
            <Link
                key={link.label}
                href={link.link}
                style={{
                    textDecoration: "none",
                    color: active ? "var(--mantine-color-blue-6)" : "var(--mantine-color-text)",
                    fontWeight: active ? 600 : 400,
                    padding: "10px 12px",
                    borderRadius: 12,
                    background: active ? "var(--mantine-color-blue-0)" : "transparent",
                    display: "flex",
                    alignItems: "center",
                    gap: 10,
                }}
            >
                {NAV_ICONS[link.link]}
                {link.label}
            </Link>
        );
    });

    const desktopItems = filteredLinks.map((link) => {
        const active = pathname === link.link || (link.link !== "/" && pathname.startsWith(link.link + '/'));
        return (
            <Link
                key={link.label}
                href={link.link}
                className={active ? styles.navLinkActive : styles.navLink}
            >
                {link.label}
            </Link>
        );
    });

    const userMenu = user && (
        <Menu shadow="md" width={200} withinPortal={false}>
            <Menu.Target>
                <Button
                    variant="transparent"
                    radius={9999}
                    rightSection={<IconChevronDown size={16} />}
                    styles={{ root: { fontWeight: 600, fontSize: 14, padding: "7px 12px 7px 20px", color: "var(--mantine-color-text)" } }}
                >
                    <Text component="span">{user.nickname || user.name || "User"}</Text>
                </Button>
            </Menu.Target>

            <Menu.Dropdown>
                <Menu.Item component="a" href="/profile">
                    {t("profile")}
                </Menu.Item>
                <Menu.Item component="a" href="/auth/logout" color="red">
                    {t("logout")}
                </Menu.Item>
            </Menu.Dropdown>
        </Menu>
    );

    const handleBookMeeting = (e: React.MouseEvent) => {
        if (!bookMeetingUrl) {
            e.preventDefault();
            notifications.show({
                title: t("bookMeetingUrlNotSet"),
                message: "",
                color: "red",
            });
        }
    };

    // Module 3 is always the "Book Meeting" CTA — primary acquisition action
    const bookMeetingCapsule = (
        <Box
            component="a"
            href={bookMeetingUrl ?? "#"}
            target={bookMeetingUrl ? "_blank" : undefined}
            rel={bookMeetingUrl ? "noopener noreferrer" : undefined}
            onClick={handleBookMeeting}
            className={styles.bookButton}
            style={{
                borderRadius: 9999,
                padding: "1.5px",
                display: "inline-flex",
                flexDirection: "column",
                height: CAPSULE_HEIGHT,
                boxSizing: "border-box",
                textDecoration: "none",
            }}
        >
            <Box
                style={{
                    background: "var(--mantine-color-body)",
                    borderRadius: 9999,
                    flex: 1,
                    padding: "0 24px",
                    fontWeight: 700,
                    fontSize: 14,
                    color: "var(--mantine-color-text)",
                    display: "flex",
                    alignItems: "center",
                    whiteSpace: "nowrap",
                }}
            >
                {t("bookMeeting")}
            </Box>
        </Box>
    );

    return (
        <>
            <Box
                component="header"
                style={{
                    position: "fixed",
                    top: 20,
                    left: 0,
                    right: "var(--removed-body-scroll-bar-size, 0px)",
                    zIndex: "var(--mantine-z-index-app)",
                    pointerEvents: "none",
                }}
            >
                <Group
                    justify="center"
                    align="center"
                    gap="md"
                    style={{ pointerEvents: "auto" }}
                >
                    {/* Module 1: Logo Capsule */}
                    <Link href="/" style={{ textDecoration: "none", display: "flex" }}>
                        <Box
                            style={{
                                ...CAPSULE_STYLE,
                                padding: "8px 20px",
                                display: "flex",
                                alignItems: "center",
                            }}
                        >
                            <Group gap="sm" align="center">
                                <Image
                                    src="/images/logo-envision-ad.png"
                                    alt={t("images.envisionAdLogo.alt")}
                                    width={38}
                                    height={38}
                                    style={{ objectFit: "contain" }}
                                    priority
                                />
                                <Text component="span" size="lg" fw={700} c="blue.6">
                                    {t("platformName")}
                                </Text>
                            </Group>
                        </Box>
                    </Link>

                    {/* Module 2: Nav links + Language picker + Sign In (or user menu) */}
                    <Group
                        visibleFrom="md"
                        gap={4}
                        align="center"
                        style={{
                            ...CAPSULE_STYLE,
                            padding: "6px 10px",
                        }}
                    >
                        {desktopItems}

                        <Box
                            style={{
                                width: 1,
                                height: 24,
                                background: "var(--mantine-color-gray-3)",
                                flexShrink: 0,
                                marginLeft: 6,
                                marginRight: 6,
                            }}
                        />

                        {user ? userMenu : (
                            <a
                                href={`/auth/login?ui_locales=${locale}`}
                                style={{
                                    textDecoration: "none",
                                    color: "var(--mantine-color-blue-6)",
                                    fontWeight: 600,
                                    padding: "7px 20px",
                                    fontSize: 14,
                                    whiteSpace: "nowrap",
                                }}
                            >
                                {t("signIn")}
                            </a>
                        )}

                        <LanguagePicker />
                    </Group>

                    {/* Module 3: Book Meeting — permanent CTA */}
                    <Box visibleFrom="md">
                        {bookMeetingCapsule}
                    </Box>

                    {/* Burger (mobile only) */}
                    <Box
                        hiddenFrom="md"
                        style={{
                            ...CAPSULE_STYLE,
                            padding: "6px 10px",
                            display: "flex",
                            alignItems: "center",
                        }}
                    >
                        <Burger
                            opened={drawerOpened}
                            onClick={toggleDrawer}
                            aria-label="Toggle navigation"
                        />
                    </Box>
                </Group>
            </Box>

            {/* Navigation drawer */}
            <Drawer
                opened={drawerOpened}
                onClose={closeDrawer}
                size="100%"
                padding="md"
                title={
                    <Group justify="space-between" align="center" style={{ width: "100%" }}>
                        <Text fw={700} c="blue.6">{t("platformName")}</Text>
                        <LanguagePicker />
                    </Group>
                }
                hiddenFrom="md"
                zIndex={1000000}
            >
                <ScrollArea h="calc(100vh - 80px)" mx="-md">
                    <Divider my="sm" />

                    <Text size="xs" fw={600} c="dimmed" px="md" mb={4} style={{ textTransform: "uppercase", letterSpacing: "0.06em" }}>
                        {t("navigate")}
                    </Text>
                    <Stack px="md" gap={2}>
                        {mobileItems}
                    </Stack>

                    {showDashboardNav && (
                        <>
                            <Divider my="sm" />

                            <Text size="xs" fw={600} c="dimmed" px="md" mb={4} style={{ textTransform: "uppercase", letterSpacing: "0.06em" }}>
                                {t("dashboard")}
                            </Text>
                            <Box px="md">
                                <SideBar />
                            </Box>
                        </>
                    )}

                    <Divider my="sm" />

                    <Text size="xs" fw={600} c="dimmed" px="md" mb={8} style={{ textTransform: "uppercase", letterSpacing: "0.06em" }}>
                        {t("account")}
                    </Text>
                    <Box px="md" pb="xl">
                        {user ? (
                            <Stack gap={2} mb="md">
                                <Group align="center" gap="sm" px={12} py={8}>
                                    <Box style={{
                                        width: 36, height: 36, borderRadius: "50%",
                                        background: "var(--mantine-color-blue-1)",
                                        display: "flex", alignItems: "center", justifyContent: "center",
                                        fontWeight: 600, fontSize: 13,
                                        color: "var(--mantine-color-blue-7)",
                                        flexShrink: 0,
                                    }}>
                                        {(user.nickname || user.name || "U").slice(0, 2).toUpperCase()}
                                    </Box>
                                    <Text size="sm" fw={500}>{user.nickname || user.name || "User"}</Text>
                                </Group>
                                <NavLink
                                    component="a"
                                    href="/profile"
                                    label={t("profile")}
                                    leftSection={<IconUser size={18} />}
                                    styles={{ root: { borderRadius: 12 } }}
                                />
                                <NavLink
                                    component="a"
                                    href="/auth/logout"
                                    label={t("logout")}
                                    leftSection={<IconLogout size={18} />}
                                    styles={{ root: { borderRadius: 12, color: "var(--mantine-color-red-6)" } }}
                                />
                            </Stack>
                        ) : (
                            <a href={`/auth/login?ui_locales=${locale}`} style={{ textDecoration: "none" }}>
                                <Button fullWidth variant="filled" color="blue.6" radius={9999} mb="md">
                                    {t("signIn")}
                                </Button>
                            </a>
                        )}

                        <Box
                            component="a"
                            href={bookMeetingUrl ?? "#"}
                            target={bookMeetingUrl ? "_blank" : undefined}
                            rel={bookMeetingUrl ? "noopener noreferrer" : undefined}
                            onClick={handleBookMeeting}
                            className={styles.bookButton}
                            style={{
                                borderRadius: 9999,
                                padding: "1.5px",
                                display: "flex",
                                textDecoration: "none",
                            }}
                        >
                            <Box style={{
                                background: "var(--mantine-color-body)",
                                borderRadius: 9999,
                                flex: 1,
                                padding: "10px 24px",
                                fontWeight: 700,
                                fontSize: 14,
                                color: "var(--mantine-color-text)",
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                whiteSpace: "nowrap",
                            }}>
                                {t("bookMeeting")}
                            </Box>
                        </Box>
                    </Box>
                </ScrollArea>
            </Drawer>
        </>
    );
}
