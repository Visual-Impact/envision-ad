import type { ChartTooltipPayload } from "@/pages/dashboard/media-owner/model/metricsTypes";

export const getUniqueTooltipItems = (payload: ChartTooltipPayload[]) => {
    const seen = new Set<string>();
    return payload.filter((item) => {
        const seriesKey = item.dataKey ?? item.name;
        const key = `${seriesKey}-${item.value}`;
        if (seen.has(key)) return false;
        seen.add(key);
        return true;
    });
};
