"use client";

import type { CSSProperties } from "react";
import { Alert } from "@mantine/core";
import { IconAlertTriangle, IconClock } from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import Link from "next/link";
import type { StripeAccountStatus } from "@/features/payment";

interface StripeRequiredAlertProps {
    status: StripeAccountStatus | null;
    style?: CSSProperties;
}

/**
 * Shown in place of media creation until the owner's Stripe account is ready.
 * An owner whose details Stripe is still verifying has nothing left to do, so
 * they get a "being reviewed" notice rather than a call to set up Stripe.
 */
export function StripeRequiredAlert({ status, style }: StripeRequiredAlertProps) {
    const t = useTranslations("media");

    if (status?.state === "PENDING_VERIFICATION") {
        return (
            <Alert icon={<IconClock size="1rem" />} title={t("stripe.pendingTitle")} color="blue" style={style}>
                {t("stripe.pendingMessage")}
                <Link href="/dashboard/stripe" style={{ color: "var(--mantine-color-blue-7)", textDecoration: "underline" }}>
                    {t("stripe.pendingLink")}
                </Link>
            </Alert>
        );
    }

    return (
        <Alert icon={<IconAlertTriangle size="1rem" />} title={t("stripe.requiredTitle")} color="orange" style={style}>
            {t("stripe.requiredMessage")}
            <Link href="/dashboard/stripe" style={{ color: "var(--mantine-color-orange-7)", textDecoration: "underline" }}>
                {t("stripe.requiredLink")}
            </Link>
        </Alert>
    );
}
