import type {
    PayoutAmountPoint,
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
