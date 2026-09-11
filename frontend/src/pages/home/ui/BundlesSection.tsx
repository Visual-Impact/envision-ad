"use client";

import { Box, Container, Grid, GridCol, Loader, Stack, Tabs, Text, Title } from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { useTranslations } from "next-intl";
import { useEffect, useMemo, useState } from "react";
import { Bundle, BundleRuleType } from "@/entities/bundle";
import { getAllBundles } from "@/features/bundle-management";
import { getBundleSubscriptions } from "@/features/bundle-subscription";
import { BundleSubscribeModal } from "@/widgets/BundleSubscribeModal";
import { useOrganization } from "@/entities/organization";
import { BundleCard } from "@/widgets/Cards/BundleCard";
import { BundleCardLarge } from "@/widgets/Cards/BundleCardLarge";
import classes from "./BundlesSection.module.css";

// Statuses that occupy a business's one-live-subscription-per-bundle slot (brief
// req. 13) — CANCELED does not, since resubscribing to a bundle after cancelling
// is explicitly allowed.
const LIVE_STATUSES = new Set(["INCOMPLETE", "ACTIVE", "PAST_DUE"]);

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

    const { organization } = useOrganization();

    const [bundles, setBundles] = useState<Bundle[]>([]);
    const [status, setStatus] = useState<"loading" | "error" | "ready">("loading");
    const [activeTab, setActiveTab] = useState<TabValue>("FULL_NETWORK");
    const [subscribingTo, setSubscribingTo] = useState<Bundle | null>(null);
    const [subscribedBundleIds, setSubscribedBundleIds] = useState<Set<string>>(new Set());

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

    // Card-level "already subscribed" state (D46) — a cheap authenticated fetch so a
    // business doesn't have to hit the subscribe 409 to learn it already holds a bundle.
    // Silently skipped/cleared without an organization; failure here shouldn't block
    // browsing, so it's not surfaced as a page-level error like the bundles fetch above.
    useEffect(() => {
        let cancelled = false;

        (async () => {
            if (!organization) {
                if (!cancelled) {
                    setSubscribedBundleIds(new Set());
                }
                return;
            }

            try {
                const subscriptions = await getBundleSubscriptions(organization.businessId);
                if (!cancelled) {
                    setSubscribedBundleIds(
                        new Set(
                            subscriptions
                                .filter((s) => LIVE_STATUSES.has(s.status))
                                .map((s) => s.bundleId),
                        ),
                    );
                }
            } catch {
                if (!cancelled) {
                    setSubscribedBundleIds(new Set());
                }
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [organization]);

    const visibleBundles = useMemo(
        () => bundles.filter((b) => b.ruleType === activeTab),
        [bundles, activeTab],
    );

    // An authenticated user with no organization has no businessId to subscribe under, so
    // the CTA stays inert rather than opening a modal that could never submit.
    const onSubscribe = organization ? setSubscribingTo : undefined;

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
                                        <BundleCardLarge
                                            bundle={bundle}
                                            stats={stats}
                                            onSubscribe={onSubscribe}
                                            alreadySubscribed={subscribedBundleIds.has(bundle.bundleId)}
                                        />
                                    </GridCol>
                                ) : (
                                    <GridCol key={bundle.bundleId} span={{ base: 12, sm: 6, md: 4, lg: 3 }}>
                                        <BundleCard
                                            bundle={bundle}
                                            onSubscribe={onSubscribe}
                                            alreadySubscribed={subscribedBundleIds.has(bundle.bundleId)}
                                        />
                                    </GridCol>
                                ),
                            )}
                        </Grid>
                    )}
                </Stack>
            </Container>

            <BundleSubscribeModal
                opened={subscribingTo !== null}
                onClose={() => setSubscribingTo(null)}
                bundle={subscribingTo}
                businessId={organization?.businessId ?? null}
            />
        </Box>
    );
}

export default BundlesSection;
