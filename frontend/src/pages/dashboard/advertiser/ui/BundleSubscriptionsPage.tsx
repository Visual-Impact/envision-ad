"use client";

import {
    Alert,
    Badge,
    Button,
    Card,
    Group,
    Loader,
    Modal,
    Stack,
    Text,
    Title,
} from "@mantine/core";
import { notifications } from "@mantine/notifications";
import { IconAlertTriangle, IconInfoCircle } from "@tabler/icons-react";
import { useLocale, useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import type { BundleSubscription, BundleSubscriptionStatus } from "@/entities/bundle-subscription";
import { cancelBundleSubscription, getBundleSubscriptions } from "@/features/bundle-subscription";
import { useOrganization } from "@/app/providers";

/** Only a live subscription can be cancelled at Stripe; the rest are already closed out. */
const CANCELLABLE: BundleSubscriptionStatus[] = ["ACTIVE", "PAST_DUE", "INCOMPLETE"];

const STATUS_COLOR: Record<BundleSubscriptionStatus, string> = {
    ACTIVE: "green",
    PAST_DUE: "red",
    CANCELED: "gray",
    INCOMPLETE: "yellow",
};

/**
 * The advertiser's own view of what they are paying for (P1 M6).
 *
 * Until now a subscription was invisible once created: no renewal date, no past-due signal, and
 * no way to cancel short of calling the API by hand. This page closes those gaps from
 * P1-UI-VERIFICATION's list.
 */
export default function BundleSubscriptionsPage() {
    const t = useTranslations("bundleSubscriptions");
    const locale = useLocale();
    const { organization } = useOrganization();

    const [subscriptions, setSubscriptions] = useState<BundleSubscription[]>([]);
    const [loading, setLoading] = useState(true);
    const [loadFailed, setLoadFailed] = useState(false);
    const [pendingCancel, setPendingCancel] = useState<BundleSubscription | null>(null);
    const [cancelling, setCancelling] = useState(false);

    const businessId = organization?.businessId;

    /**
     * Reload after a cancel. The mount fetch below deliberately does NOT call this — invoking a
     * named setState-calling function straight from an effect body trips
     * `react-hooks/set-state-in-effect`, so the effect inlines its own copy with a cancellation
     * flag. The small duplication is the honest cost of that rule.
     */
    const reload = async () => {
        if (!businessId) return;
        setLoading(true);
        setLoadFailed(false);
        try {
            setSubscriptions(await getBundleSubscriptions(businessId));
        } catch (error) {
            console.error("Failed to load bundle subscriptions", error);
            setLoadFailed(true);
            setSubscriptions([]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        if (!businessId) return;

        let cancelled = false;

        void (async () => {
            setLoading(true);
            setLoadFailed(false);
            try {
                const rows = await getBundleSubscriptions(businessId);
                if (cancelled) return;
                setSubscriptions(rows);
            } catch (error) {
                if (cancelled) return;
                console.error("Failed to load bundle subscriptions", error);
                setLoadFailed(true);
                setSubscriptions([]);
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [businessId]);

    const handleCancel = async () => {
        if (!pendingCancel) return;
        setCancelling(true);
        try {
            await cancelBundleSubscription(pendingCancel.subscriptionId);
            notifications.show({ message: t("page.cancelSuccess"), color: "green" });
            setPendingCancel(null);
            await reload();
        } catch (error) {
            console.error("Failed to cancel bundle subscription", error);
            notifications.show({ message: t("page.cancelError"), color: "red" });
        } finally {
            setCancelling(false);
        }
    };

    const formatDate = (value: string) =>
        new Date(value).toLocaleDateString(locale === "fr" ? "fr-CA" : "en-CA", {
            year: "numeric",
            month: "long",
            day: "numeric",
        });

    /**
     * The renewal line carries four distinct meanings, and conflating them would misinform the
     * advertiser: a genuine future renewal, an end date because the subscription is cancelling,
     * no date at all on a row that's still going (legitimate on ACTIVE — only `invoice.paid` sets
     * one), and no date at all on a row that's already over — e.g. an INCOMPLETE checkout
     * cancelled before it ever billed. That last case must not read as "renewal date not set
     * yet", which implies one is still coming; cancellation status is checked first so a missing
     * date on an ended subscription doesn't fall through to the pending-renewal copy.
     */
    const renewalLine = (subscription: BundleSubscription) => {
        const isEnding = subscription.cancelAtPeriodEnd || subscription.status === "CANCELED";
        if (!subscription.currentPeriodEnd) {
            return isEnding ? t("page.subscriptionEnded") : t("page.renewalUnknown");
        }
        const date = formatDate(subscription.currentPeriodEnd);
        return isEnding ? t("page.endsOn", { date }) : t("page.renewsOn", { date });
    };

    const bundleName = (subscription: BundleSubscription) => {
        const name = locale === "fr" ? subscription.bundleNameFr : subscription.bundleNameEn;
        return name ?? t("page.bundleUnavailable");
    };

    if (loading) {
        return (
            <Stack align="center" p="xl">
                <Loader />
            </Stack>
        );
    }

    return (
        <Stack gap="md" p="md">
            <Stack gap={4}>
                <Title order={2}>{t("page.title")}</Title>
                <Text size="sm" c="dimmed">{t("page.subtitle")}</Text>
            </Stack>

            {loadFailed && (
                <Alert color="red" icon={<IconAlertTriangle size="1rem" />}>
                    {t("page.loadError")}
                </Alert>
            )}

            {!loadFailed && subscriptions.length === 0 && (
                <Card withBorder radius="lg" p="xl">
                    <Stack align="center" gap="sm">
                        <Text c="dimmed">{t("page.empty")}</Text>
                        <Button component="a" href={`/${locale}#bundles`} variant="gradient">
                            {t("page.emptyCta")}
                        </Button>
                    </Stack>
                </Card>
            )}

            {subscriptions.map((subscription) => {
                const statusHint =
                    subscription.status === "PAST_DUE" || subscription.status === "INCOMPLETE"
                        ? t(`statusHint.${subscription.status}`)
                        : null;

                return (
                    <Card key={subscription.subscriptionId} withBorder radius="lg" p="lg">
                        <Stack gap="sm">
                            <Group justify="space-between" align="flex-start" wrap="wrap">
                                <Stack gap={4}>
                                    <Group gap="xs">
                                        <Text fw={600} size="lg">{bundleName(subscription)}</Text>
                                        <Badge color={STATUS_COLOR[subscription.status]} variant="light">
                                            {t(`status.${subscription.status}`)}
                                        </Badge>
                                        {/*
                                          A cancelled-but-not-yet-ended subscription is still
                                          ACTIVE. Without this the advertiser cannot tell that
                                          they already cancelled.
                                        */}
                                        {subscription.cancelAtPeriodEnd && subscription.status !== "CANCELED" && (
                                            <Badge color="orange" variant="light">
                                                {t("status.cancelAtPeriodEnd")}
                                            </Badge>
                                        )}
                                    </Group>
                                    <Text size="sm" c="dimmed">
                                        {t("page.screens", { count: subscription.screenCount })}
                                        {" · "}
                                        {subscription.campaignName
                                            ? t("page.campaign", { name: subscription.campaignName })
                                            : t("page.campaignUnknown")}
                                    </Text>
                                    <Text size="sm" c="dimmed">{renewalLine(subscription)}</Text>
                                </Stack>

                                <Stack gap="xs" align="flex-end">
                                    <Text fw={700} size="xl">
                                        {new Intl.NumberFormat(locale === "fr" ? "fr-CA" : "en-CA", {
                                            style: "currency",
                                            currency: "CAD",
                                        }).format(subscription.monthlyAmount)}
                                        <Text span size="sm" c="dimmed">{t("page.perMonth")}</Text>
                                    </Text>
                                    {CANCELLABLE.includes(subscription.status) && !subscription.cancelAtPeriodEnd && (
                                        <Button
                                            variant="subtle"
                                            color="red"
                                            size="compact-sm"
                                            onClick={() => setPendingCancel(subscription)}
                                        >
                                            {t("page.cancel")}
                                        </Button>
                                    )}
                                </Stack>
                            </Group>

                            {statusHint && (
                                <Alert color={STATUS_COLOR[subscription.status]} icon={<IconInfoCircle size="1rem" />}>
                                    {statusHint}
                                </Alert>
                            )}
                        </Stack>
                    </Card>
                );
            })}

            <Modal
                opened={!!pendingCancel}
                onClose={() => setPendingCancel(null)}
                title={t("page.confirmTitle")}
                size="sm"
                centered
                radius="lg"
                overlayProps={{ backgroundOpacity: 0.55 }}
            >
                <Stack gap="md">
                    <Text size="sm">{t("page.confirmBody")}</Text>
                    <Group justify="flex-end">
                        <Button variant="default" onClick={() => setPendingCancel(null)} disabled={cancelling}>
                            {t("page.confirmDismiss")}
                        </Button>
                        <Button color="red" onClick={handleCancel} loading={cancelling}>
                            {cancelling ? t("page.cancelling") : t("page.confirmAction")}
                        </Button>
                    </Group>
                </Stack>
            </Modal>
        </Stack>
    );
}
