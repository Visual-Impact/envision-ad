"use client";

import React, { useState, useEffect } from "react";
import {
    Grid,
    Paper,
    Text,
    Group,
    Stack,
    SegmentedControl,
    ThemeIcon,
    Title,
    Box,
    Skeleton,
} from "@mantine/core";
import {
    IconCoin,
    IconSpeakerphone,
    IconEye,
    IconChartBar,
} from "@tabler/icons-react";
import { AreaChart } from "@mantine/charts";
import { useTranslations } from "next-intl";

import { jwtDecode } from "jwt-decode";
import { MetricCard } from "@/shared/ui";


const MS_PER_DAY = 24 * 60 * 60 * 1000;

const daysBetween = (a: Date, b: Date): number => Math.floor((b.getTime() - a.getTime()) / MS_PER_DAY);

// Mirrors StripeServiceImpl#calculateStartDate on the backend: the dashboard endpoint queries a
// rolling window ending "now", not a calendar-aligned week/month/year. Buckets below are built
// relative to that same window so labels line up chronologically instead of colliding across
// month/year boundaries (e.g. day-of-month 9 in both July and August landing in the same bucket).
const getWindowStart = (timeRange: string, now: Date): Date => {
    const start = new Date(now);
    if (timeRange === "Weekly") {
        start.setDate(start.getDate() - 7);
    } else if (timeRange === "Monthly") {
        start.setMonth(start.getMonth() - 1);
    } else {
        start.setFullYear(start.getFullYear() - 1);
    }
    return start;
};

interface Bucket {
    key: string;
    label: string;
}

const buildBuckets = (timeRange: string, now: Date): { buckets: Bucket[]; keyFor: (d: Date) => string } => {
    if (timeRange === "Weekly") {
        const buckets: Bucket[] = [];
        for (let daysAgo = 6; daysAgo >= 0; daysAgo--) {
            const d = new Date(now);
            d.setDate(d.getDate() - daysAgo);
            buckets.push({ key: `d${daysAgo}`, label: d.toLocaleDateString('en-US', { weekday: 'short' }) });
        }
        const keyFor = (d: Date) => `d${Math.min(6, Math.max(0, daysBetween(d, now)))}`;
        return { buckets, keyFor };
    }

    if (timeRange === "Monthly") {
        const start = getWindowStart("Monthly", now);
        const totalDays = Math.max(1, daysBetween(start, now));
        const numWeeks = Math.ceil(totalDays / 7);
        const buckets: Bucket[] = [];
        for (let w = 0; w < numWeeks; w++) {
            buckets.push({ key: `w${w}`, label: `Week ${w + 1}` });
        }
        const keyFor = (d: Date) => {
            const offsetDays = Math.min(totalDays - 1, Math.max(0, daysBetween(start, d)));
            return `w${Math.min(numWeeks - 1, Math.floor(offsetDays / 7))}`;
        };
        return { buckets, keyFor };
    }

    // Yearly
    const start = getWindowStart("Yearly", now);
    const totalMonths = (now.getFullYear() - start.getFullYear()) * 12 + (now.getMonth() - start.getMonth()) + 1;
    const buckets: Bucket[] = [];
    for (let m = 0; m < totalMonths; m++) {
        const d = new Date(start.getFullYear(), start.getMonth() + m, 1);
        buckets.push({ key: `m${m}`, label: d.toLocaleDateString('en-US', { month: 'short' }) });
    }
    const keyFor = (d: Date) => {
        const offsetMonths = (d.getFullYear() - start.getFullYear()) * 12 + (d.getMonth() - start.getMonth());
        return `m${Math.min(totalMonths - 1, Math.max(0, offsetMonths))}`;
    };
    return { buckets, keyFor };
};

interface DecodedToken {
    sub: string;
    [key: string]: unknown;
}

interface Payment {
    created: number;
    amount: number;
}

interface ChartDataPoint {
    date: string;
    Spend: number;
    [key: string]: number | string;
}

interface TooltipPayload {
    name: string;
    value: number;
    color: string;
}

