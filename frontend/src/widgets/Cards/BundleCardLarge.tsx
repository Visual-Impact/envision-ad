"use client";

import { Badge, Box, Button, Group, Paper, SimpleGrid, Stack, Text, ThemeIcon, Title, Tooltip } from "@mantine/core";
import { IconBuildingStore, IconCheck, IconDeviceTv, IconMapPin, type TablerIcon } from "@tabler/icons-react";
import { useLocale, useTranslations } from "next-intl";
import { useUser } from "@auth0/nextjs-auth0/client";
import { Bundle } from "@/entities/bundle";
import { formatCurrency } from "@/shared/lib/formatCurrency";
import { Link } from "@/shared/lib/i18n/navigation";

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
    /** See BundleCard.tsx — swaps the CTA for a link to the subscriptions page (D46). */
    alreadySubscribed?: boolean;
}

const NETWORK_VENUE_TYPE_COUNT = 5;

/**
 * The flagship full-network bundle, rendered as a wide two-zone feature card:
 * value on the left (eyebrow, title, stat row, benefits) and the decision on the
 * right (a full-height action rail with the price, the per-screen value hook, the
 * CTA, and a risk-reducer). Uses only data we have; the prototype's savings line
 * and "most popular"/region tags are omitted (no discount until P2, one badge per
 * bundle in the model). See P1-PROGRESS decision log.
 */
