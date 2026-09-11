"use client";

import { Divider, Grid, Group, Select, Stack, Text, Title } from "@mantine/core";
import { DatePickerInput } from "@mantine/dates";
import { useTranslations } from "next-intl";
import { KpiCard } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/KpiCard";
import { PayoutHistorySection } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/PayoutHistorySection";
import type { OverviewPeriod } from "@/pages/dashboard/media-owner/model/metricsTypes";
import { useMediaOwnerMetricsData } from "@/pages/dashboard/media-owner/model/useMediaOwnerMetricsData";

/**
 * Media-owner metrics.
 *
 * P1 M6 (decision D41) removed four sections that were derived from reservations and have no
 * bundle-era source: the earnings trend chart, revenue by location, revenue by media location,
 * and active-campaign details. Each was keyed on a reservation's own `startDate`/`endDate` and
 * its single `mediaId`; a bundle subscription has neither — it covers many screens at once with
 * no per-screen date range, and the split that would let these be rebuilt lives in
 * `bundle_subscription_items`, which no media-owner-facing endpoint exposes.
 *
 * Rebuilding them on subscription data is tracked for M7 rather than treated as an accepted
 * loss. What remains — earnings KPIs and payout history — is sourced from the `bundle_payouts`
 * ledger, i.e. money that actually moved.
 */
export default function MediaOwnerMetricsDashboard() {
    const t = useTranslations("mediaOwnerMetrics");
    const {
        overviewPeriod,
        setOverviewPeriod,
        dateRange,
        setDateRange,
        kpis,
        payoutHistoryRows,
        payoutPage,
        payoutTotalPages,
        setPayoutPage,
    } = useMediaOwnerMetricsData();

    return (
        <Stack gap="md" p="md">
            <Title order={2}>{t("title")}</Title>

            <Grid gutter="md">
                {kpis.map((kpi) => (
                    <Grid.Col key={kpi.id} span={{ base: 12, sm: 6, lg: 3 }}>
                        <KpiCard item={kpi} />
                    </Grid.Col>
                ))}
            </Grid>

            <Divider mt="md" mb="md" />

            <Group justify="space-between" align="center">
                <Text fw={600} size="lg">
                    {t("sections.performanceMetrics")}
                </Text>

                <Group gap="sm">
                    {overviewPeriod === "custom" && (
                        <DatePickerInput
                            type="range"
                            value={dateRange}
                            onChange={(val) => setDateRange(val as [Date | null, Date | null])}
                            placeholder={t("period.customPlaceholder")}
                            w={260}
                            clearable
                        />
                    )}
                    <Select
                        value={overviewPeriod}
                        onChange={(value) => setOverviewPeriod((value ?? "weekly") as OverviewPeriod)}
                        data={[
                            { value: "allTime", label: t("period.allTime") },
                            { value: "yearly", label: t("period.yearly") },
                            { value: "monthly", label: t("period.monthly") },
                            { value: "weekly", label: t("period.weekly") },
                            { value: "custom", label: t("period.custom") },
                        ]}
                        allowDeselect={false}
                        w={140}
                    />
                </Group>
            </Group>

            <PayoutHistorySection
                rows={payoutHistoryRows}
                currentPage={payoutPage}
                totalPages={payoutTotalPages}
                onPageChange={setPayoutPage}
            />
        </Stack>
    );
}