export function AdvertiserOverview() {
    const t = useTranslations("advertiserMetrics");
    const [timeRange, setTimeRange] = useState<string>("Weekly");
    const [mounted, setMounted] = useState(false);
    const [activeCreativeCount, setActiveCreativeCount] = useState<number | null>(null);
    const [totalSpend, setTotalSpend] = useState<number>(0);

    const [estimatedImpressions, setEstimatedImpressions] = useState<number>(0);
    const [averageCPM, setAverageCPM] = useState<number>(0);
    const [chartData, setChartData] = useState<ChartDataPoint[]>([]);

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setMounted(true);
    }, []);

    useEffect(() => {
        let ignore = false;

        const fetchData = async () => {
            const now = new Date();
            const { buckets, keyFor } = buildBuckets(timeRange, now);
            const emptyChartData = buckets.map(b => ({ date: b.label, Spend: 0 }));

            try {
                const response = await fetch('/api/auth0/token');
                const { accessToken } = await response.json();
                const token = accessToken;
                const decodedToken = jwtDecode<DecodedToken>(token);
                const userId = decodedToken.sub;

                // 1. Get Business ID
                const businessResponse = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/businesses/employee/${encodeURIComponent(userId)}`, {
                    headers: { Authorization: `Bearer ${token}` }
                });

                if (businessResponse.ok) {
                    const business = await businessResponse.json();
                    const businessId = business.businessId;

                    // 2. Get the creative count of the active campaign ("what's on screen now")
                    const countResponse = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/businesses/${businessId}/campaigns/active-creative-count`, {
                        headers: { Authorization: `Bearer ${token}` }
                    });

                    if (countResponse.ok) {
                        const count = await countResponse.json();
                        if (!ignore) setActiveCreativeCount(count);
                    }

                    // 3. Get Dashboard Data (Spend & Payments)
                    const dashboardResponse = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/payments/dashboard?businessId=${businessId}&period=${timeRange || "Weekly"}`, {
                        headers: { Authorization: `Bearer ${token}` }
                    });

                    if (!dashboardResponse.ok) {
                        console.error(
                            "Failed to fetch dashboard data",
                            dashboardResponse.status,
                            dashboardResponse.statusText
                        );
                        // Reset to this period's empty state so a failed request doesn't leave the
                        // previous period's chart on screen mislabeled as the newly selected one.
                        if (!ignore) {
                            setTotalSpend(0);
                            setEstimatedImpressions(0);
                            setAverageCPM(0);
                            setChartData(emptyChartData);
                        }
                        return;
                    }

                    const data = await dashboardResponse.json();

                    let calculatedTotal = 0;

                    // Process Chart Data, bucketed relative to the rolling window (see buildBuckets)
                    const paymentsByKey: Record<string, number> = {};

                    // Process Spend (Outgoing)
                    if (data.payments && Array.isArray(data.payments)) {
                        data.payments.forEach((payment: Payment) => {
                            const date = new Date(payment.created * 1000);
                            const key = keyFor(date);
                            paymentsByKey[key] = (paymentsByKey[key] || 0) + payment.amount;
                            calculatedTotal += payment.amount;
                        });
                    }

                    if (ignore) return;

                    // Set Total Spend from manual calculation of all transactions
                    setTotalSpend(calculatedTotal);

                    if (data.estimatedImpressions !== undefined) setEstimatedImpressions(data.estimatedImpressions);
                    if (data.averageCPM !== undefined) setAverageCPM(data.averageCPM);

                    // Fill buckets with real data, oldest to newest left-to-right
                    const filledData = buckets.map(b => ({
                        date: b.label,
                        Spend: paymentsByKey[b.key] || 0, // Already in dollars (BigDecimal from backend)
                    }));

                    setChartData(filledData);
                } else {
                    console.error(
                        "Failed to fetch business for user",
                        businessResponse.status,
                        businessResponse.statusText
                    );
                    if (!ignore) {
                        setTotalSpend(0);
                        setEstimatedImpressions(0);
                        setAverageCPM(0);
                        setChartData(emptyChartData);
                    }
                }
            } catch (error) {
                console.error("Failed to fetch dashboard data:", error);
                // Same reasoning as the businessResponse/dashboardResponse failure branches above:
                // don't leave a previous period's numbers on screen mislabeled as the new period.
                if (!ignore) {
                    setTotalSpend(0);
                    setEstimatedImpressions(0);
                    setAverageCPM(0);
                    setChartData(emptyChartData);
                }
            }
        };
        fetchData();

        return () => {
            ignore = true;
        };
    }, [timeRange]);

    // Stats
    const stats = [
        {
            title: t("graphs.totalAdSpend"),
            value: `C$${(totalSpend).toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`,
            icon: IconCoin,
            color: "blue",
        },
        {
            title: t("graphs.activeCreatives"),
            value: activeCreativeCount !== null ? activeCreativeCount.toString() : "-",
            icon: IconSpeakerphone,
            color: "orange",
        },
        {
            title: t("graphs.estimatedImpressions"),
            value: estimatedImpressions.toLocaleString(),
            icon: IconEye,
            color: "teal",
        },
        {
            title: t("graphs.averageCPM"),
            value: `C$${averageCPM.toFixed(2)}`,
            icon: IconChartBar,
            color: "grape",
        },
    ];

    // Helper to render stats cards
    const items = stats.map((stat) => (
        <Grid.Col span={{ base: 12, sm: 6, lg: 3 }} key={stat.title}>
            <MetricCard
                label={stat.title}
                value={stat.value}
                color={stat.color}
                icon={<stat.icon size="1.4rem" stroke={1.5} />}
            />
        </Grid.Col>
    ));

    return (
        <Stack gap="lg" p="xl">
            <Group justify="space-between" align="center">
                <Title order={1}>{t("title")}</Title>
                <SegmentedControl
                    aria-label={t("timeRangeSelect")}
                    value={timeRange}
                    onChange={setTimeRange}
                    data={[
                        { value: "Weekly", label: t("timeRanges.weekly") },
                        { value: "Monthly", label: t("timeRanges.monthly") },
                        { value: "Yearly", label: t("timeRanges.yearly") },
                    ]}
                />
            </Group>

            <Grid gutter="xl">{items}</Grid>

            <Paper shadow="sm" p="xl" radius="lg">
                <Group justify="space-between" mb="md">
                    <Text size="lg" fw={600}>
                        {t("graphs.campaignPerformance")}
                    </Text>
                    <Group gap="xs">
                        <ThemeIcon variant="filled" color="cyan.6" size={10} radius="xl" />
                        <Text size="sm" fw={600} c="dimmed">
                            {t("graphs.spent")}
                        </Text>
                    </Group>
                </Group>
                <Box w="100%" h={370} style={{ minWidth: 0 }}>
                    {mounted ? (
                        <AreaChart
                            h={350}
                            data={chartData}
                            dataKey="date"
                            series={[
                                // Always show Spend series
                                { name: "Spend", label: t("graphs.spent"), color: "cyan.6" },
                            ]}
                            curveType="monotone"
                            gridAxis="xy"
                            tickLine="y"
                            withLegend={false}
                            withDots={false}
                            withPointLabels={false}
                            areaProps={{ label: false }}
                            tooltipProps={{
                                // eslint-disable-next-line @typescript-eslint/no-explicit-any
                                content: ({ payload, label }: any) => {
                                    if (!payload) return null;
                                    return (
                                        <Paper px="md" py="xs" withBorder shadow="md" radius="md">
                                            <Text fw={500} mb={5}>{label}</Text>
                                            {payload.map((item: TooltipPayload) => (
                                                <Group key={item.name} gap="xs" justify="space-between">
                                                    <Group gap={5}>
                                                        <ThemeIcon color={item.color} variant="filled" size={8} radius="xl" />
                                                        <Text size="sm" c="dimmed">{item.name === "Spend" ? t("graphs.spent") : t("graphs.impressions")}</Text>
                                                    </Group>
                                                    <Text size="sm" fw={500}>
                                                        {item.name === "Spend" ? `C$${item.value}` : item.value}
                                                    </Text>
                                                </Group>
                                            ))}
                                        </Paper>
                                    );
                                }
                            }}
                        />
                    ) : (
                        <Skeleton height={350} radius="md" />
                    )}
                </Box>
            </Paper>
        </Stack>
    );
}
