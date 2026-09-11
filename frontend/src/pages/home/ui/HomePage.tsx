"use client";

import React from "react";
import { Container, Stack, Title, Text, Box, Group, Divider } from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { Link } from "@/shared/lib/i18n";
import classes from "./HomePage.module.css";
import { useTranslations } from "next-intl";
import { HeroGridBackground } from "./HeroGridBackground";
import { HeroScreens } from "./HeroScreens";
import { AdvertiserTimeline } from "./AdvertiserTimeline";
import { BundlesSection } from "./BundlesSection";
import { DisplayGallery } from "@/widgets/DisplayGallery/DisplayGallery";

interface HomepageStats {
    activeScreens: number;
    citiesCovered: number;
    venueTypes: number;
    monthlyBroadcasts: number;
}

interface HomePageProps {
    bookMeetingUrl: string | null;
    stats: HomepageStats | null;
    galleryImages: string[];
}

function formatStat(value: number): string {
    if (value >= 1_000_000) return `${Math.floor(value / 1_000_000)}M+`;
    if (value >= 1_000) return `${Math.floor(value / 1_000)}K+`;
    return value.toString();
}

export default function HomePage({ bookMeetingUrl, stats, galleryImages }: HomePageProps) {
    const t = useTranslations("homepage");
    const tNav = useTranslations("nav");

    const handleBookMeeting = (e: React.MouseEvent) => {
        if (!bookMeetingUrl) {
            e.preventDefault();
            notifications.show({
                title: tNav("bookMeetingUrlNotSet"),
                message: "",
                color: "red",
            });
        }
    };

    // Keep the hero paragraph's screen count in sync with the stat card below.
    const activeScreens = stats?.activeScreens ?? 21;

    const statItems = [
        { value: formatStat(activeScreens),                            label: t("stats.screens") },
        { value: stats ? formatStat(stats.citiesCovered) : "6",        label: t("stats.cities") },
        { value: stats ? formatStat(stats.venueTypes) : "8",           label: t("stats.venueTypes") },
        { value: stats ? formatStat(stats.monthlyBroadcasts) : "400K+", label: t("stats.broadcasts") },
    ];

    return (
        <>
            {/* Hero Section */}
            <Box className={classes.hero}>
                {/* Animated grid background (decorative, full-bleed, behind content) */}
                <HeroGridBackground />

                {/* Content */}
                <Container
                    size={1480}
                    className={classes.heroContainer}
                    style={{ position: "relative", zIndex: 1 }}
                >
                    {/* Soft white wash keeps left-column text AA-legible over the
                        animated grid, even under the pointer spotlight. */}
                    <div className={classes.heroWash} aria-hidden="true" />
                    <div className={classes.heroGrid}>
                        <Stack gap="xl" className={classes.heroLeft}>
                        {/* Headline */}
                        <Stack gap={0}>
                            <Title order={1} className={classes.heroTitle}>
                                {t("heroTitlePart1")}
                            </Title>
                            <Title order={1} className={`${classes.heroTitle} ${classes.heroTitleGradient}`}>
                                {t("heroTitlePart2")}
                            </Title>
                        </Stack>

                        {/* Description */}
                        <Text size="lg" c="gray.7" className={classes.heroDescription}>
                            {t("heroDescription", { count: activeScreens })}
                        </Text>

                        {/* CTA Buttons */}
                        <Group gap="md" className={classes.ctaGroup}>
                            <Box
                                component="a"
                                href={bookMeetingUrl ?? "#"}
                                target={bookMeetingUrl ? "_blank" : undefined}
                                rel={bookMeetingUrl ? "noopener noreferrer" : undefined}
                                onClick={handleBookMeeting}
                                className={classes.ctaPrimary}
                            >
                                {t("ctaMeeting")}
                            </Box>
                            <Link href="/browse" className={classes.ctaSecondary}>
                                {t("ctaPackages")}
                            </Link>
                        </Group>

                        {/* Stats row */}
                        <Divider />
                        <Group wrap="nowrap" justify="space-between" className={classes.statsRow}>
                            {statItems.map((stat) => (
                                <Box key={stat.label} className={classes.statItem}>
                                    <Text className={classes.statValue}>{stat.value}</Text>
                                    <Text className={classes.statLabel}>{stat.label}</Text>
                                </Box>
                            ))}
                        </Group>
                        </Stack>

                        {/* Right panel — device screen illustrations */}
                        <div className={classes.heroRight}>
                            <HeroScreens />
                        </div>
                    </div>
                </Container>
            </Box>

            {/* Real-world display gallery */}
            <DisplayGallery images={galleryImages} />

            {/* Animated "how it works" advertiser timeline */}
            <AdvertiserTimeline bookMeetingUrl={bookMeetingUrl} onBookMeetingClick={handleBookMeeting} />

            {/* Bundle discovery — a section here rather than a standalone /bundles page */}
            <BundlesSection stats={stats} />
        </>
    );
}
