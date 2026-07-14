"use client";

import React, { useEffect, useState } from "react";
import {
    Container,
    Stack,
    Group,
    Text,
    Title,
    Card,
    Divider,
    Loader,
    Center,
    Button,
    Image,
} from "@mantine/core";
import {
    IconCalendar,
    IconCurrencyDollar,
    IconChevronLeft,
    IconChevronRight,
    IconDownload,
} from "@tabler/icons-react";
import { useParams } from "next/navigation";
import { useTranslations, useLocale } from "next-intl";
import { notifications } from "@mantine/notifications";

import { useRouter } from "next/navigation";
import { BackButton } from "@/widgets/BackButton";
import { ReservationResponseDTO, ReservationStatus, DenialReason } from "@/entities/reservation";
import { AdCampaign } from "@/entities/ad-campaign";
import { Ad } from "@/entities/ad";
import { Media } from "@/entities/media";
import { getReservationById, approveReservation, denyReservation } from "@/features/reservation-management/api";
import { getAdCampaignById } from "@/features/ad-campaign-management/api";
import { getMediaById } from "@/features/media-management/api";
import {ConfirmationModal} from "@/shared/ui";
import {DenyReservationModal} from "@/pages/dashboard/media-owner/ui/modals/DenyReservationModal";

const formatDate = (isoDate: string, locale: string): string => {
    return new Date(isoDate).toLocaleDateString(locale, {
        year: "numeric",
        month: "long",
        day: "numeric",
    });
};

const formatDuration = (start: string, end: string, t: ReturnType<typeof useTranslations>): string => {
    const diffMs = new Date(end).getTime() - new Date(start).getTime();
    const days = Math.max(1, Math.round(diffMs / (1000 * 60 * 60 * 24)));
    const weeks = Math.round(days / 7);

    return weeks === 1 ? t("detail.oneWeek") : t("detail.weeks", { count: weeks });
};

