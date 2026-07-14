'use client'

import React, { useEffect, useState } from "react";
import { Grid, Card, Text, Group, Stack, Button, Badge, Skeleton, Popover, Loader, ActionIcon, Tooltip } from "@mantine/core";
import { IconCalendar, IconCurrencyDollar, IconInfoCircle, IconLanguage } from "@tabler/icons-react";
import { useRouter } from "next/navigation";
import { useLocale, useTranslations } from "next-intl";
import translate from 'translate';

import { ReservationResponseDTO, ReservationStatus, DenialReason } from "@/entities/reservation";
import { getMediaById } from "@/features/media-management/api";
import { Media } from "@/entities/media";

translate.engine = 'google';

type ViewType = "advertiser" | "media-owner";

interface ReservationCardsProps {
    reservations: ReservationResponseDTO[];
    viewType: ViewType;
    onPayClick?: (reservation: ReservationResponseDTO) => void;
}

const formatDate = (isoDate: string, locale?: string): string => {
    return new Date(isoDate).toLocaleDateString(locale || undefined, {
        year: "numeric",
        month: locale ? "long" : "short",
        day: "numeric",
    });
};

const getStatusColor = (status: ReservationStatus): string => {
    switch (status) {
        case ReservationStatus.PENDING:
            return "yellow";
        case ReservationStatus.APPROVED:
            return "blue";
        case ReservationStatus.CONFIRMED:
            return "green";
        case ReservationStatus.DENIED:
            return "red";
        default:
            return "gray";
    }
};

const getStatusLabel = (status: ReservationStatus, t: (key: string) => string): string => {
    switch (status) {
        case ReservationStatus.PENDING:
            return t("status.pending");
        case ReservationStatus.APPROVED:
            return t("status.approved");
        case ReservationStatus.CONFIRMED:
            return t("status.confirmed");
        case ReservationStatus.DENIED:
            return t("status.denied");
        default:
            return status;
    }
};

const getDenialReasonLabel = (reason: DenialReason, t: (key: string) => string): string => {
    switch (reason) {
        case DenialReason.CONTENT_POLICY:
            return t("denialReasons.contentPolicy");
        case DenialReason.CREATIVE_TECHNICAL:
            return t("denialReasons.creativeTechnical");
        case DenialReason.LEGAL_COMPLIANCE:
            return t("denialReasons.legalCompliance");
        case DenialReason.MEDIA_OWNER_RULES:
            return t("denialReasons.mediaOwnerRules");
        case DenialReason.LOCAL_VENUE:
            return t("denialReasons.localVenue");
        case DenialReason.OTHER:
            return t("denialReasons.other");
        default:
            return reason;
    }
};

interface ReservationCardProps {
    reservation: ReservationResponseDTO;
    viewType: ViewType;
    onPayClick?: (reservation: ReservationResponseDTO) => void;
    t: (key: string) => string;
    locale: string;
}

