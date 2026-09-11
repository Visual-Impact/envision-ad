"use client";

import { useEffect, useState } from "react";
import {
    ActionIcon,
    Alert,
    Button,
    Center,
    Divider,
    Group,
    Loader,
    Modal,
    Paper,
    Select,
    Stack,
    Text,
    TextInput,
    ThemeIcon,
    Title,
} from "@mantine/core";
import { IconCheck, IconInfoCircle, IconX } from "@tabler/icons-react";
import { notifications } from "@mantine/notifications";
import { useLocale, useTranslations } from "next-intl";
import { EmbeddedCheckout, EmbeddedCheckoutProvider } from "@stripe/react-stripe-js";
import { loadStripe } from "@stripe/stripe-js";
import axios from "axios";

import { Bundle, BundlePriceQuote } from "@/entities/bundle";
import { AdCampaign } from "@/entities/ad-campaign";
import { CouponValidateError } from "@/entities/coupon";
import { getBundleQuote } from "@/features/bundle-management";
import { getAllAdCampaigns } from "@/features/ad-campaign-management";
import { createBundleSubscription } from "@/features/bundle-subscription";
import { validateCoupon } from "@/features/payment";
import { Link } from "@/shared/lib/i18n";
import { formatCurrency } from "@/shared/lib/formatCurrency";

// Lazy singleton: loadStripe() must not run at module scope, since this component is
// imported by the home page's bundles section that every visitor renders. Calling it
// eagerly spins up Stripe's fraud-detection script/iframes for everyone, not just the
// people who reach checkout.
let stripePromise: ReturnType<typeof loadStripe> | null = null;
function getStripe() {
    if (!stripePromise && process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY) {
        stripePromise = loadStripe(process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY);
    }
    return stripePromise;
}

interface BundleSubscribeModalProps {
    opened: boolean;
    onClose: () => void;
    bundle: Bundle | null;
    businessId: string | null;
}

/**
 * Subscribe flow for a bundle: review (buyer-specific quote + campaign pick) → embedded
 * Stripe checkout → confirmation. The EmbeddedCheckout wiring was modeled on the reservation
 * PaymentModal, which was removed with the rest of that flow in M6.
 *
 * <p>Lives in `widgets` rather than under `pages/dashboard/advertiser` (where the brief
 * originally placed it): its caller is now the home page's bundles section, so a
 * pages→pages import would break FSD, and it composes three different feature slices —
 * which only the widgets layer is allowed to do.
 */