function AdCarousel({ ads }: { ads: Ad[] }) {
    const [activeIndex, setActiveIndex] = useState(0);
    const current = ads[activeIndex];

    const goTo = (i: number) => setActiveIndex(i);
    const prev = () => setActiveIndex((i) => (i === 0 ? ads.length - 1 : i - 1));
    const next = () => setActiveIndex((i) => (i === ads.length - 1 ? 0 : i + 1));

    useEffect(() => {
        const adCarouselDuration = 5 * 1000; // 5 seconds per ad
        if (current.adType !== "IMAGE") return;

        const timer = setTimeout(() => {
            setActiveIndex((i) => (i === ads.length - 1 ? 0 : i + 1));
        }, adCarouselDuration);

        return () => clearTimeout(timer);
    }, [activeIndex, current, ads.length]);

    return (
        <Card withBorder radius="lg" p={0} style={{ overflow: "hidden" }}>
            <div style={{
                position: "relative",
                width: "100%",
                maxHeight: "500px",
                height: "500px",
                background: "#000",
                display: "flex",
                alignItems: "center",
                justifyContent: "center"
            }}>
                {current.adType === "IMAGE" ? (
                    <Image
                        key={current.adId}
                        src={current.adUrl}
                        alt={current.name}
                        style={{ maxWidth: "100%", maxHeight: "100%", objectFit: "contain" }}
                    />
                ) : (
                    <video
                        key={current.adId}
                        src={current.adUrl}
                        autoPlay
                        muted
                        playsInline
                        style={{ maxWidth: "100%", maxHeight: "100%", objectFit: "contain" }}
                    />
                )}

                {ads.length > 1 && (
                    <>
                        <button
                            onClick={prev}
                            aria-label="Previous ad"
                            style={{
                                position: "absolute",
                                left: 8,
                                top: "50%",
                                transform: "translateY(-50%)",
                                background: "rgba(0,0,0,0.45)",
                                border: "none",
                                borderRadius: "50%",
                                width: 32,
                                height: 32,
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                cursor: "pointer",
                                color: "#fff",
                            }}
                        >
                            <IconChevronLeft size={18} />
                        </button>
                        <button
                            onClick={next}
                            aria-label="Next ad"
                            style={{
                                position: "absolute",
                                right: 8,
                                top: "50%",
                                transform: "translateY(-50%)",
                                background: "rgba(0,0,0,0.45)",
                                border: "none",
                                borderRadius: "50%",
                                width: 32,
                                height: 32,
                                display: "flex",
                                alignItems: "center",
                                justifyContent: "center",
                                cursor: "pointer",
                                color: "#fff",
                            }}
                        >
                            <IconChevronRight size={18} />
                        </button>
                    </>
                )}
            </div>

            <div style={{ padding: "10px 16px 12px", display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}>
                <Text size="sm" fw={500}>{current.name}</Text>

                {ads.length > 1 && (
                    <Group gap={6} justify="center">
                        {ads.map((_, i) => (
                            <button
                                key={i}
                                onClick={() => goTo(i)}
                                aria-label={`Go to ad ${i + 1}`}
                                style={{
                                    width: i === activeIndex ? 20 : 8,
                                    height: 8,
                                    borderRadius: 4,
                                    border: "none",
                                    background: i === activeIndex ? "var(--mantine-color-blue-6)" : "var(--mantine-color-gray-3)",
                                    cursor: "pointer",
                                    transition: "width 0.25s ease, background 0.25s ease",
                                    padding: 0,
                                }}
                            />
                        ))}
                    </Group>
                )}
            </div>
        </Card>
    );
}

export default function AdRequestDetailPage() {
    const t = useTranslations("adRequests");
    const router = useRouter();
    const locale = useLocale();
    const params = useParams();
    const reservationId = params?.id as string | undefined;

    const [reservation, setReservation] = useState<ReservationResponseDTO | null>(null);
    const [campaign, setCampaign] = useState<AdCampaign | null>(null);
    const [media, setMedia] = useState<Media | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    const [showApproveModal, setShowApproveModal] = useState(false);
    const [showDenyModal, setShowDenyModal] = useState(false);
    const [submitting, setSubmitting] = useState(false);

    useEffect(() => {
        if (!reservationId) return;

        const load = async () => {
            try {
                setLoading(true);
                setError(null);
                const data = await getReservationById(reservationId);
                setReservation(data);
            } catch (err: unknown) {
                const msg = err instanceof Error ? err.message : "Failed to load ad request.";
                setError(msg);
            } finally {
                setLoading(false);
            }
        };

        void load();
    }, [reservationId]);

    useEffect(() => {
        if (!reservation?.campaignId) return;

        const loadCampaign = async () => {
            try {
                const data = await getAdCampaignById(reservation.campaignId);
                setCampaign(data);
            } catch (err: unknown) {
                console.error("Failed to load campaign details", err);
            }
        };

        void loadCampaign();
    }, [reservation?.campaignId]);

    useEffect(() => {
        if (!reservation?.mediaId) return;

        const loadMedia = async () => {
            try {
                const data = await getMediaById(reservation.mediaId);
                setMedia(data);
            } catch (err: unknown) {
                console.error("Failed to load media details", err);
            }
        };

        void loadMedia();
    }, [reservation?.mediaId]);

    const handleApprove = async () => {
        if (!reservation) return;

        try {
            setSubmitting(true);
            await approveReservation(
                reservation.mediaId,
                reservation.reservationId
            );

            setShowApproveModal(false);
            notifications.show({
                title: t("notifications.approve.success.title"),
                message: t("notifications.approve.success.message"),
                color: "green",
            });

            router.push(`/${locale}/dashboard/media-owner/advertisements`);
        } catch (e) {
            console.error(e);
            notifications.show({
                title: t("notifications.updateFailed.title"),
                message: t("notifications.updateFailed.message"),
                color: "red",
            });
        } finally {
            setSubmitting(false);
        }
    };

    const handleDeny = async (reason: DenialReason, description: string | null) => {
        if (!reservation) return;

        try {
            setSubmitting(true);

            await denyReservation(
                reservation.mediaId,
                reservation.reservationId,
                {
                    reason,
                    description
                }
            );

            setShowDenyModal(false);
            notifications.show({
                title: t("notifications.deny.success.title"),
                message: t("notifications.deny.success.message"),
                color: "green",
            });

            router.push(`/${locale}/dashboard/media-owner/advertisements`);
        } catch (e) {
            console.error(e);
            notifications.show({
                title: t("notifications.updateFailed.title"),
                message: t("notifications.updateFailed.message"),
                color: "red",
            });
        } finally {
            setSubmitting(false);
        }
    };

    const handleDownloadAll = async () => {
        if (!campaign || !campaign.ads.length) return;

        try {
            for (const ad of campaign.ads) {
                const response = await fetch(ad.adUrl);
                const blob = await response.blob();
                const url = window.URL.createObjectURL(blob);
                const a = document.createElement('a');
                a.href = url;
                const extension = ad.adType === "IMAGE" ? "jpg" : "mp4";
                a.download = `${ad.name}.${extension}`;
                document.body.appendChild(a);
                a.click();
                window.URL.revokeObjectURL(url);
                document.body.removeChild(a);

                // Add delay between downloads to avoid browser blocking
                await new Promise(resolve => setTimeout(resolve, 500));
            }

            notifications.show({
                title: t("notifications.download.success.title"),
                message: t("notifications.download.success.message"),
                color: "green",
            });
        } catch (error) {
            console.error("Download failed:", error);
            notifications.show({
                title: t("notifications.download.error.title"),
                message: t("notifications.download.error.message"),
                color: "red",
            });
        }
    };

    if (loading) {
        return (
            <Container size="lg" py="xl">
                <Center>
                    <Loader />
                </Center>
            </Container>
        );
    }

    if (error || !reservation) {
        return (
            <Container size="lg" py="xl">
                <Stack align="center" gap="sm">
                    <Text fw={600}>{t("detail.errorTitle")}</Text>
                    <Text size="sm" c="dimmed">
                        {error ?? t("detail.errorNotFound")}
                    </Text>
                    <BackButton />
                </Stack>
            </Container>
        );
    }

    const isPending = reservation.status === ReservationStatus.PENDING;
    const duration = formatDuration(reservation.startDate, reservation.endDate, t);

    return (
        <>
            <Container size="lg" py="xl">
                <Group align="center" justify="space-between" wrap="wrap">

                    <Stack gap="md" style={{ flex: 2, minWidth: 320 }}>
                        <Group gap="xs">
                            <BackButton />
                            <Title order={2}>
                                {campaign?.name || reservation.campaignName || "Ad Request"}
                            </Title>
                        </Group>

                        {campaign && campaign.ads.length > 0 && (
                            <AdCarousel ads={campaign.ads} />
                        )}
                    </Stack>

                    <Stack gap="md" style={{ flex: 1, minWidth: 280 }}>
                        {!isPending && campaign && campaign.ads.length > 0 && (
                            <Card shadow="sm" radius="lg" p="lg">
                                <Stack gap="sm" align="center">
                                    <Text size="sm" fw={500}>{t("detail.mediaFiles")}</Text>
                                    <Button
                                        radius="xl"
                                        fullWidth
                                        leftSection={<IconDownload size={16} />}
                                        variant="light"
                                        onClick={handleDownloadAll}
                                    >
                                        {t("detail.downloadAllMedia")}
                                    </Button>
                                </Stack>
                            </Card>
                        )}

                        <Card shadow="sm" radius="lg" p="lg">
                            <Stack gap="sm">
                                <Group justify="space-between" align="center">
                                    <Text size="sm" c="dimmed">{t("detail.name")}</Text>
                                    <Text size="sm" fw={500}>{media?.title}</Text>
                                </Group>

                                <Divider />

                                <Group justify="space-between" align="center">
                                    <Group gap="sm" align="center">
                                        <IconCalendar size={16} color="var(--mantine-color-dimmed)" />
                                        <Text size="sm" c="dimmed">{t("detail.startDate")}</Text>
                                    </Group>
                                    <Text size="sm">{formatDate(reservation.startDate, locale)}</Text>
                                </Group>

                                <Group justify="space-between" align="center">
                                    <Group gap="sm" align="center">
                                        <IconCalendar size={16} color="var(--mantine-color-dimmed)" />
                                        <Text size="sm" c="dimmed">{t("detail.endDate")}</Text>
                                    </Group>
                                    <Text size="sm">{formatDate(reservation.endDate, locale)}</Text>
                                </Group>

                                <Group justify="space-between" align="center">
                                    <Group gap="sm" align="center">
                                        <IconCalendar size={16} color="var(--mantine-color-dimmed)" />
                                        <Text size="sm" c="dimmed">{t("detail.duration")}</Text>
                                    </Group>
                                    <Text size="sm">{duration}</Text>
                                </Group>
                            </Stack>
                        </Card>

                        <Card shadow="md" radius="lg" p="lg">
                            <Stack align="center" gap="md">
                                <Group gap="xs" align="center">
                                    <IconCurrencyDollar size={20} />
                                    <Text fw={600} size="xl">
                                        {reservation.totalPrice.toLocaleString(locale)}
                                    </Text>
                                </Group>
                                <Text size="xs" c="dimmed">{t("detail.totalPrice")}</Text>
                                {isPending && (
                                    <>
                                        <Divider w="100%" />

                                        <Button
                                            radius="xl"
                                            fullWidth
                                            variant="gradient"
                                            type="button"
                                            onClick={() => setShowApproveModal(true)}
                                            disabled={submitting}
                                        >
                                            {t("detail.approve")}
                                        </Button>

                                        <Button
                                            radius="xl"
                                            fullWidth
                                            color="red"
                                            variant="outline"
                                            type="button"
                                            onClick={() => setShowDenyModal(true)}
                                            disabled={submitting}
                                        >
                                            {t("detail.deny")}
                                        </Button>
                                    </>
                                )}
                            </Stack>
                        </Card>
                    </Stack>
                </Group>
            </Container>

            <ConfirmationModal
                opened={showApproveModal}
                title={t("detail.confirmApproveTitle")}
                message={t("detail.confirmApproveText")}
                confirmLabel={t("detail.confirmApprove")}
                cancelLabel={t("detail.cancel")}
                confirmColor="blue"
                onConfirm={handleApprove}
                onCancel={() => setShowApproveModal(false)}
            />

            <DenyReservationModal
                opened={showDenyModal}
                onDeny={handleDeny}
                onCancel={() => setShowDenyModal(false)}
            />
        </>
    );
}