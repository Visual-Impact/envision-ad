"use client";

import React, { useState } from "react";
import {
    Modal,
    Stack,
    Text,
    Button,
    Group,
    Paper,
    Divider,
    Center,
    Loader,
    ThemeIcon,
    Title,
} from "@mantine/core";
import { IconCheck } from "@tabler/icons-react";
import { notifications } from "@mantine/notifications";
import { useTranslations, useLocale } from "next-intl";
import { EmbeddedCheckout, EmbeddedCheckoutProvider } from "@stripe/react-stripe-js";
import { loadStripe } from "@stripe/stripe-js";

import { ReservationResponseDTO } from "@/entities/reservation";
import { createPaymentIntent } from "@/features/payment";
import { formatCurrency } from "@/shared/lib/formatCurrency";

const stripePromise = process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY
    ? loadStripe(process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY)
    : null;

interface PaymentModalProps {
    opened: boolean;
    onClose: () => void;
    reservation: ReservationResponseDTO | null;
    onSuccess: () => void;
}

const formatDate = (isoDate: string, locale: string): string => {
    return new Date(isoDate).toLocaleDateString(locale, {
        year: "numeric",
        month: "long",
        day: "numeric",
    });
};

export function PaymentModal({
                                 opened,
                                 onClose,
                                 reservation,
                                 onSuccess,
                             }: PaymentModalProps) {
    const t = useTranslations("advertiserReservations");
    const locale = useLocale();

    const [step, setStep] = useState<"review" | "payment" | "success">("review");
    const [clientSecret, setClientSecret] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    const missingKey = !process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY;

    // Reset once the modal transitions to closed. Adjusting state during
    // render (rather than in an effect) for a prop change is the pattern
    // React recommends — see
    // https://react.dev/reference/react/useState#storing-information-from-previous-renders
    const [prevOpened, setPrevOpened] = useState(opened);
    if (opened !== prevOpened) {
        setPrevOpened(opened);
        if (!opened) {
            setStep("review");
            setClientSecret(null);
            setLoading(false);
        }
    }

    const handleProceedToPayment = async () => {
        if (!reservation) return;

        setLoading(true);
        try {
            const paymentIntentPayload = {
                mediaId: reservation.mediaId,
                campaignId: reservation.campaignId,
                startDate: reservation.startDate,
                endDate: reservation.endDate,
                reservationId: reservation.reservationId,
            };

            const data = await createPaymentIntent(paymentIntentPayload);

            if (data.clientSecret) {
                setClientSecret(data.clientSecret);
                setStep("payment");
            } else {
                notifications.show({
                    title: t("payment.errorTitle"),
                    message: t("payment.initFailed"),
                    color: "red",
                });
            }
        } catch (error) {
            console.error("Payment init error:", error);
            notifications.show({
                title: t("payment.errorTitle"),
                message: t("payment.initFailed"),
                color: "red",
            });
        } finally {
            setLoading(false);
        }
    };

    const handleModalClose = () => {
        // Only reset if payment wasn't successful
        if (step !== "success") {
            setStep("review");
            setClientSecret(null);
            setLoading(false);
        }
        onClose();
    };

    const handlePaymentComplete = () => {
        setStep("success");
    };

    const handleSuccessClose = () => {
        setStep("review");
        setClientSecret(null);
        onClose();
        onSuccess();
    };

    if (!reservation) return null;

    return (
        <Modal
            opened={opened}
            onClose={step === "success" ? handleSuccessClose : handleModalClose}
            size="lg"
            title={<Text fw={700}>{t("payment.title")}</Text>}
            centered
            padding="xl"
            closeOnClickOutside={step !== "payment"}
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}
        >
            <Stack gap="xl" p="md">
                {step === "review" && (
                    <>
                        <Stack align="center" gap="md">
                            <Text size="xl" fw={600}>{t("payment.reviewTitle")}</Text>
                            <Paper shadow="sm" radius="lg" p="lg" w="100%">
                                <Group justify="space-between">
                                    <Text c="dimmed">{t("payment.campaign")}:</Text>
                                    <Text fw={500}>{reservation.campaignName || "Unknown"}</Text>
                                </Group>
                                <Group justify="space-between" mt="xs">
                                    <Text c="dimmed">{t("payment.dates")}:</Text>
                                    <Text fw={500}>
                                        {formatDate(reservation.startDate, locale)} -{" "}
                                        {formatDate(reservation.endDate, locale)}
                                    </Text>
                                </Group>
                                <Divider my="sm" />
                                <Group justify="space-between">
                                    <Text size="lg" fw={700}>{t("payment.totalCost")}:</Text>
                                    <Group gap="xs">
                                    <Text size="lg" fw={700} c="blue">
                                            {formatCurrency(reservation.totalPrice, { locale })}
                                    </Text>
                                    </Group>
                                </Group>
                            </Paper>
                        </Stack>

                        <Group justify="center">
                            <Button variant="default" onClick={handleModalClose}>
                                {t("payment.cancel")}
                            </Button>
                            <Button variant="gradient" onClick={handleProceedToPayment} loading={loading}>
                                {t("payment.proceedToPay")}
                            </Button>
                        </Group>
                    </>
                )}

                {step === "payment" && clientSecret && (
                    missingKey ? (
                        <Text c="red">{t("payment.missingKey")}</Text>
                    ) : stripePromise ? (
                        <EmbeddedCheckoutProvider
                            stripe={stripePromise}
                            options={{
                                clientSecret,
                                onComplete: handlePaymentComplete,
                            }}
                        >
                            <EmbeddedCheckout />
                        </EmbeddedCheckoutProvider>
                    ) : (
                        <Center>
                            <Loader />
                        </Center>
                    )
                )}

                {step === "success" && (
                    <Center py="xl">
                        <Stack align="center" gap="sm">
                            <ThemeIcon color="green" size={80} radius="100%">
                                <IconCheck size={50} />
                            </ThemeIcon>
                            <Title order={3}>{t("payment.successTitle")}</Title>
                            <Text c="dimmed" ta="center" maw={400}>
                                {t("payment.successMessage")}
                            </Text>
                            <Button mt="md" color="green" onClick={handleSuccessClose}>
                                {t("payment.done")}
                            </Button>
                        </Stack>
                    </Center>
                )}
            </Stack>
        </Modal>
    );
}
