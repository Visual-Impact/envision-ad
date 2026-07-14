"use client";

import { useState, useEffect, useMemo } from "react";
import { Box, Grid, Group, Paper, Select, Skeleton, Stack, Text, Title, Checkbox, Pagination, Divider } from "@mantine/core";
import { DatePickerInput } from "@mantine/dates";
import { BarChart } from "@mantine/charts";
import { useMounted } from "@mantine/hooks";
import { useTranslations, useLocale } from "next-intl";
import { ActiveCampaignDetailsSection } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/ActiveCampaignDetailsSection";
import { ChartTooltipContent } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/ChartTooltipContent";
import { KpiCard } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/KpiCard";
import { MediaScreensTimeline } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/MediaScreensTimeline";
import { PayoutHistorySection } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/PayoutHistorySection";
import { RevenueByMediaSection } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/RevenueByMediaSection";
import { formatCurrency } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/formatting-utils";
import type {
    ChartTooltipPayload,
    EarningsTrendPoint,
    OverviewPeriod,
} from "@/pages/dashboard/media-owner/ui/metrics-dashboard/types";
import { getUniqueTooltipItems } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/tooltip-utils";
import { useMediaOwnerMetricsData } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/useMediaOwnerMetricsData";

export default function MediaOwnerMetricsDashboard() {
    const t = useTranslations("mediaOwnerMetrics");
    const locale = useLocale();
    const mounted = useMounted();
    const {
        overviewPeriod,
        setOverviewPeriod,
        dateRange,
        setDateRange,
        kpis,
        earningsTrend,
        overviewMetricsData,
        payoutHistoryRows,
        payoutPage,
        payoutTotalPages,
        setPayoutPage,
        revenueByMediaLocationRows,
        revenueByMediaLocationPage,
        revenueByMediaLocationTotalPages,
        revenueByMediaLocationRowsPerPage,
        setRevenueByMediaLocationPage,
        activeCampaignDetailsRows,
        activeCampaignDetailsPage,
        activeCampaignDetailsTotalPages,
        activeCampaignDetailsRowsPerPage,
        setActiveCampaignDetailsPage,
        selectedMediaLocationId,
        setSelectedMediaLocationId,
        mediaScreensTimelineData,
        mediaLocations,
    } = useMediaOwnerMetricsData();

    const revenueByLocationChartData = overviewMetricsData.revenueByLocation.map((item) => ({
        ...item,
        cityLabel: `${item.city} (City)`,
    }));
    const earningsTrendSectionTitle = t("sections.earningsTrend");

    const [selectedMediaNames, setSelectedMediaNames] = useState<string[]>([]);
    const [legendPage, setLegendPage] = useState(1);
    const legendItemsPerPage = 5;
    const DEFAULT_SELECTED_MEDIA_COUNT = 5;

    // Initialise to the first N media once data loads; runs only once
    useEffect(() => {
        if (selectedMediaNames.length === 0 && overviewMetricsData.revenueByMediaLocation.length > 0) {
            setSelectedMediaNames(
                overviewMetricsData.revenueByMediaLocation
                    .slice(0, DEFAULT_SELECTED_MEDIA_COUNT)
                    .map((m) => m.locationName)
            );
        }
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [overviewMetricsData.revenueByMediaLocation]);

    const activeSeries = overviewMetricsData.revenueByMediaLocation
        .filter((m) => selectedMediaNames.includes(m.locationName))
        .map((m) => ({ name: m.locationName, color: m.color }));

    const totalLegendPages = Math.ceil(
        overviewMetricsData.revenueByMediaLocation.length / legendItemsPerPage
    );
    const paginatedLegendItems = overviewMetricsData.revenueByMediaLocation.slice(
        (legendPage - 1) * legendItemsPerPage,
        legendPage * legendItemsPerPage
    );

    const toggleMediaSelection = (mediaName: string) => {
        setSelectedMediaNames((prev) =>
            prev.includes(mediaName)
                ? prev.filter((name) => name !== mediaName)
                : [...prev, mediaName]
        );
    };

    // ── Timeline media filter ────────────────────────────────────────────────
    const [selectedTimelineMediaIds, setSelectedTimelineMediaIds] = useState<string[]>([]);
    const [timelineLegendPage, setTimelineLegendPage] = useState(1);
    const TIMELINE_LEGEND_PER_PAGE = 5;

    // Initialise to all rows on first load, and re-sync whenever the location rows change
    useEffect(() => {
        if (mediaScreensTimelineData.rows.length > 0) {
            setSelectedTimelineMediaIds(mediaScreensTimelineData.rows.map((r) => r.mediaId));
            setTimelineLegendPage(1);
        }
    }, [mediaScreensTimelineData.rows]);

    const filteredTimelineData = {
        ...mediaScreensTimelineData,
        rows: mediaScreensTimelineData.rows.filter((r) =>
            selectedTimelineMediaIds.includes(r.mediaId)
        ),
    };

    const totalTimelineLegendPages = Math.ceil(
        mediaScreensTimelineData.rows.length / TIMELINE_LEGEND_PER_PAGE
    );
    const paginatedTimelineLegendItems = mediaScreensTimelineData.rows.slice(
        (timelineLegendPage - 1) * TIMELINE_LEGEND_PER_PAGE,
        timelineLegendPage * TIMELINE_LEGEND_PER_PAGE
    );

    const toggleTimelineMedia = (mediaId: string) => {
        setSelectedTimelineMediaIds((prev) =>
            prev.includes(mediaId)
                ? prev.filter((id) => id !== mediaId)
                : [...prev, mediaId]
        );
    };

    const EARNINGS_TREND_SERIES = activeSeries;

    /** Maps raw Recharts series keys to their translated display labels. */
    const seriesLabelMap: Record<string, string> = {
        revenue: t("table.amount"),
    };

    // Build a count-based trend: each location's bar height = number of reservations
    // (not revenue), scoped to the currently selected series so the Y axis represents
    // reservation counts. totalAmount is preserved for the tooltip.
    const reservationCountTrend = useMemo(
        () =>
            earningsTrend.map((point) => {
                const p = point as Record<string, number>;
                const locationCounts = EARNINGS_TREND_SERIES.reduce(
                    (acc, s) => {
                        acc[s.name] = p[`${s.name}_count`] ?? 0;
                        return acc;
                    },
                    {} as Record<string, number>
                );

                return {
                    date: point.date,
                    totalAmount: point.totalAmount,
                    reservationCount: Object.values(locationCounts).reduce((a, b) => a + b, 0),
                    averageAmount: point.averageAmount,
                    ...locationCounts,
                };
            }),
        [earningsTrend, EARNINGS_TREND_SERIES]
    );

    const renderEarningsTrendTooltip = (
        payload: ChartTooltipPayload[] | undefined,
        label: string | number | undefined
    ) => {
        if (!payload || payload.length === 0) return null;
        // The raw data point is attached by Recharts at payload[0].payload
        const raw = (payload[0] as unknown as { payload?: EarningsTrendPoint })?.payload;
        const labelText = label == null ? "" : String(label);
        const total = raw?.totalAmount ?? 0;
        const count = raw?.reservationCount ?? 0;
        const avg = raw?.averageAmount ?? 0;
        if (total === 0 && count === 0) return null;
        return (
            <Paper px="md" py="xs" withBorder shadow="md" radius="md">
                <Text fw={500} mb={5}>{labelText}</Text>
                <Group gap="xs" justify="space-between" mt={4}>
                    <Text size="sm" c="dimmed">{t("earningsTrend.reservations")}</Text>
                    <Text size="sm" fw={500}>{count}</Text>
                </Group>
                <Group gap="xs" justify="space-between" mt={4}>
                    <Text size="sm" c="dimmed">{t("earningsTrend.totalAmount")}</Text>
                    <Text size="sm" fw={500}>{formatCurrency(total, { locale })}</Text>
                </Group>
                <Group gap="xs" justify="space-between" mt={4}>
                    <Text size="sm" c="dimmed">{t("earningsTrend.averageValue")}</Text>
                    <Text size="sm" fw={500}>{formatCurrency(avg, { locale })}</Text>
                </Group>
            </Paper>
        );
    };

    const renderTooltip = (
        payload: ChartTooltipPayload[] | undefined,
        label: string | number | undefined
    ) => {
        if (!payload || payload.length === 0) return null;
        const uniquePayload = getUniqueTooltipItems(payload).map((item) => ({
            ...item,
            name: seriesLabelMap[item.name] ?? item.name,
        }));

        const labelText = label == null ? "" : String(label);
        return <ChartTooltipContent labelText={labelText} items={uniquePayload} />;
    };

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
                            clearable
                            w={260}
                        />
                    )}
                    <Select
                        value={overviewPeriod}
                        onChange={(value) =>
                            setOverviewPeriod((value as OverviewPeriod) ?? "allTime")
                        }
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

            <Grid gutter="md">
                <Grid.Col span={{ base: 12, lg: 6 }}>
                    <Paper shadow="sm" p="md" radius="lg">
                        <Text fw={600} mb="sm">
                            {earningsTrendSectionTitle}
                        </Text>
                        <Box w="100%" h={280} style={{ minWidth: 0 }}>
                            {mounted ? (
                                <BarChart
                                    type="stacked"
                                    h={260}
                                    data={reservationCountTrend}
                                    dataKey="date"
                                    series={EARNINGS_TREND_SERIES}
                                    withLegend={false}
                                    gridAxis="xy"
                                    tickLine="xy"
                                    tooltipProps={{
                                        content: ({ payload, label }) =>
                                            renderEarningsTrendTooltip(
                                                payload as unknown as ChartTooltipPayload[] | undefined,
                                                label
                                            ),
                                    }}
                                />
                            ) : (
                                <Skeleton h={260} radius="md" />
                            )}
                        </Box>
                        {overviewMetricsData.revenueByMediaLocation.length > 0 && (

                            <Stack mt="md" gap="sm">
                                <Group gap="md">
                                    {paginatedLegendItems.map((media) => (
                                        <Checkbox
                                            key={media.locationName}
                                            label={media.locationName}
                                            color={media.color.split(".")[0]}
                                            checked={selectedMediaNames.includes(media.locationName)}
                                            onChange={() => toggleMediaSelection(media.locationName)}
                                        />
                                    ))}
                                </Group>
                                {totalLegendPages > 1 && (
                                    <Group justify="center" mt="xs">
                                        <Pagination
                                            size="sm"
                                            total={totalLegendPages}
                                            value={legendPage}
                                            onChange={setLegendPage}
                                        />
                                    </Group>
                                )}
                            </Stack>
                        )}
                    </Paper>
                </Grid.Col>

                <Grid.Col span={{ base: 12, lg: 6 }}>
                    <Paper shadow="sm" p="md" radius="lg" h="100%">
                        <Text fw={600} mb="sm">
                            {t("sections.revenueByLocation")}
                        </Text>
                        {revenueByLocationChartData.length === 0 ? (
                            <Text size="sm" c="dimmed">
                                {t("emptyState.noData")}
                            </Text>
                        ) : (
                            <Box w="100%" h={280} style={{ minWidth: 0 }}>
                                {mounted ? (
                                    <BarChart
                                        h={260}
                                        data={revenueByLocationChartData}
                                        dataKey="cityLabel"
                                        series={[{ name: "revenue", color: "blue.6" }]}
                                        withLegend={false}
                                        tickLine="xy"
                                        gridAxis="y"
                                        tooltipProps={{
                                            cursor: false,
                                            content: ({ payload, label }) =>
                                                renderTooltip(
                                                    payload as unknown as ChartTooltipPayload[] | undefined,
                                                    label
                                                ),
                                        }}
                                    />
                                ) : (
                                    <Skeleton h={260} radius="md" />
                                )}
                            </Box>
                        )}
                    </Paper>
                </Grid.Col>
            </Grid>

            {/* Media Screens Reservation Trend Section */}
            <Grid gutter="md">
                <Grid.Col span={12}>
                    <Paper shadow="sm" p="md" radius="lg">
                        <Group justify="space-between" align="center" mb="sm">
                            <Text fw={600}>
                                {t("sections.mediaReservations")}
                            </Text>
                            {mediaLocations.length > 0 && (
                                <Select
                                    value={selectedMediaLocationId}
                                    onChange={setSelectedMediaLocationId}
                                    data={mediaLocations.map((loc) => ({
                                        value: loc.id,
                                        label: loc.name,
                                    }))}
                                    allowDeselect={false}
                                    w={300}
                                />
                            )}
                        </Group>

                        {mediaScreensTimelineData.rows.length === 0 ? (
                            <Text size="sm" c="dimmed">
                                {t("emptyState.noData")}
                            </Text>
                        ) : (
                            <Box w="100%" h="100%" style={{ minWidth: 0 }}>
                                {mounted ? (
                                    <MediaScreensTimeline data={filteredTimelineData} />
                                ) : (
                                    <Skeleton h={260} radius="md" />
                                )}
                                <Stack mt="md" gap="sm">
                                    <Group gap="md" wrap="wrap">
                                        {paginatedTimelineLegendItems.map((row) => (
                                            <Checkbox
                                                key={row.mediaId}
                                                label={row.mediaName}
                                                color={row.color.split(".")[0]}
                                                checked={selectedTimelineMediaIds.includes(row.mediaId)}
                                                onChange={() => toggleTimelineMedia(row.mediaId)}
                                            />
                                        ))}
                                    </Group>
                                    {totalTimelineLegendPages > 1 && (
                                        <Group justify="center" mt="xs">
                                            <Pagination
                                                size="sm"
                                                total={totalTimelineLegendPages}
                                                value={timelineLegendPage}
                                                onChange={setTimelineLegendPage}
                                            />
                                        </Group>
                                    )}
                                </Stack>
                            </Box>
                        )}
                    </Paper>
                </Grid.Col>
            </Grid>

            <Grid gutter="md">
                <Grid.Col span={{ base: 12, lg: 6 }}>
                    <RevenueByMediaSection
                        rows={revenueByMediaLocationRows}
                        totalRows={overviewMetricsData.revenueByMediaLocation.length}
                        currentPage={revenueByMediaLocationPage}
                        totalPages={revenueByMediaLocationTotalPages}
                        rowsPerPage={revenueByMediaLocationRowsPerPage}
                        onPageChange={setRevenueByMediaLocationPage}
                    />
                </Grid.Col>

                <Grid.Col span={{ base: 12, lg: 6 }}>
                    <ActiveCampaignDetailsSection
                        rows={activeCampaignDetailsRows}
                        totalRows={overviewMetricsData.activeCampaignDetails.length}
                        currentPage={activeCampaignDetailsPage}
                        totalPages={activeCampaignDetailsTotalPages}
                        rowsPerPage={activeCampaignDetailsRowsPerPage}
                        onPageChange={setActiveCampaignDetailsPage}
                    />
                </Grid.Col>
            </Grid>

            <PayoutHistorySection
                rows={payoutHistoryRows}
                currentPage={payoutPage}
                totalPages={payoutTotalPages}
                onPageChange={setPayoutPage}
            />
        </Stack>
    );
}
