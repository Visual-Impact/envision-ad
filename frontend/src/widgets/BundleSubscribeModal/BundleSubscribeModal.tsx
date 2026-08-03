"use client";

import { useEffect, useState } from "react";
import {
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
    ThemeIcon,
    Title,
} from "@mantine/core";
import { IconCheck, IconInfoCircle } from "@tabler/icons-react";
import { notifications } from "@mantine/notifications";
import { useLocale, useTranslations } from "next-intl";
import { EmbeddedCheckout, EmbeddedCheckoutProvider } from "@stripe/react-stripe-js";
import { loadStripe } from "@stripe/stripe-js";

import { Bundle, BundlePriceQuote } from "@/entities/bundle";
import { AdCampaign } from "@/entities/ad-campaign";
import { getBundleQuote } from "@/features/bundle-management/api";
import { getAllAdCampaigns } from "@/features/ad-campaign-management/api";
import { createBundleSubscription } from "@/features/bundle-subscription/api";
import { Link } from "@/shared/lib/i18n/navigation";
import { formatCurrency } from "@/shared/lib/formatCurrency";

const stripePromise = process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY
    ? loadStripe(process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY)
    : null;

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
    const [loadState, setLoadState] = useState<"loading" | "error" | "ready">("loading");
    const [submitting, setSubmitting] = useState(false);

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
            try {
                const [quoteData, campaignData] = await Promise.all([
                    getBundleQuote(bundleId, businessId),
                    getAllAdCampaigns(businessId),
                ]);
                if (cancelled) return;

                // A campaign with no ads has nothing to display, so the backend rejects it;
                // filtering here keeps the picker from offering a guaranteed failure.
                const usable = campaignData.filter((c) => (c.ads?.length ?? 0) > 0);
                setQuote(quoteData);
                setCampaigns(usable);
                setCampaignId(usable.length === 1 ? usable[0].campaignId : null);
                setLoadState("ready");
            } catch {
                if (!cancelled) setLoadState("error");
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [opened, bundleId, businessId]);

    const handleSubscribe = async () => {
        if (!bundleId || !businessId || !campaignId) return;

        setSubmitting(true);
        try {
            const data = await createBundleSubscription({ bundleId, campaignId, businessId });
            setClientSecret(data.clientSecret);
            setStep("payment");
        } catch {
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
            overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}
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
                                            {formatCurrency(quote?.finalPrice ?? 0, { locale })}
                                            <Text component="span" size="sm" c="dimmed" fw={500}>
                                                {" "}
                                                {t("perMonth")}
                                            </Text>
                                        </Text>
                                    </Group>
                                </Paper>

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
                    ) : stripePromise ? (
                        <EmbeddedCheckoutProvider
                            stripe={stripePromise}
                            options={{ clientSecret, onComplete: () => setStep("success") }}
                        >
                            <EmbeddedCheckout />
                        </EmbeddedCheckoutProvider>
                    ) : (
                        <Center>
                            <Loader />
                        </Center>
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
