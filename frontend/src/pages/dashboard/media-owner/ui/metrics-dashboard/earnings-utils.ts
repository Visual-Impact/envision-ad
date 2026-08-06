import type { MetricTrend } from "@/pages/dashboard/media-owner/model/mockMetrics";
import type {
    EarningsDashboardData,
    MetricsKpi,
    PayoutAmountPoint,
    StripeDashboardPayout,
} from "@/pages/dashboard/media-owner/ui/metrics-dashboard/types";
import {
    parseNumericValue,
    subtractDays,
    subtractMonths,
    subtractYears,
    sumAmountInRange,
} from "@/pages/dashboard/media-owner/ui/metrics-dashboard/shared-utils";

const toKpiTrend = (
    currentTotal: number,
    previousTotal: number
): { trend: MetricTrend; deltaPercent: number } => {
    if (previousTotal <= 0) {
        return {
            trend: "up",
            deltaPercent: 0,
        };
    }

    const changePercent = ((currentTotal - previousTotal) / previousTotal) * 100;

    return {
        trend: changePercent >= 0 ? "up" : "down",
        deltaPercent: Math.round(Math.abs(changePercent)),
    };
};

export const mapPayoutsToAmountPoints = (
    payouts: StripeDashboardPayout[]
): PayoutAmountPoint[] => {
    const nowUnix = Math.floor(Date.now() / 1000);

    return payouts.map((payout) => {
        const createdAtUnix = parseNumericValue(payout.created) ?? nowUnix;
        const grossAmountMinor = parseNumericValue(payout.amount);
        const netAmountMinor = parseNumericValue(payout.net);
        const amountMinor = grossAmountMinor ?? netAmountMinor ?? 0;

        return {
            createdAtUnix,
            amount: amountMinor / 100,
        };
    });
};

export const buildEarningsKpis = (
    points: PayoutAmountPoint[],
    activeCampaignCount = 0
): MetricsKpi[] => {
    const now = new Date();
    const nowUnix = Math.floor(now.getTime() / 1000);

    const weeklyStartUnix = Math.floor(subtractDays(now, 7).getTime() / 1000);
    const weeklyPreviousStartUnix = Math.floor(subtractDays(now, 14).getTime() / 1000);
    const weeklyTotal = sumAmountInRange(points, weeklyStartUnix, nowUnix);
    const previousWeeklyTotal = sumAmountInRange(
        points,
        weeklyPreviousStartUnix,
        weeklyStartUnix
    );

    const monthlyStartUnix = Math.floor(subtractMonths(now, 1).getTime() / 1000);
    const monthlyPreviousStartUnix = Math.floor(subtractMonths(now, 2).getTime() / 1000);
    const monthlyTotal = sumAmountInRange(points, monthlyStartUnix, nowUnix);
    const previousMonthlyTotal = sumAmountInRange(
        points,
        monthlyPreviousStartUnix,
        monthlyStartUnix
    );

    const yearlyStartUnix = Math.floor(subtractYears(now, 1).getTime() / 1000);
    const yearlyPreviousStartUnix = Math.floor(subtractYears(now, 2).getTime() / 1000);
    const yearlyTotal = sumAmountInRange(points, yearlyStartUnix, nowUnix);
    const previousYearlyTotal = sumAmountInRange(
        points,
        yearlyPreviousStartUnix,
        yearlyStartUnix
    );

    const weeklyTrend = toKpiTrend(weeklyTotal, previousWeeklyTotal);
    const monthlyTrend = toKpiTrend(monthlyTotal, previousMonthlyTotal);
    const yearlyTrend = toKpiTrend(yearlyTotal, previousYearlyTotal);

    const activeCampaignsKpi = {
        id: "activeCampaigns",
        value: activeCampaignCount,
        deltaPercent: 0,
        trend: "up" as MetricTrend,
    };

    return [
        {
            id: "weeklyEarnings",
            value: weeklyTotal,
            deltaPercent: weeklyTrend.deltaPercent,
            trend: weeklyTrend.trend,
        },
        {
            id: "monthlyEarnings",
            value: monthlyTotal,
            deltaPercent: monthlyTrend.deltaPercent,
            trend: monthlyTrend.trend,
        },
        {
            id: "yearlyEarnings",
            value: yearlyTotal,
            deltaPercent: yearlyTrend.deltaPercent,
            trend: yearlyTrend.trend,
        },
        activeCampaignsKpi,
    ];
};


export const buildEarningsDashboardData = (
    payouts: StripeDashboardPayout[],
    activeCampaignCount = 0
): EarningsDashboardData => {
    const points = mapPayoutsToAmountPoints(payouts);
    return {
        kpis: buildEarningsKpis(points, activeCampaignCount),
    };
};

export const applyActiveCampaignCountToKpis = (
    sourceKpis: MetricsKpi[],
    activeCampaignCount: number
) =>
    sourceKpis.map((kpi) =>
        kpi.id === "activeCampaigns" ? { ...kpi, value: activeCampaignCount } : kpi
    );
