"use client";

import { useEffect, useMemo, useState } from "react";
import { getAllMediaLocations } from "@/features/media-location-management/api/getAllMediaLocations";
import { getPaymentsDashboardData } from "@/features/payment";
import type { MediaLocation } from "@/entities/media-location/model/mediaLocation";
import {
    type MetricsKpi,
    type PayoutHistoryRow,
} from "@/pages/dashboard/media-owner/model/mockMetrics";
import type {
    DateRangeMap,
    OverviewPeriod,
} from "@/pages/dashboard/media-owner/ui/metrics-dashboard/types";
import {
    buildEarningsDashboardData,
    buildEarningsKpis,
} from "@/pages/dashboard/media-owner/ui/metrics-dashboard/earnings-utils";
import { buildPaginationInfo } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/pagination-utils";
import { mapPayoutsToRows } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/payout-utils";
import { filterPayoutsByPeriod } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/shared-utils";
import type { StripeDashboardPayout } from "@/pages/dashboard/media-owner/ui/metrics-dashboard/types";
import { useOrganization } from "@/app/providers";

const PAYOUTS_PER_PAGE = 10;

/**
 * Media-owner metrics.
 *
 * P1 M6 (decision D41) narrowed this to what a bundle subscription can actually answer. The
 * reservation-derived sections — the screens-booked timeline, revenue by location, revenue by
 * media location, and active-campaign details — were all keyed on a per-reservation
 * `startDate`/`endDate` and a single `mediaId`. A subscription has no date range and spans many
 * screens at once, and the per-screen split lives in `bundle_subscription_items`, which no
 * endpoint exposes for a media owner. Rebuilding those charts on that data is tracked for M7.
 *
 * What survives is sourced from real money movement: earnings KPIs and payout history come from
 * the backend dashboard endpoint, which M6 re-pointed at the `bundle_payouts` ledger.
 */
export function useMediaOwnerMetricsData() {
    const [overviewPeriod, setOverviewPeriodState] = useState<OverviewPeriod>("weekly");
    const [dateRange, setDateRangeState] = useState<DateRangeMap>([null, null]);
    const [mediaLocations, setMediaLocations] = useState<MediaLocation[]>([]);
    const [selectedMediaLocationId, setSelectedMediaLocationId] = useState<string | null>(null);
    const { organization } = useOrganization();
    const [kpis, setKpis] = useState<MetricsKpi[]>(() => buildEarningsKpis([], 0));

    const [rawPayouts, setRawPayouts] = useState<StripeDashboardPayout[]>([]);
    const [payoutPage, setPayoutPage] = useState(1);

    const payoutHistoryRows: PayoutHistoryRow[] = useMemo(
        () => mapPayoutsToRows(filterPayoutsByPeriod(rawPayouts, overviewPeriod, dateRange)),
        [rawPayouts, overviewPeriod, dateRange]
    );

    const payoutPagination = useMemo(
        () => buildPaginationInfo({ rows: payoutHistoryRows, page: payoutPage, rowsPerPage: PAYOUTS_PER_PAGE }),
        [payoutHistoryRows, payoutPage]
    );

    useEffect(() => {
        if (!organization) return;

        let isCancelled = false;

        const fetchMetrics = async () => {
            try {
                if (!organization?.businessId) return;

                const [dashboardDataResult, locationsResult] =
                    await Promise.allSettled([
                        getPaymentsDashboardData(organization.businessId, "monthly"),
                        getAllMediaLocations(organization.businessId)
                    ]);

                if (isCancelled) return;

                if (dashboardDataResult.status === "fulfilled") {
                    const payouts = Array.isArray(dashboardDataResult.value.payouts)
                        ? dashboardDataResult.value.payouts : [];
                    const earningsDashboardData = buildEarningsDashboardData(payouts, 0);
                    setKpis(earningsDashboardData.kpis);
                    setRawPayouts(payouts);
                    setPayoutPage(1);
                } else {
                    console.error("Failed to load payout history", dashboardDataResult.reason);
                    setKpis(buildEarningsKpis([], 0));
                    setRawPayouts([]);
                    setPayoutPage(1);
                }

                if (locationsResult.status === "fulfilled") {
                    const locations = Array.isArray(locationsResult.value) ? locationsResult.value : [];
                    setMediaLocations(locations);
                    if (locations.length > 0) {
                        setSelectedMediaLocationId(locations[0].id);
                    }
                } else {
                    console.error("Failed to load media locations", locationsResult.reason);
                    setMediaLocations([]);
                }
            } catch (error) {
                if (!isCancelled) {
                    console.error("Failed to load media owner metrics", error);
                    setKpis(buildEarningsKpis([], 0));
                    setRawPayouts([]);
                    setMediaLocations([]);
                    setPayoutPage(1);
                }
            }
        };

        void fetchMetrics();

        return () => { isCancelled = true; };
    }, [organization]);

    const setOverviewPeriod = (period: OverviewPeriod) => {
        setOverviewPeriodState(period);
        setPayoutPage(1);
    };

    const setDateRange = (range: DateRangeMap) => {
        setDateRangeState(range);
        setPayoutPage(1);
    };

    return {
        overviewPeriod,
        setOverviewPeriod,
        dateRange,
        setDateRange,
        kpis,
        payoutHistoryRows: payoutPagination.rows,
        payoutPage: payoutPagination.currentPage,
        payoutTotalPages: payoutPagination.totalPages,
        setPayoutPage,
        selectedMediaLocationId,
        setSelectedMediaLocationId,
        mediaLocations,
    };
}
