"use client";

import { Badge, Box, Button, Divider, Group, List, Paper, Stack, Text, ThemeIcon, Tooltip } from "@mantine/core";
import { IconCheck } from "@tabler/icons-react";
import { useLocale, useTranslations } from "next-intl";
import { useUser } from "@auth0/nextjs-auth0/client";
import { Bundle } from "@/entities/bundle";
import { formatCurrency } from "@/shared/lib/formatCurrency";

export interface BundleCardProps {
    bundle: Bundle;
    /**
     * Invoked when an authenticated advertiser clicks Subscribe on a bundle with
     * eligible screens. Left undefined in M3 (discovery only); M4 wires it to the
     * BundleSubscribeModal.
     */
    onSubscribe?: (bundle: Bundle) => void;
}

// How many static feature bullets each rule type shows (bundles.features.<TYPE>.<0..n>).
const FEATURE_COUNT = 3;

export function BundleCard({ bundle, onSubscribe }: BundleCardProps) {
    const t = useTranslations("bundles");
    const locale = useLocale();
    const { user } = useUser();

    const name = locale === "fr" ? bundle.nameFr : bundle.nameEn;
    const description = locale === "fr" ? bundle.descriptionFr : bundle.descriptionEn;
    const idealFor = locale === "fr" ? bundle.idealForFr : bundle.idealForEn;

    const hasScreens = bundle.screenCount > 0;
    const subline =
        bundle.perScreenPrice != null
            ? t("card.subline", {
                  price: formatCurrency(bundle.perScreenPrice, { locale }),
                  count: bundle.screenCount,
              })
            : t("card.sublineNoPrice", { count: bundle.screenCount });

    const features = Array.from({ length: FEATURE_COUNT }, (_, i) =>
        t(`features.${bundle.ruleType}.${i}`),
    );

    const subscribeButton = (
        <Button
            fullWidth
            variant="gradient"
            disabled={!hasScreens}
            component={!hasScreens || user ? "button" : "a"}
            href={!hasScreens || user ? undefined : `/auth/login?ui_locales=${locale}`}
            onClick={hasScreens && user && onSubscribe ? () => onSubscribe(bundle) : undefined}
        >
            {t("card.subscribe")}
        </Button>
    );

    return (
        <Paper shadow="sm" radius="lg" p="lg" withBorder style={{ height: "100%" }}>
            <Stack gap="sm" style={{ height: "100%" }}>
                <Group justify="space-between" align="flex-start">
                    <Badge color={bundle.badgeColor} variant="filled" size="lg">
                        {name}
                    </Badge>
                </Group>

                <Box>
                    <Text fw={700} size="2rem" lh={1.1}>
                        {formatCurrency(bundle.basePrice ?? 0, { locale })}
                        <Text component="span" size="sm" c="dimmed" fw={500}>
                            {" "}
                            {t("card.perMonth")}
                        </Text>
                    </Text>
                    <Text size="sm" c="dimmed">
                        {subline}
                    </Text>
                </Box>

                {description && <Text size="sm">{description}</Text>}

                <Divider />

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
                    {features.map((feature, i) => (
                        <List.Item key={i}>{feature}</List.Item>
                    ))}
                </List>

                {idealFor && (
                    <Text size="sm" c="dimmed">
                        <Text component="span" fw={600}>
                            {t("card.idealFor")}
                        </Text>{" "}
                        {idealFor}
                    </Text>
                )}

                {/* Push the CTA to the bottom so cards of varying content align. */}
                <Box style={{ marginTop: "auto" }} pt="sm">
                    {hasScreens ? (
                        subscribeButton
                    ) : (
                        <Tooltip label={t("card.noScreens")} withArrow>
                            {/* Tooltip needs a non-disabled wrapper to receive hover events. */}
                            <Box>{subscribeButton}</Box>
                        </Tooltip>
                    )}
                </Box>
            </Stack>
        </Paper>
    );
}

export default BundleCard;
