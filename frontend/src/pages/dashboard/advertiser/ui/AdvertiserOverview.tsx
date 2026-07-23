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
import { MetricCard } from "@/widgets/Cards/MetricCard";


const getDateKey = (date: Date, timeRange: string | null): string => {
    if (timeRange === "Weekly") {
        return date.toLocaleDateString('en-US', { weekday: 'short' });
    } else if (timeRange === "Monthly") {
        const day = date.getDate();
        const weekNum = Math.ceil(day / 7);
        return `Week ${weekNum}`;
    } else {
        return date.toLocaleDateString('en-US', { month: 'short' });
    }
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
    const [activeCampaignCount, setActiveCampaignCount] = useState<number | null>(null);
    const [totalSpend, setTotalSpend] = useState<number>(0);

    const [estimatedImpressions, setEstimatedImpressions] = useState<number>(0);
    const [averageCPM, setAverageCPM] = useState<number>(0);
    const [chartData, setChartData] = useState<ChartDataPoint[]>([]);

    useEffect(() => {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setMounted(true);
    }, []);

    useEffect(() => {
        const fetchData = async () => {
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

                    // 2. Get Active Campaign Count
                    const countResponse = await fetch(`${process.env.NEXT_PUBLIC_API_URL}/businesses/${businessId}/campaigns/active-count`, {
                        headers: { Authorization: `Bearer ${token}` }
                    });

                    if (countResponse.ok) {
                        const count = await countResponse.json();
                        setActiveCampaignCount(count);
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
                        return;
                    }

                    const data = await dashboardResponse.json();

                    let calculatedTotal = 0;

                    // Process Chart Data
                    const paymentsByDate: Record<string, { spend: number }> = {};

                    // Process Spend (Outgoing)
                    if (data.payments && Array.isArray(data.payments)) {
                        data.payments.forEach((payment: Payment) => {
                            const date = new Date(payment.created * 1000);
                            const dateKey = getDateKey(date, timeRange);
                            if (!paymentsByDate[dateKey]) paymentsByDate[dateKey] = { spend: 0 };
                            paymentsByDate[dateKey].spend += payment.amount;
                            calculatedTotal += payment.amount;
                        });
                    }

                    // Set Total Spend from manual calculation of all transactions
                    setTotalSpend(calculatedTotal);

                    if (data.estimatedImpressions !== undefined) setEstimatedImpressions(data.estimatedImpressions);
                    if (data.averageCPM !== undefined) setAverageCPM(data.averageCPM);



                    // Transform to Chart Data Array
                    let templateData = [];
                    if (timeRange === "Weekly") {
                        const days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
                        templateData = days.map(d => ({ date: d, Spend: 0 }));
                    } else if (timeRange === "Monthly") {
                        templateData = [
                            { date: "Week 1", Spend: 0 },
                            { date: "Week 2", Spend: 0 },
                            { date: "Week 3", Spend: 0 },
                            { date: "Week 4", Spend: 0 },
                            { date: "Week 5", Spend: 0 }
                        ];
                    } else {
                        const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
                        templateData = months.map(m => ({ date: m, Spend: 0 }));
                    }

                    // Fill template with real data
                    const filledData = templateData.map(item => ({
                        ...item,
                        Spend: (paymentsByDate[item.date]?.spend || 0), // Already in dollars (BigDecimal from backend)
                    }));

                    setChartData(filledData);
                }
            } catch (error) {
                console.error("Failed to fetch dashboard data:", error);
            }
        };
        fetchData();
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
            title: t("graphs.activeCampaigns"),
            value: activeCampaignCount !== null ? activeCampaignCount.toString() : "-",
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
