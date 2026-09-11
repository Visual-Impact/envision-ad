import type {
    PayoutHistoryRow,
    StripeDashboardPayout,
} from "@/pages/dashboard/media-owner/model/metricsTypes";
import { parseNumericValue } from "@/pages/dashboard/media-owner/lib/metrics/shared-utils";

export const mapPayoutsToRows = (
    payouts: StripeDashboardPayout[]
): PayoutHistoryRow[] => {
    const nowUnix = Math.floor(Date.now() / 1000);

    return [...payouts]
        .sort(
            (a, b) =>
                (parseNumericValue(b.created) ?? 0) - (parseNumericValue(a.created) ?? 0)
        )
        .map((payout, index): PayoutHistoryRow => {
            const createdAtUnix = parseNumericValue(payout.created) ?? nowUnix;
            const availableOnUnix = parseNumericValue(payout.availableOn);
            const grossAmountMinor = parseNumericValue(payout.amount);
            const netAmountMinor = parseNumericValue(payout.net);
            const amountMinor = grossAmountMinor ?? netAmountMinor ?? 0;

            const transactionId =
                typeof payout.id === "string" && payout.id.trim().length > 0
                    ? payout.id
                    : `PAY-${(index + 1).toString().padStart(4, "0")}`;

            const date = new Date(createdAtUnix * 1000).toISOString().slice(0, 10);

            return {
                transactionId,
                date,
                amount: amountMinor / 100,
                status:
                    availableOnUnix !== null && availableOnUnix > nowUnix
                        ? "PROCESSING"
                        : "PAID",
            };
        });
};