export function BundleSubscribeModal({ opened, onClose, bundle, businessId }: BundleSubscribeModalProps) {
    const t = useTranslations("bundles.subscribe");
    const locale = useLocale();

    const [step, setStep] = useState<"review" | "payment" | "success">("review");
    const [clientSecret, setClientSecret] = useState<string | null>(null);
    const [quote, setQuote] = useState<BundlePriceQuote | null>(null);
    const [campaigns, setCampaigns] = useState<AdCampaign[]>([]);
    const [campaignId, setCampaignId] = useState<string | null>(null);
    const [loadState, setLoadState] = useState<"loading" | "error" | "notAdvertiser" | "ready">("loading");
    const [submitting, setSubmitting] = useState(false);

    // Coupon (P3). `appliedCouponCode` is the normalized code actually sent at
    // subscribe time — separate from `couponInput`, the raw text field — so editing
    // the input after a successful Apply doesn't silently change what gets submitted.
    const [couponInput, setCouponInput] = useState("");
    const [couponState, setCouponState] = useState<"idle" | "loading" | "applied" | "error">("idle");
    const [couponError, setCouponError] = useState<CouponValidateError | null>(null);
    const [appliedCouponCode, setAppliedCouponCode] = useState<string | null>(null);
    const [discountAmountCents, setDiscountAmountCents] = useState<number | null>(null);
    const [previewTotalCents, setPreviewTotalCents] = useState<number | null>(null);

    const missingKey = !process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY;
    const name = locale === "fr" ? bundle?.nameFr : bundle?.nameEn;

    // Reset as the modal closes. Adjusting state during render for a prop change is the
    // pattern React recommends over an effect.
    const [prevOpened, setPrevOpened] = useState(opened);
    if (opened !== prevOpened) {
        setPrevOpened(opened);
        if (!opened) {
            setStep("review");
            setClientSecret(null);
            setQuote(null);
            setCampaigns([]);
            setCampaignId(null);
            setLoadState("loading");
            setSubmitting(false);
            setCouponInput("");
            setCouponState("idle");
            setCouponError(null);
            setAppliedCouponCode(null);
            setDiscountAmountCents(null);
            setPreviewTotalCents(null);
        }
    }

    const bundleId = bundle?.bundleId;

    // The quote is fetched fresh on open rather than reusing the card's public price:
    // this one is buyer-specific, which is what P4/P8 will later make differ.
    useEffect(() => {
        if (!opened || !bundleId || !businessId) return;
        let cancelled = false;

        (async () => {
            setLoadState("loading");
            // allSettled rather than Promise.all: a media-owner-only business gets a
            // structured NOT_ADVERTISER rejection from one or both calls, and that needs
            // to win over a generic failure regardless of which promise rejects first.
            const [quoteResult, campaignResult] = await Promise.allSettled([
                getBundleQuote(bundleId, businessId),
                getAllAdCampaigns(businessId),
            ]);
            if (cancelled) return;

            const isNotAdvertiser = (result: PromiseSettledResult<unknown>) =>
                result.status === "rejected" &&
                axios.isAxiosError(result.reason) &&
                result.reason.response?.data?.code === "NOT_ADVERTISER";

            if (isNotAdvertiser(quoteResult) || isNotAdvertiser(campaignResult)) {
                setLoadState("notAdvertiser");
                return;
            }

            if (quoteResult.status === "rejected" || campaignResult.status === "rejected") {
                setLoadState("error");
                return;
            }

            // A campaign with no ads has nothing to display, so the backend rejects it;
            // filtering here keeps the picker from offering a guaranteed failure.
            const usable = campaignResult.value.filter((c) => (c.ads?.length ?? 0) > 0);
            setQuote(quoteResult.value);
            setCampaigns(usable);
            setCampaignId(usable.length === 1 ? usable[0].campaignId : null);
            setLoadState("ready");
        })();

        return () => {
            cancelled = true;
        };
    }, [opened, bundleId, businessId]);

    const isCouponError = (value: unknown): value is CouponValidateError =>
        value === "invalid" || value === "expired" || value === "exhausted";

    const handleApplyCoupon = async () => {
        const code = couponInput.trim();
        if (!code || !quote) return;

        setCouponState("loading");
        setCouponError(null);
        try {
            const subtotalCents = Math.round(quote.finalPrice * 100);
            const result = await validateCoupon(code, subtotalCents);
            if (result.valid) {
                setAppliedCouponCode(code.toUpperCase());
                setDiscountAmountCents(result.discountAmountCents ?? 0);
                setPreviewTotalCents(result.previewTotalCents ?? subtotalCents);
                setCouponState("applied");
            } else {
                setCouponError(result.error ?? "invalid");
                setCouponState("error");
            }
        } catch {
            setCouponError("invalid");
            setCouponState("error");
        }
    };

    const handleRemoveCoupon = () => {
        setCouponInput("");
        setAppliedCouponCode(null);
        setDiscountAmountCents(null);
        setPreviewTotalCents(null);
        setCouponState("idle");
        setCouponError(null);
    };

    const handleSubscribe = async () => {
        if (!bundleId || !businessId || !campaignId) return;

        setSubmitting(true);
        try {
            const data = await createBundleSubscription({
                bundleId,
                campaignId,
                businessId,
                ...(appliedCouponCode && { couponCode: appliedCouponCode }),
            });
            setClientSecret(data.clientSecret);
            setStep("payment");
        } catch (err) {
            // A coupon that passed the /validate preview can still be rejected by
            // Stripe at this exact instant (brief §4.6.5) — clear it and re-show the
            // same error vocabulary the preview uses, rather than the generic message
            // below, and without resetting the bundle/campaign selection.
            const code = axios.isAxiosError(err) ? err.response?.data?.code : undefined;
            if (appliedCouponCode && isCouponError(code)) {
                setAppliedCouponCode(null);
                setDiscountAmountCents(null);
                setPreviewTotalCents(null);
                setCouponError(code);
                setCouponState("error");
                return;
            }

            // Backend guard messages are English-only and not meant for display — the
            // frontend owns user-facing copy so it stays correct and translated regardless
            // of what the server says. Re-quoting on open (the effect above) means most of
            // these guards can't actually fire here; the ones that still can (eligibility
            // changing between quote and submit, a stale duplicate) are rare enough that a
            // generic retry message is the right tradeoff over parsing server text.
            notifications.show({
                title: t("errorTitle"),
                message: t("initFailed"),
                color: "red",
            });
        } finally {
            setSubmitting(false);
        }
    };

    const handleClose = () => {
        onClose();
    };

    if (!bundle) return null;

    const hasScreens = (quote?.screenCount ?? 0) > 0;
    const canSubscribe = loadState === "ready" && hasScreens && !!campaignId;

    return (
        <Modal
            opened={opened}
            onClose={handleClose}
            size="lg"
            title={<Text fw={700}>{t("title", { bundle: name ?? "" })}</Text>}
            centered
            padding="xl"
            closeOnClickOutside={step !== "payment"}
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55 }}
        >
            <Stack gap="xl" p="md">
                {step === "review" && (
                    <>
                        {loadState === "loading" && (
                            <Center py="xl">
                                <Loader />
                            </Center>
                        )}

                        {loadState === "error" && (
                            <Alert color="red" icon={<IconInfoCircle size={18} />}>
                                {t("loadFailed")}
                            </Alert>
                        )}

                        {loadState === "notAdvertiser" && (
                            <Alert color="yellow" icon={<IconInfoCircle size={18} />}>
                                {t("notAdvertiser")}
                            </Alert>
                        )}

                        {loadState === "ready" && (
                            <>
                                <Paper shadow="sm" radius="lg" p="lg" w="100%">
                                    <Group justify="space-between">
                                        <Text c="dimmed">{t("bundle")}</Text>
                                        <Text fw={500}>{name}</Text>
                                    </Group>
                                    <Group justify="space-between" mt="xs">
                                        <Text c="dimmed">{t("screens")}</Text>
                                        <Text fw={500}>{quote?.screenCount ?? 0}</Text>
                                    </Group>
                                    <Divider my="sm" />
                                    <Group justify="space-between">
                                        <Text size="lg" fw={700}>
                                            {t("monthlyTotal")}
                                        </Text>
                                        <Text size="lg" fw={700} c="blue">
                                            {formatCurrency(
                                                couponState === "applied" && previewTotalCents !== null
                                                    ? previewTotalCents / 100
                                                    : quote?.finalPrice ?? 0,
                                                { locale },
                                            )}
                                            <Text component="span" size="sm" c="dimmed" fw={500}>
                                                {" "}
                                                {t("perMonth")}
                                            </Text>
                                        </Text>
                                    </Group>
                                </Paper>

                                <Stack gap={4}>
                                    {couponState === "applied" && appliedCouponCode ? (
                                        <Alert color="green" icon={<IconCheck size={18} />} p="sm">
                                            <Group justify="space-between" wrap="nowrap">
                                                <Text size="sm">
                                                    {t("coupon.applied", {
                                                        code: appliedCouponCode,
                                                        amount: formatCurrency((discountAmountCents ?? 0) / 100, { locale }),
                                                    })}
                                                </Text>
                                                <ActionIcon
                                                    variant="subtle"
                                                    color="green"
                                                    size="sm"
                                                    onClick={handleRemoveCoupon}
                                                    aria-label={t("coupon.remove")}
                                                >
                                                    <IconX size={14} />
                                                </ActionIcon>
                                            </Group>
                                        </Alert>
                                    ) : (
                                        <Group gap="xs" align="flex-end" wrap="nowrap">
                                            <TextInput
                                                label={t("coupon.label")}
                                                placeholder={t("coupon.placeholder")}
                                                value={couponInput}
                                                onChange={(e) => setCouponInput(e.currentTarget.value.toUpperCase())}
                                                error={couponState === "error" ? t(`coupon.error.${couponError}`) : undefined}
                                                style={{ flex: 1 }}
                                            />
                                            <Button
                                                variant="light"
                                                onClick={handleApplyCoupon}
                                                loading={couponState === "loading"}
                                                disabled={!couponInput.trim()}
                                            >
                                                {t("coupon.apply")}
                                            </Button>
                                        </Group>
                                    )}
                                </Stack>

                                {!hasScreens && (
                                    <Alert color="yellow" icon={<IconInfoCircle size={18} />}>
                                        {t("noScreens")}
                                    </Alert>
                                )}

                                {campaigns.length === 0 ? (
                                    <Alert color="blue" icon={<IconInfoCircle size={18} />}>
                                        <Stack gap="xs" align="flex-start">
                                            <Text size="sm">{t("noCampaigns")}</Text>
                                            <Button
                                                component={Link}
                                                href="/dashboard/advertiser/campaigns"
                                                size="xs"
                                                variant="light"
                                            >
                                                {t("createCampaign")}
                                            </Button>
                                        </Stack>
                                    </Alert>
                                ) : (
                                    <Select
                                        label={t("campaignLabel")}
                                        description={t("campaignHelp")}
                                        placeholder={t("campaignPlaceholder")}
                                        data={campaigns.map((c) => ({ value: c.campaignId, label: c.name }))}
                                        value={campaignId}
                                        onChange={setCampaignId}
                                        allowDeselect={false}
                                    />
                                )}

                                <Text size="xs" c="dimmed" ta="center">
                                    {t("recurringNote")}
                                </Text>

                                <Group justify="center">
                                    <Button variant="default" onClick={handleClose}>
                                        {t("cancel")}
                                    </Button>
                                    <Button
                                        variant="gradient"
                                        onClick={handleSubscribe}
                                        loading={submitting}
                                        disabled={!canSubscribe}
                                    >
                                        {t("proceed")}
                                    </Button>
                                </Group>
                            </>
                        )}
                    </>
                )}

                {step === "payment" &&
                    clientSecret &&
                    (missingKey ? (
                        <Text c="red">{t("missingKey")}</Text>
                    ) : (
                        <EmbeddedCheckoutProvider
                            stripe={getStripe()}
                            options={{ clientSecret, onComplete: () => setStep("success") }}
                        >
                            <EmbeddedCheckout />
                        </EmbeddedCheckoutProvider>
                    ))}

                {step === "success" && (
                    <Center py="xl">
                        <Stack align="center" gap="sm">
                            <ThemeIcon color="green" size={80} radius="100%">
                                <IconCheck size={50} />
                            </ThemeIcon>
                            <Title order={3}>{t("successTitle")}</Title>
                            {/* Deliberately "being confirmed", not "active": the subscription
                                is only flipped to ACTIVE once Stripe's webhook lands. */}
                            <Text c="dimmed" ta="center" maw={400}>
                                {t("successMessage")}
                            </Text>
                            <Button mt="md" color="green" onClick={handleClose}>
                                {t("done")}
                            </Button>
                        </Stack>
                    </Center>
                )}
            </Stack>
        </Modal>
    );
}

export default BundleSubscribeModal;
