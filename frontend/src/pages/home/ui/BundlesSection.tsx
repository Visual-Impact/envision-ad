"use client";

import { Box, Container, Grid, GridCol, Loader, Stack, Tabs, Text, Title } from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { useTranslations } from "next-intl";
import { useEffect, useMemo, useState } from "react";
import { Bundle, BundleRuleType } from "@/entities/bundle";
import { getAllBundles } from "@/features/bundle-management/api";
import { BundleCard } from "@/widgets/Cards/BundleCard";
import { BundleCardLarge } from "@/widgets/Cards/BundleCardLarge";
import classes from "./BundlesSection.module.css";

type TabValue = BundleRuleType;

// Tab order matches the redesign prototype: whole network first, then narrowing scopes.
const TABS: TabValue[] = ["FULL_NETWORK", "REGION", "CITY", "VENUE"];

interface BundlesSectionProps {
    /** Network-wide counts fed to the full-network feature card's stat row. */
    stats?: { citiesCovered: number; venueTypes: number } | null;
}

/**
 * Bundle discovery as a home-page section (below "How it works"), not a standalone
 * route. Reuses the public GET /bundles and the BundleCard widget; the id lets the
 * section be linked to if a nav anchor is ever reintroduced.
 */
export function BundlesSection({ stats }: BundlesSectionProps) {
    const t = useTranslations("bundles");

    const [bundles, setBundles] = useState<Bundle[]>([]);
    const [status, setStatus] = useState<"loading" | "error" | "ready">("loading");
    const [activeTab, setActiveTab] = useState<TabValue>("FULL_NETWORK");

    // Load once and filter tabs client-side — the active bundle set is small, so a
    // refetch per tab would be wasteful. Inlined async IIFE with a cancelled guard
    // is the react-hooks/set-state-in-effect-safe pattern used across the repo.
    useEffect(() => {
        let cancelled = false;

        (async () => {
            setStatus("loading");
            try {
                const data = await getAllBundles(undefined, true);
                if (!cancelled) {
                    setBundles(data);
                    setStatus("ready");
                }
            } catch {
                if (!cancelled) {
                    setStatus("error");
                    notifications.show({ title: t("loadFailed"), message: "", color: "red" });
                }
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [t]);

    const visibleBundles = useMemo(
        () => bundles.filter((b) => b.ruleType === activeTab),
        [bundles, activeTab],
    );

    return (
        <Box component="section" id="bundles" className={classes.section}>
            <Container size="xl" px="md">
                <Stack gap="lg">
                    <Stack gap="xs" align="center" ta="center">
                        <Title order={2} className={`${classes.title} ${classes.heading} ${classes.titleGradient}`}>
                            {t("title")}
                        </Title>
                        <Text size="lg" c="gray.6" className={classes.subtitle}>
                            {t("subtitle")}
                        </Text>
                    </Stack>

                    <Tabs value={activeTab} onChange={(v) => setActiveTab((v as TabValue) ?? "FULL_NETWORK")}>
                        <Tabs.List justify="center">
                            {TABS.map((tab) => (
                                <Tabs.Tab key={tab} value={tab}>
                                    {t(`tabs.${tab}`)}
                                </Tabs.Tab>
                            ))}
                        </Tabs.List>
                    </Tabs>

                    {status === "loading" ? (
                        <Stack h="20em" justify="center" align="center">
                            <Loader />
                        </Stack>
                    ) : status === "error" ? (
                        <Stack h="20em" justify="center" align="center">
                            <Text>{t("loadFailed")}</Text>
                        </Stack>
                    ) : visibleBundles.length === 0 ? (
                        <Stack h="20em" justify="center" align="center">
                            <Text size="xl">{t("empty.title")}</Text>
                            <Text c="dimmed">{t("empty.subtitle")}</Text>
                        </Stack>
                    ) : (
                        <Grid w="100%">
                            {visibleBundles.map((bundle) =>
                                bundle.ruleType === "FULL_NETWORK" ? (
                                    // The flagship full-network bundle spans the full row as a feature card.
                                    <GridCol key={bundle.bundleId} span={12}>
                                        <BundleCardLarge bundle={bundle} stats={stats} />
                                    </GridCol>
                                ) : (
                                    <GridCol key={bundle.bundleId} span={{ base: 12, sm: 6, md: 4, lg: 3 }}>
                                        <BundleCard bundle={bundle} />
                                    </GridCol>
                                ),
                            )}
                        </Grid>
                    )}
                </Stack>
            </Container>
        </Box>
    );
}

export default BundlesSection;