function ReservationCard({
                             reservation,
                             viewType,
                             onPayClick,
                             t,
                             locale,
                         }: ReservationCardProps) {
    const router = useRouter();
    const [media, setMedia] = useState<Media | null>(null);
    const [loading, setLoading] = useState(true);
    const [reasonOpened, setReasonOpened] = useState(false);
    const [translatedDescription, setTranslatedDescription] = useState<string | null>(null);
    const [hasBeenProcessed, setHasBeenProcessed] = useState(false);
    const [isTranslating, setIsTranslating] = useState(false);
    const [showOriginal, setShowOriginal] = useState(false);

    useEffect(() => {
        let isMounted = true;

        const fetchMedia = async () => {
            try {
                const mediaData = await getMediaById(reservation.mediaId);
                if (isMounted) setMedia(mediaData);
            } catch (error) {
                console.error("Failed to load media:", error);
            } finally {
                if (isMounted) setLoading(false);
            }
        };

        fetchMedia();

        return () => {
            isMounted = false;
        };
    }, [reservation.mediaId]);

    useEffect(() => {
        if (!reasonOpened ||
            !reservation.denialDetails?.description ||
            hasBeenProcessed) return;

        let isMounted = true;

        setHasBeenProcessed(true);
        setIsTranslating(true);

        translate(reservation.denialDetails.description, { from: locale === 'fr' ? 'en' : 'fr', to: locale })
            .then(translated => {
                if (!isMounted) return;
                setTranslatedDescription(translated);
            })
            .catch(error => {
                console.error('Translation error:', error);
                if (!isMounted) return;
                setTranslatedDescription(reservation.denialDetails?.description || '');
            })
            .finally(() => {
                if (!isMounted) return;
                setIsTranslating(false);
            });

        return () => {
            isMounted = false;
        };
    }, [reasonOpened, reservation.denialDetails?.description, hasBeenProcessed, locale]);

    const handleCardClick = () => {
        if (viewType === "media-owner") {
            router.push(`/${locale}/dashboard/media-owner/advertisements/${reservation.reservationId}`);
        }
    };

    const isMediaOwnerView = viewType === "media-owner";
    const isAdvertiserView = viewType === "advertiser";
    const isDenied = reservation.status === ReservationStatus.DENIED;
    const isApproved = reservation.status === ReservationStatus.APPROVED;

    const displayedDescription = showOriginal
        ? reservation.denialDetails?.description
        : translatedDescription || reservation.denialDetails?.description;

    const toggleLabel = showOriginal ? t("showTranslation") : t("showOriginal");

    return (
        <Card
            shadow="sm"
            padding="lg"
            radius="lg"
            style={{
                cursor: isMediaOwnerView ? "pointer" : "default",
                height: "100%",
            }}
            onClick={handleCardClick}
        >
            <Stack gap="sm" style={{ height: "100%" }}>
                <Group justify="space-between" align="center" wrap="nowrap">
                    {loading ? (
                        <Skeleton height={20} width="60%" />
                    ) : (
                        <Text fw={600} size="sm" truncate>
                            {isMediaOwnerView
                                ? reservation.campaignName || t("unknownCampaign")
                                : media?.title || t("unknownMedia")}
                        </Text>
                    )}
                    <Badge color={getStatusColor(reservation.status)} variant="light" size="md">
                        {getStatusLabel(reservation.status, t)}
                    </Badge>
                </Group>

                {loading ? (
                    <Skeleton height={16} width="80%" />
                ) : (
                    <Text size="xs" c="dimmed" truncate>
                        {isMediaOwnerView
                            ? media?.title || t("unknownMedia")
                            : reservation.campaignName || t("unknownCampaign")}
                    </Text>
                )}

                <Group gap="xs" align="center">
                    <IconCalendar size={14} color="var(--mantine-color-dimmed)" />
                    <Text size="xs" c="dimmed">
                        {formatDate(reservation.startDate, isMediaOwnerView ? locale : undefined)} —{" "}
                        {formatDate(reservation.endDate, isMediaOwnerView ? locale : undefined)}
                    </Text>
                </Group>

                <Group gap="xs" align="center" style={isMediaOwnerView ? { marginTop: "auto" } : undefined}>
                    <IconCurrencyDollar size={14} color="var(--mantine-color-dimmed)" />
                    <Text size="sm" fw={500}>
                        {reservation.totalPrice.toLocaleString(isMediaOwnerView ? locale : undefined)}
                    </Text>
                </Group>

                {isAdvertiserView && isApproved && onPayClick && (
                    <Button fullWidth mt="auto" variant="gradient" onClick={() => onPayClick(reservation)}>
                        {t("payButton")}
                    </Button>
                )}

                {isDenied && reservation.denialDetails && (
                    <Popover
                        width={360}
                        position="top"
                        withArrow
                        shadow="md"
                        opened={reasonOpened}
                        onChange={setReasonOpened}
                    >
                        <Popover.Target>
                            <Button
                                fullWidth
                                mt="auto"
                                variant="light"
                                color="red"
                                leftSection={<IconInfoCircle size={16} />}
                                onClick={(e) => {
                                    e.stopPropagation();
                                    setReasonOpened((o) => !o);
                                }}
                            >
                                {t("viewReason")}
                            </Button>
                        </Popover.Target>
                        <Popover.Dropdown>
                            <Stack gap="sm">
                                <div>
                                    <Text size="xs" fw={700} c="red" mb={4}>
                                        {t("denialReasonLabel")}
                                    </Text>
                                    <Text size="sm" fw={500}>
                                        {getDenialReasonLabel(reservation.denialDetails.reason, t)}
                                    </Text>
                                </div>

                                {reservation.denialDetails.description && (
                                    <div>
                                        <Group justify="space-between" align="center" mb={4} wrap="nowrap">
                                            <Text size="xs" fw={700} c="dimmed">
                                                {t("denialDetailsLabel")}
                                            </Text>
                                            {translatedDescription && translatedDescription !== reservation.denialDetails.description && (
                                                <Tooltip label={toggleLabel} position="top">
                                                    <ActionIcon
                                                        size="sm"
                                                        variant="subtle"
                                                        color="gray"
                                                        aria-label={toggleLabel}
                                                        onClick={() => setShowOriginal(!showOriginal)}
                                                    >
                                                        <IconLanguage size={16} />
                                                    </ActionIcon>
                                                </Tooltip>
                                            )}
                                        </Group>

                                        {isTranslating ? (
                                            <Group gap="xs">
                                                <Loader size="xs" />
                                                <Text size="xs" c="dimmed">{t("translating")}</Text>
                                            </Group>
                                        ) : (
                                            <Text
                                                size="sm"
                                                style={{
                                                    whiteSpace: "pre-wrap",
                                                    wordBreak: "break-word",
                                                    maxHeight: "200px",
                                                    overflowY: "auto"
                                                }}
                                            >
                                                {displayedDescription}
                                            </Text>
                                        )}
                                    </div>
                                )}
                            </Stack>
                        </Popover.Dropdown>
                    </Popover>
                )}
            </Stack>
        </Card>
    );
}

export function ReservationCards({
                                     reservations,
                                     viewType,
                                     onPayClick,
                                 }: ReservationCardsProps) {
    const locale = useLocale();
    const t = useTranslations(
        viewType === "advertiser" ? "advertiserReservations" : "adRequests.page"
    );

    if (reservations.length === 0) {
        return (
            <Text c="dimmed" ta="center" py="xl" size="sm">
                {viewType === "advertiser" ? t("noReservations") : t("noRequests")}
            </Text>
        );
    }

    return (
        <Grid>
            {reservations.map((reservation) => (
                <Grid.Col span={{ base: 12, sm: 6, lg: 4 }} key={reservation.reservationId}>
                    <ReservationCard
                        reservation={reservation}
                        viewType={viewType}
                        onPayClick={onPayClick}
                        t={t}
                        locale={locale}
                    />
                </Grid.Col>
            ))}
        </Grid>
    );
}