export function BundleCardLarge({ bundle, stats, onSubscribe, alreadySubscribed }: BundleCardLargeProps) {
    const t = useTranslations("bundles");
    const locale = useLocale();
    const { user } = useUser();

    const name = locale === "fr" ? bundle.nameFr : bundle.nameEn;
    const description = locale === "fr" ? bundle.descriptionFr : bundle.descriptionEn;
    const idealFor = locale === "fr" ? bundle.idealForFr : bundle.idealForEn;
    const hasScreens = bundle.screenCount > 0;
    // See BundleCard.tsx — onSubscribe is left undefined for a signed-in user with
    // no organization, which used to render the button enabled but inert.
    const noOrganization = !!user && !onSubscribe;
    const disabled = !hasScreens || noOrganization;

    // Where the screens actually are, not generic feature bullets — mirrors the
    // prototype's venue-type list, ending with the posting-frequency line.
    const venueTypeRows = [
        ...Array.from({ length: NETWORK_VENUE_TYPE_COUNT }, (_, i) => t(`card.networkVenueTypes.${i}`)),
        t("card.networkFrequency"),
    ];

    const statBoxes: { value: number; label: string; Icon: TablerIcon }[] = [
        { value: bundle.screenCount, label: t("card.stats.screens"), Icon: IconDeviceTv },
        ...(stats ? [{ value: stats.citiesCovered, label: t("card.stats.cities"), Icon: IconMapPin }] : []),
        ...(stats ? [{ value: stats.venueTypes, label: t("card.stats.venueTypes"), Icon: IconBuildingStore }] : []),
    ];

    const isDiscounted = bundle.discountPercent > 0;

    // Value hook: exact per-screen price when every screen shares one, else the
    // average (flagged with ≈ so it stays honest). Both are real, no fabrication.
    // Under a discount it becomes a before/after comparison.
    const perScreen = bundle.perScreenPrice ?? (hasScreens ? bundle.basePrice / bundle.screenCount : null);
    const perScreenExact = bundle.perScreenPrice != null;
    const discountedPerScreen =
        bundle.discountedPerScreenPrice ??
        (isDiscounted && hasScreens ? bundle.finalPrice / bundle.screenCount : null);
    const approx = perScreenExact ? "" : "≈ ";
    const perScreenHook =
        isDiscounted && perScreen != null && discountedPerScreen != null
            ? t("card.perScreenHookDiscounted", {
                  was: approx + formatCurrency(perScreen, { locale }),
                  now: approx + formatCurrency(discountedPerScreen, { locale }),
              })
            : perScreen != null
              ? t("card.perScreenHook", { price: approx + formatCurrency(perScreen, { locale }) })
              : null;

    const tooltipLabel = alreadySubscribed
        ? null
        : !hasScreens
          ? t("card.noScreens")
          : noOrganization
            ? t("card.noOrganization")
            : null;

    const subscribeButton = alreadySubscribed ? (
        <Button fullWidth variant="light" size="md" component={Link} href="/dashboard/advertiser/subscriptions">
            {t("card.manageSubscription")}
        </Button>
    ) : (
        <Button
            fullWidth
            variant="gradient"
            size="md"
            disabled={disabled}
            component={disabled || user ? "button" : "a"}
            href={disabled || user ? undefined : `/auth/login?ui_locales=${locale}`}
            onClick={!disabled && user && onSubscribe ? () => onSubscribe(bundle) : undefined}
        >
            {t("card.subscribe")}
        </Button>
    );

    return (
        <Paper radius="lg" p={{ base: "lg", md: "xl" }} withBorder bg="var(--mantine-color-indigo-0)">
            {/* align=stretch makes both zones equal height, so the action rail fills
                the full card height instead of leaving dead space beneath the price. */}
            <Group align="stretch" wrap="wrap" gap="xl">
                {/* Value zone */}
                <Stack gap="md" style={{ flex: "1 1 460px", minWidth: 0 }}>
                    <Stack gap={4}>
                        <Text fw={700} size="xs" tt="uppercase" style={{ letterSpacing: "0.08em", color: bundle.badgeColor }}>
                            {t("card.eyebrow")}
                        </Text>
                        <Title order={3} fw={800}>
                            {name}
                        </Title>
                        {description && (
                            <Text c="gray.7" maw={560}>
                                {description}
                            </Text>
                        )}
                    </Stack>

                    {/* Stacks to one full-width row per stat below the `xs` breakpoint —
                        three side-by-side columns don't leave enough room for labels like
                        "Venue Types" at phone widths, so the neighbouring card's background
                        was clipping the overflow text instead of wrapping it. */}
                    <SimpleGrid cols={{ base: 1, xs: statBoxes.length }} spacing="sm">
                        {statBoxes.map(({ value, label, Icon }) => (
                            <Paper key={label} radius="md" p={{ base: "sm", xs: "md" }} withBorder bg="var(--mantine-color-body)">
                                <Group gap={8} wrap="nowrap" align="center">
                                    <ThemeIcon variant="light" color="indigo" size={34} radius="md">
                                        <Icon size={18} stroke={1.6} />
                                    </ThemeIcon>
                                    <Box>
                                        <Text fw={800} size="1.5rem" lh={1.1}>
                                            {value}
                                        </Text>
                                        <Text size="xs" c="dimmed" tt="uppercase" fw={600}>
                                            {label}
                                        </Text>
                                    </Box>
                                </Group>
                            </Paper>
                        ))}
                    </SimpleGrid>

                    {/* Built from Group rows rather than Mantine's List wrapping a grid —
                        that nesting let the checkmark drift whenever a neighbouring cell's
                        text wrapped to a different number of lines. Pinning the icon to
                        the top of each self-contained row keeps every row aligned the
                        same way regardless of text length. */}
                    <SimpleGrid cols={{ base: 1, sm: 2 }} spacing="xs" verticalSpacing="sm">
                        {venueTypeRows.map((row, i) => (
                            <Group key={i} gap={8} wrap="nowrap" align="flex-start">
                                <ThemeIcon color="teal" size={18} radius="xl" mt={2} style={{ flexShrink: 0 }}>
                                    <IconCheck size={12} stroke={3} />
                                </ThemeIcon>
                                <Text size="sm">{row}</Text>
                            </Group>
                        ))}
                    </SimpleGrid>

                    {idealFor && (
                        <Text size="sm" c="dimmed">
                            <Text component="span" fw={600}>
                                {t("card.idealFor")}
                            </Text>{" "}
                            {idealFor}
                        </Text>
                    )}
                </Stack>

                {/* Action rail — white panel, full height, centered on the price + CTA. */}
                <Paper
                    radius="md"
                    p="lg"
                    withBorder
                    bg="var(--mantine-color-body)"
                    style={{ flex: "1 1 240px", display: "flex" }}
                >
                    <Stack gap="xs" justify="center" align="center" ta="center" style={{ flex: 1 }}>
                        {alreadySubscribed && (
                            <Badge color="teal" variant="light" size="lg">
                                {t("card.alreadySubscribed")}
                            </Badge>
                        )}

                        {isDiscounted && (
                            <Badge color="red" variant="filled" size="lg">
                                {t("card.discountBadge", { percent: bundle.discountPercent })}
                            </Badge>
                        )}

                        {/* nowrap + clamp keep the amount and "/ month" on one line at any
                            card width instead of orphaning "month" onto its own line. */}
                        <Box style={{ whiteSpace: "nowrap" }}>
                            <Text
                                component="span"
                                fw={800}
                                variant="gradient"
                                gradient={{ from: "#0795ed", to: "#a855f7", deg: 95 }}
                                style={{ fontSize: "clamp(2rem, 9vw, 2.75rem)", lineHeight: 1 }}
                            >
                                {formatCurrency(bundle.finalPrice ?? 0, { locale })}
                            </Text>
                            <Text component="span" size="sm" c="dimmed" fw={500}>
                                {" "}
                                {t("card.perMonth")}
                            </Text>
                        </Box>

                        {isDiscounted && (
                            <Text size="sm" c="dimmed" td="line-through">
                                {formatCurrency(bundle.basePrice ?? 0, { locale })}
                            </Text>
                        )}

                        {perScreenHook && (
                            <Text size="sm" c="gray.7" fw={600}>
                                {perScreenHook}
                            </Text>
                        )}

                        <Box w="100%" mt="xs">
                            {tooltipLabel ? (
                                <Tooltip label={tooltipLabel} withArrow>
                                    {/* Tooltip needs a non-disabled wrapper to receive hover events. */}
                                    <Box>{subscribeButton}</Box>
                                </Tooltip>
                            ) : (
                                subscribeButton
                            )}
                        </Box>

                        <Text size="xs" c="dimmed">
                            {t("card.reassurance")}
                        </Text>
                    </Stack>
                </Paper>
            </Group>
        </Paper>
    );
}

export default BundleCardLarge;
