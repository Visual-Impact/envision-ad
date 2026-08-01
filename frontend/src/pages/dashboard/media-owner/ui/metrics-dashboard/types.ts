import type { StripeDashboardPayout } from "@/features/payment";
import type {
    ActiveCampaignItem,
    EarningsTrendPoint,
    MetricsKpi,
    PayoutHistoryRow,
    RevenueByLocationItem,
    RevenueByLocationPoint,
} from "@/pages/dashboard/media-owner/model/mockMetrics";

export interface ChartTooltipPayload {
    dataKey?: string;
    name: string;
    value: number;
    color: string;
}

export interface PayoutAmountPoint {
    amount: number;
    createdAtUnix: number;
}

export type OverviewPeriod = "allTime" | "weekly" | "monthly" | "yearly" | "custom";
export type DateRangeMap = [Date | null, Date | null];

export interface EarningsDashboardData {
    kpis: MetricsKpi[];
}

export interface PaginationInfo<T> {
    totalPages: number;
    currentPage: number;
    rows: T[];
}

export interface BuildPaginationInput<T> {
    rows: T[];
    page: number;
    rowsPerPage: number;
}

export type {
    ActiveCampaignItem,
    EarningsTrendPoint,
    MetricsKpi,
    PayoutHistoryRow,
    RevenueByLocationItem,
    RevenueByLocationPoint,
    StripeDashboardPayout,
};
