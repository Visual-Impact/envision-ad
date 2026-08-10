import type {
    DateRangeMap,
    OverviewPeriod,
    PayoutAmountPoint,
    StripeDashboardPayout,
} from "@/pages/dashboard/media-owner/ui/metrics-dashboard/types";

export const parseNumericValue = (value: unknown): number | null => {
    if (typeof value === "number" && Number.isFinite(value)) {
        return value;
    }
    if (typeof value === "string") {
        const parsed = Number(value);
        return Number.isFinite(parsed) ? parsed : null;
    }
    return null;
};

export const parseDateMs = (value: string | undefined) => {
    if (!value) return null;
    const timestamp = Date.parse(value);
    return Number.isFinite(timestamp) ? timestamp : null;
};

export const normalizeText = (value: string | undefined, fallback: string) => {
    if (!value) return fallback;
    const trimmed = value.trim();
    return trimmed.length > 0 ? trimmed : fallback;
};

export const subtractDays = (date: Date, days: number) => {
    const next = new Date(date);
    next.setDate(next.getDate() - days);
    return next;
};

export const subtractMonths = (date: Date, months: number) => {
    const next = new Date(date);
    next.setMonth(next.getMonth() - months);
    return next;
};

export const subtractYears = (date: Date, years: number) => {
    const next = new Date(date);
    next.setFullYear(next.getFullYear() - years);
    return next;
};

export const startOfDay = (date: Date) => {
    const next = new Date(date);
    next.setHours(0, 0, 0, 0);
    return next;
};

export const formatTrendDate = (date: Date) =>
    date.toLocaleDateString("en-US", {
        month: "short",
        day: "numeric",
    });

export const formatTrendMonth = (date: Date) =>
    date.toLocaleDateString("en-US", {
        month: "short",
        year: "2-digit",
    });

/**
 * Filters the raw payout list down to the window implied by the selected overview period,
 * so the "Weekly/Monthly/Yearly/All Time/Custom" selector next to the payout history table
 * actually changes what it shows instead of always displaying the full unfiltered list.
 */
export const filterPayoutsByPeriod = (
    payouts: StripeDashboardPayout[],
    period: OverviewPeriod,
    dateRange: DateRangeMap
): StripeDashboardPayout[] => {
    if (period === "allTime") return payouts;

    const now = new Date();
    let startUnix: number;
    let endUnix = Math.floor(now.getTime() / 1000);

    if (period === "custom") {
        const [start, end] = dateRange;
        if (!start || !end) return payouts;
        startUnix = Math.floor(startOfDay(start).getTime() / 1000);
        const endOfDay = new Date(end);
        endOfDay.setHours(23, 59, 59, 999);
        endUnix = Math.floor(endOfDay.getTime() / 1000);
    } else if (period === "weekly") {
        startUnix = Math.floor(subtractDays(now, 7).getTime() / 1000);
    } else if (period === "yearly") {
        startUnix = Math.floor(subtractYears(now, 1).getTime() / 1000);
    } else {
        startUnix = Math.floor(subtractMonths(now, 1).getTime() / 1000);
    }

    return payouts.filter((payout) => {
        const createdAtUnix = parseNumericValue(payout.created);
        return createdAtUnix !== null && createdAtUnix >= startUnix && createdAtUnix <= endUnix;
    });
};

export const sumAmountInRange = (
    points: PayoutAmountPoint[],
    startUnix: number,
    endUnix: number
) =>
    points.reduce((total, point) => {
        if (point.createdAtUnix >= startUnix && point.createdAtUnix < endUnix) {
            return total + point.amount;
        }
        return total;
    }, 0);
