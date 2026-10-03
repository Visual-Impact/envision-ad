"use client";

import { Text } from "@mantine/core";
import { useLocale, useTranslations } from "next-intl";
import { BundlePriceQuote } from "../model/bundle";
import { formatCurrency } from "@/shared/lib/formatCurrency";

/**
 * P4: the card's numbers are the public ones; when this advertiser's business type removes screens
 * from the bundle, say how many and what they would actually pay, so it isn't a surprise in the
 * subscribe modal. Renders nothing when nothing is excluded.
 */
export function BuyerQuoteNote({ buyerQuote }: { buyerQuote?: BundlePriceQuote | null }) {
    const t = useTranslations("bundles");
    const locale = useLocale();

    if (!buyerQuote || buyerQuote.excludedScreenCount <= 0) return null;

    return (
        <Text size="sm" fw={600} c="orange.8">
            {buyerQuote.screenCount > 0
                ? t("card.buyerQuote", {
                      count: buyerQuote.excludedScreenCount,
                      price: formatCurrency(buyerQuote.finalPrice, { locale }),
                  })
                : t("card.notAvailableForBusinessType")}
        </Text>
    );
}

/** True when the advertiser's business type removes every screen from the bundle. */
export function isUnavailableForBuyer(buyerQuote?: BundlePriceQuote | null): boolean {
    return !!buyerQuote && buyerQuote.screenCount === 0 && buyerQuote.excludedScreenCount > 0;
}
