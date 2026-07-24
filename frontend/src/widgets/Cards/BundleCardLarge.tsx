"use client";

import { Box, Button, Group, List, Paper, SimpleGrid, Stack, Text, ThemeIcon, Title } from "@mantine/core";
import { IconCheck } from "@tabler/icons-react";
import { useLocale, useTranslations } from "next-intl";
import { useUser } from "@auth0/nextjs-auth0/client";
import { Bundle } from "@/entities/bundle";
import { formatCurrency } from "@/shared/lib/formatCurrency";

export interface BundleCardLargeStats {
    citiesCovered: number;
    venueTypes: number;
}

export interface BundleCardLargeProps {
    bundle: Bundle;
    /** Network-wide counts for the stat row; accurate because this card is the full network. */
    stats?: BundleCardLargeStats | null;
    /** Wired to the subscribe modal in M4; unused in M3 (discovery only). */
    onSubscribe?: (bundle: Bundle) => void;
}

const FEATURE_COUNT = 3;

/**
 * The flagship full-network bundle, rendered as a wide feature card that stands
 * apart from the normal BundleCard grid. Uses only data we have: the bundle's own
 * badge/name/price/screen count/features, plus network-wide city & venue-type
 * counts (valid here since the full network spans everything). The prototype's
 * savings line and extra "most popular"/region tags are intentionally omitted —
 * there's no discount until P2 and no such fields in the model.
 */
export function BundleCardLarge({ bundle, stats, onSubscribe }: BundleCardLargeProps) {
    const t = useTranslations("bundles");
    const locale = useLocale();
    const { user } = useUser();

    const name = locale === "fr" ? bundle.nameFr : bundle.nameEn;
    const description = locale === "fr" ? bundle.descriptionFr : bundle.descriptionEn;
    const idealFor = locale === "fr" ? bundle.idealForFr : bundle.idealForEn;
    const hasScreens = bundle.screenCount > 0;

    const features = Array.from({ length: FEATURE_COUNT }, (_, i) => t(`features.${bundle.ruleType}.${i}`));

    const statBoxes = [
        { value: bundle.screenCount, label: t("card.stats.screens") },
        ...(stats ? [{ value: stats.citiesCovered, label: t("card.stats.cities") }] : []),
        ...(stats ? [{ value: stats.venueTypes, label: t("card.stats.venueTypes") }] : []),
    ];

    const subscribeButton = (
        <Button
            variant="gradient"
            size="md"
            disabled={!hasScreens}
            component={!hasScreens || user ? "button" : "a"}
            href={!hasScreens || user ? undefined : `/auth/login?ui_locales=${locale}`}
            onClick={hasScreens && user && onSubscribe ? () => onSubscribe(bundle) : undefined}
        >
            {t("card.subscribe")}
        </Button>
    );

    return (
        <Paper radius="lg" p={{ base: "lg", md: "xl" }} withBorder bg="var(--mantine-color-indigo-0)">
            <Group justify="space-between" align="flex-start" wrap="wrap" gap="xl">
                {/* Main column */}
                <Stack gap="md" style={{ flex: "1 1 420px", minWidth: 0 }}>
                    {/* A colored accent bar in the bundle's badge color stands in for the
                        badge pill (the name is already the title, so a pill would duplicate it). */}
                    <Box w={48} h={4} style={{ background: bundle.badgeColor, borderRadius: 4 }} />

                    <Title order={3} fw={800}>
                        {name}
                    </Title>

                    {description && (
                        <Text c="gray.7" maw={560}>
                            {description}
                        </Text>
                    )}

                    <SimpleGrid cols={{ base: statBoxes.length, sm: statBoxes.length }} spacing="md">
                        {statBoxes.map((s) => (
                            <Paper key={s.label} radius="md" p="md" withBorder ta="center" bg="var(--mantine-color-body)">
                                <Text fw={800} size="1.75rem" lh={1.1}>
                                    {s.value}
                                </Text>
                                <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
                                    {s.label}
                                </Text>
                            </Paper>
                        ))}
                    </SimpleGrid>

                    <List
                        spacing="xs"
                        size="sm"
                        center
                        icon={
                            <ThemeIcon color="teal" size={18} radius="xl">
                                <IconCheck size={12} stroke={3} />
                            </ThemeIcon>
                        }
                    >
                        <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="xs" verticalSpacing="xs">
                            {features.map((feature, i) => (
                                <List.Item key={i}>{feature}</List.Item>
                            ))}
                        </SimpleGrid>
                    </List>

                    {idealFor && (
                        <Text size="sm" c="dimmed">
                            <Text component="span" fw={600}>
                                {t("card.idealFor")}
                            </Text>{" "}
                            {idealFor}
                        </Text>
                    )}
                </Stack>

                {/* Price + CTA column */}
                <Stack gap="sm" align="flex-end" style={{ flex: "0 0 auto" }}>
                    <Box ta="right">
                        <Text
                            component="span"
                            fw={800}
                            variant="gradient"
                            gradient={{ from: "#0795ed", to: "#a855f7", deg: 95 }}
                            style={{ fontSize: "3rem", lineHeight: 1 }}
                        >
                            {formatCurrency(bundle.basePrice ?? 0, { locale })}
                        </Text>
                        <Text component="span" size="sm" c="dimmed" fw={500}>
                            {" "}
                            {t("card.perMonth")}
                        </Text>
                    </Box>
                    {subscribeButton}
                </Stack>
            </Group>
        </Paper>
    );
}

export default BundleCardLarge;
