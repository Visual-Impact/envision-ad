"use client";

import React, { useState, useEffect, useCallback } from "react";
import {
    Center, Stack, Title, Text, Button, Group, Loader,
    ThemeIcon, Badge, Divider, Paper, Box, Alert, SimpleGrid,
} from "@mantine/core";
import { useTranslations } from "next-intl";
import { useMediaQuery } from "@mantine/hooks";
import { useUser } from "@auth0/nextjs-auth0/client";
import { notifications } from "@mantine/notifications";
import { useOrganizationForm } from "@/pages/dashboard/organization/hooks/useOrganizationForm";
import { OrganizationModal } from "@/pages/dashboard/organization/ui/modals/OrganizationModal";
import { createOrganization } from "@/features/organization-management/api";
import { AUTH0_ROLES } from "@/shared/lib/auth/roles";
import { useOrganization, usePermissions } from "@/app/providers";
import { getStripeAccountStatus, createStripeConnection } from "@/features/payment";
import {
    IconCheck, IconBuilding, IconCreditCard, IconRocket,
    IconAlertTriangle, IconPhoto, IconSpeakerphone, IconCalendar,
} from "@tabler/icons-react";
import { getAllMediaLocations } from "@/features/media-location-management/api";
import { getAllAdCampaigns } from "@/features/ad-campaign-management/api";
import { getAllReservationByAdvertiserBusinessId } from "@/features/reservation-management/api";
import { ReservationStatus } from "@/entities/reservation";
import { useRouter, Link } from "@/shared/lib/i18n/navigation";

interface StripeStatus {
    connected: boolean;
    onboardingComplete: boolean;
    chargesEnabled: boolean;
    payoutsEnabled: boolean;
}

type StepStatus = "complete" | "current" | "upcoming";
type StepKey = "organization" | "stripe" | "media" | "campaign" | "reservation";

interface Step {
    number: number;
    key: StepKey;
    icon: React.ReactNode;
    status: StepStatus;
}

export default function OnboardingPage() {
    const t = useTranslations("onboarding");
    const { user } = useUser();
    const { organization, refreshOrganization, loading: orgLoading } = useOrganization();
    const { permissions, refreshPermissions, loading: permissionsLoading } = usePermissions();
    const { formState, updateField, resetForm } = useOrganizationForm();
    const [isModalOpen, setIsModalOpen] = useState(false);
    const isMobile = useMediaQuery("(max-width: 48em)") ?? false;
    const router = useRouter();

    const [stripeStatus, setStripeStatus] = useState<StripeStatus | null>(null);
    const [isStripeLoading, setIsStripeLoading] = useState(false);
    const [isConnecting, setIsConnecting] = useState(false);
    const [stripeError, setStripeError] = useState<string | null>(null);

    const isMediaOwner = !!organization?.roles?.mediaOwner;
    const isAdvertiser = !!organization?.roles?.advertiser;
    const isBoth = isMediaOwner && isAdvertiser;
    const isAdmin =
        permissions.includes('patch:media_status') &&
        permissions.includes('readAll:verification') &&
        permissions.includes('update:verification');

    useEffect(() => {
        if (isAdmin) {
            router.push('/dashboard/admin/metrics');
        }
    }, [isAdmin, router]);

    const fetchStripeStatus = useCallback(async () => {
        if (!organization || !isMediaOwner) return;
        try {
            setIsStripeLoading(true);
            const status = await getStripeAccountStatus(organization.businessId);
            setStripeStatus(status);
        } catch (e) {
            console.error("Failed to fetch Stripe status", e);
        } finally {
            setIsStripeLoading(false);
        }
    }, [organization, isMediaOwner]);

    useEffect(() => {
        void fetchStripeStatus();
    }, [fetchStripeStatus]);

    const handleStripeConnect = async () => {
        if (!organization) return;
        setIsConnecting(true);
        setStripeError(null);
        try {
            const response = await createStripeConnection(organization.businessId);
            if (response?.onboardingUrl) {
                window.location.href = response.onboardingUrl;
            } else {
                setStripeError(t("actions.stripe.errors.connectFailed"));
            }
        } catch (e) {
            console.error("Failed to connect to Stripe", e);
            const apiMessage = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
            setStripeError(apiMessage || t("actions.stripe.errors.connectFailed"));
        } finally {
            setIsConnecting(false);
        }
    };

    const isStripeOnboarded =
        !isMediaOwner || (
            stripeStatus?.onboardingComplete &&
            stripeStatus?.chargesEnabled &&
            stripeStatus?.payoutsEnabled
        );

    const [hasMedia, setHasMedia] = useState(false);
    const [hasCampaign, setHasCampaign] = useState(false);
    const [hasReservation, setHasReservation] = useState(false);

    useEffect(() => {
        if (!organization || !isMediaOwner) return;
        getAllMediaLocations(organization.businessId)
            .then((locations) => {
                const anyMedia = locations.some((loc) => loc.mediaList && loc.mediaList.length > 0);
                setHasMedia(anyMedia);
            })
            .catch((e) => console.error("Failed to fetch media locations", e));
    }, [organization, isMediaOwner]);

    useEffect(() => {
        if (!organization || !isAdvertiser) return;
        getAllAdCampaigns(organization.businessId)
            .then((campaigns) => setHasCampaign(campaigns.length > 0))
            .catch((e) => console.error("Failed to fetch campaigns", e));
    }, [organization, isAdvertiser]);

    useEffect(() => {
        if (!organization || !isAdvertiser) return;
        getAllReservationByAdvertiserBusinessId(organization.businessId)
            .then((reservations) => {
                const anyConfirmed = reservations.some((r) => r.status === ReservationStatus.CONFIRMED);
                setHasReservation(anyConfirmed);
            })
            .catch((e) => console.error("Failed to fetch reservations", e));
    }, [organization, isAdvertiser]);

    const hasOrganization = !!organization;

    const stepCompletionMap: Record<StepKey, boolean> = {
        organization: hasOrganization,
        stripe: !!isStripeOnboarded,
        media: hasMedia,
        campaign: hasCampaign,
        reservation: hasReservation,
    };

    const iconMap: Record<StepKey, React.ReactNode> = {
        organization: <IconBuilding size="1.25rem" />,
        stripe: <IconCreditCard size="1.25rem" />,
        media: <IconPhoto size="1.25rem" />,
        campaign: <IconSpeakerphone size="1.25rem" />,
        reservation: <IconCalendar size="1.25rem" />,
    };

    const mediaOwnerKeys: StepKey[] = ["stripe", "media"];
    const advertiserKeys: StepKey[] = ["campaign", "reservation"];

    const buildFlatKeys = (): StepKey[] => {
        const keys: StepKey[] = ["organization"];
        if (isMediaOwner) keys.push(...mediaOwnerKeys);
        if (isAdvertiser) keys.push(...advertiserKeys);
        return keys;
    };

    const flatKeys = buildFlatKeys();

    const makeStep = (key: StepKey, number: number, trackKeys?: StepKey[]): Step => {
        const isComplete = stepCompletionMap[key];
        let status: StepStatus;
        if (isComplete) {
            status = "complete";
        } else {
            const keysToCheck = trackKeys ?? flatKeys;
            const idx = keysToCheck.indexOf(key);
            const allPreviousComplete = keysToCheck.slice(0, idx).every((k) => stepCompletionMap[k]);
            const orgDone = key === "organization" || stepCompletionMap["organization"];
            status = orgDone && allPreviousComplete ? "current" : "upcoming";
        }
        return { number, key, icon: iconMap[key], status };
    };

    const allStepKeys = buildFlatKeys();
    const allDone = allStepKeys.every((k) => stepCompletionMap[k]);

    const getCurrentStep = (keys: StepKey[]): Step | undefined => {
        return keys
            .map((key, i) => makeStep(key, i + 1, keys))
            .find((s) => s.status === "current");
    };

    const handleCreate = async () => {
        if (!user?.sub) return;
        try {
            await createOrganization(formState);
            await fetch(`/api/auth0/update-user-roles/${encodeURIComponent(user.sub)}`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    roles: [
                        AUTH0_ROLES.BUSINESS_OWNER,
                        ...(formState.roles.advertiser ? [AUTH0_ROLES.ADVERTISER] : []),
                        ...(formState.roles.mediaOwner ? [AUTH0_ROLES.MEDIA_OWNER] : []),
                    ],
                }),
            });
            await refreshPermissions();
            await refreshOrganization();
            setIsModalOpen(false);
            resetForm();
            notifications.show({ title: t("success.title"), message: t("success.create"), color: "green" });
        } catch (error) {
            console.error("Failed to create organization", error);
            notifications.show({ title: t("errors.title"), message: t("errors.createFailed"), color: "red" });
        }
    };

    // --- Render helpers ---

    const renderActionPanel = (stepKey: StepKey) => {
        if (stepKey === "organization") return (
            <Stack gap="sm">
                <Text fw={500}>{t("actions.organization.prompt")}</Text>
                <Text size="sm" c="dimmed">{t("actions.organization.hint")}</Text>
                <Button fullWidth variant="gradient" onClick={() => setIsModalOpen(true)}>{t("actions.organization.cta")}</Button>
            </Stack>
        );
        if (stepKey === "stripe") return (
            <Stack gap="sm">
                {isStripeLoading ? (
                    <Group>
                        <Loader size="sm" />
                        <Text size="sm" c="dimmed">{t("actions.stripe.loading")}</Text>
                    </Group>
                ) : (
                    <>
                        <Text fw={500}>{t("actions.stripe.prompt")}</Text>
                        <Text size="sm" c="dimmed">{t("actions.stripe.hint")}</Text>
                        {stripeError && (
                            <Alert icon={<IconAlertTriangle size="1rem" />} color="red" variant="light">
                                {stripeError}
                            </Alert>
                        )}
                        <Button fullWidth variant="gradient" onClick={handleStripeConnect} loading={isConnecting}>
                            {stripeStatus?.connected ? t("actions.stripe.ctaContinue") : t("actions.stripe.cta")}
                        </Button>
                    </>
                )}
            </Stack>
        );
        if (stepKey === "media") return (
            <Stack gap="sm">
                <Text fw={500}>{t("actions.media.prompt")}</Text>
                <Text size="sm" c="dimmed">{t("actions.media.hint")}</Text>
                <Button fullWidth variant="gradient" component={Link} href="/dashboard/media-owner/locations">
                    {t("actions.media.cta")}
                </Button>
            </Stack>
        );
        if (stepKey === "campaign") return (
            <Stack gap="sm">
                <Text fw={500}>{t("actions.campaign.prompt")}</Text>
                <Text size="sm" c="dimmed">{t("actions.campaign.hint")}</Text>
                <Button fullWidth variant="gradient" component={Link} href="/dashboard/advertiser/campaigns">
                    {t("actions.campaign.cta")}
                </Button>
            </Stack>
        );
        if (stepKey === "reservation") return (
            <Stack gap="sm">
                <Text fw={500}>{t("actions.reservation.prompt")}</Text>
                <Text size="sm" c="dimmed">{t("actions.reservation.hint")}</Text>
                <Button fullWidth variant="gradient" component={Link} href="/browse">
                    {t("actions.reservation.cta")}
                </Button>
            </Stack>
        );
    };

    const renderStepList = (keys: StepKey[], trackKeys?: StepKey[]) => (
        <Stack gap="sm">
            {keys.map((key, index) => {
                const step = makeStep(key, index + 1, trackKeys ?? keys);
                return (
                    <React.Fragment key={key}>
                        <StepCard step={step} t={t} />
                        {index < keys.length - 1 && (
                            <Box pl={28}>
                                <Divider orientation="vertical" h={16} style={{ alignSelf: "flex-start" }} />
                            </Box>
                        )}
                    </React.Fragment>
                );
            })}
        </Stack>
    );

    const mediaTrackFull: StepKey[] = ["organization", ...mediaOwnerKeys];
    const advertiserTrackFull: StepKey[] = ["organization", ...advertiserKeys];

    const mediaCurrentStep = isBoth ? getCurrentStep(mediaTrackFull) : undefined;
    const advertiserCurrentStep = isBoth ? getCurrentStep(advertiserTrackFull) : undefined;

    const singleCurrentStep = !isBoth ? getCurrentStep(allStepKeys) : undefined;

    if (permissionsLoading || orgLoading || isAdmin) {
        return (
            <Center h="100vh">
                <Loader />
            </Center>
        );
    }

    return (
        <Center py={{ base: "md", sm: "xl" }} px={{ base: "xs", sm: "md" }}>
            <Stack gap="xl" w="100%" maw={isBoth ? 760 : 560}>
                {/* Header */}
                <Stack gap="xs" align="center" ta="center">
                    <ThemeIcon size={56} radius="xl" variant="gradient">
                        <IconRocket size="1.75rem" />
                    </ThemeIcon>
                    <Title order={2}>{t("header.title")}</Title>
                    <Text c="dimmed" size="sm" maw={400}>
                        {t("header.description")}
                    </Text>
                </Stack>

                {/* Steps */}
                {!isBoth ? (
                    renderStepList(allStepKeys)
                ) : (
                    <Stack gap="sm">
                        {/* Shared org step */}
                        <StepCard step={makeStep("organization", 1, allStepKeys)} t={t} />
                        <Box pl={28}>
                            <Divider orientation="vertical" h={16} style={{ alignSelf: "flex-start" }} />
                        </Box>

                        {/* Desktop: two rows of two columns (steps, then actions) */}
                        {!isMobile && (
                            <Stack gap="lg">
                                <SimpleGrid cols={2} spacing="lg">
                                    <Paper shadow="sm" radius="lg" p="md">
                                        <Stack gap="xs" mb="sm">
                                            <Badge color="violet" variant="light" size="sm">
                                                {t("roles.mediaOwner")}
                                            </Badge>
                                        </Stack>
                                        {renderStepList(mediaOwnerKeys, mediaTrackFull)}
                                    </Paper>
                                    <Paper shadow="sm" radius="lg" p="md">
                                        <Stack gap="xs" mb="sm">
                                            <Badge color="orange" variant="light" size="sm">
                                                {t("roles.advertiser")}
                                            </Badge>
                                        </Stack>
                                        {renderStepList(advertiserKeys, advertiserTrackFull)}
                                    </Paper>
                                </SimpleGrid>
                                {!allDone && (
                                    <SimpleGrid cols={2} spacing="lg">
                                        <Paper shadow="sm" radius="lg" p="lg">
                                            {mediaCurrentStep
                                                ? renderActionPanel(mediaCurrentStep.key)
                                                : <Group gap="xs"><ThemeIcon size={28} radius="xl" color="green"><IconCheck size="1rem" /></ThemeIcon><Text size="sm" c="dimmed">{t("trackComplete.mediaOwner")}</Text></Group>}
                                        </Paper>
                                        <Paper shadow="sm" radius="lg" p="lg">
                                            {advertiserCurrentStep
                                                ? renderActionPanel(advertiserCurrentStep.key)
                                                : <Group gap="xs"><ThemeIcon size={28} radius="xl" color="green"><IconCheck size="1rem" /></ThemeIcon><Text size="sm" c="dimmed">{t("trackComplete.advertiser")}</Text></Group>}
                                        </Paper>
                                    </SimpleGrid>
                                )}
                            </Stack>
                        )}

                        {/* Mobile: each track's steps immediately followed by its action */}
                        {isMobile && (
                            <Stack gap="sm">
                                <Paper shadow="sm" radius="lg" p="sm">
                                    <Stack gap="xs" mb="sm">
                                        <Badge color="violet" variant="light" size="sm">
                                            {t("roles.mediaOwner")}
                                        </Badge>
                                    </Stack>
                                    {renderStepList(mediaOwnerKeys, mediaTrackFull)}
                                </Paper>
                                {!allDone && (
                                    <Paper shadow="sm" radius="lg" p="md">
                                        {mediaCurrentStep
                                            ? renderActionPanel(mediaCurrentStep.key)
                                            : <Group gap="xs"><ThemeIcon size={28} radius="xl" color="green"><IconCheck size="1rem" /></ThemeIcon><Text size="sm" c="dimmed">{t("trackComplete.mediaOwner")}</Text></Group>}
                                    </Paper>
                                )}
                                <Paper shadow="sm" radius="lg" p="sm">
                                    <Stack gap="xs" mb="sm">
                                        <Badge color="orange" variant="light" size="sm">
                                            {t("roles.advertiser")}
                                        </Badge>
                                    </Stack>
                                    {renderStepList(advertiserKeys, advertiserTrackFull)}
                                </Paper>
                                {!allDone && (
                                    <Paper shadow="sm" radius="lg" p="md">
                                        {advertiserCurrentStep
                                            ? renderActionPanel(advertiserCurrentStep.key)
                                            : <Group gap="xs"><ThemeIcon size={28} radius="xl" color="green"><IconCheck size="1rem" /></ThemeIcon><Text size="sm" c="dimmed">{t("trackComplete.advertiser")}</Text></Group>}
                                    </Paper>
                                )}
                            </Stack>
                        )}
                    </Stack>
                )}

                {/* Action panel — single role only */}
                {!allDone && !isBoth && singleCurrentStep && (
                    <Paper shadow="sm" radius="lg" p={{ base: "md", sm: "lg" }}>
                        {renderActionPanel(singleCurrentStep.key)}
                    </Paper>
                )}

                {/* All done */}
                {allDone && (
                    <Paper shadow="sm" radius="lg" p={{ base: "md", sm: "lg" }} bg="green.0">
                        <Stack gap="xs" align="center" ta="center">
                            <ThemeIcon size={44} radius="xl" color="green">
                                <IconCheck size="1.5rem" />
                            </ThemeIcon>
                            <Title order={4}>{t("allDone.title")}</Title>
                            <Text size="sm" c="dimmed">{t("allDone.description")}</Text>
                        </Stack>
                    </Paper>
                )}
            </Stack>

            <OrganizationModal
                opened={isModalOpen}
                onClose={() => { setIsModalOpen(false); resetForm(); }}
                onSave={handleCreate}
                formState={formState}
                onFieldChange={updateField}
                editingId={null}
            />
        </Center>
    );
}

function StepCard({ step, t }: { step: Step; t: ReturnType<typeof useTranslations> }) {
    const { status, number, key, icon } = step;
    const colorMap: Record<StepStatus, string> = { complete: "green", current: "blue", upcoming: "gray" };
    const color = colorMap[status];

    return (
        <Group gap="md" align="flex-start" wrap="nowrap">
            <ThemeIcon
                size={40} radius="xl" color={color}
                variant={status === "upcoming" ? "light" : "filled"}
                style={{ flexShrink: 0, marginTop: 2 }}
            >
                {status === "complete" ? <IconCheck size="1.1rem" /> : icon}
            </ThemeIcon>
            <Stack gap={4} style={{ flex: 1, minWidth: 0 }}>
                <Group gap="xs" wrap="wrap">
                    <Text size="xs" c="dimmed" fw={500}>{t("stepLabel", { number })}</Text>
                    {status === "complete" && <Badge size="xs" color="green" variant="light">{t("badge.complete")}</Badge>}
                    {status === "current" && <Badge size="xs" color="blue" variant="light">{t("badge.current")}</Badge>}
                </Group>
                <Text fw={600} c={status === "upcoming" ? "dimmed" : undefined}>
                    {t(`steps.${key}.title`)}
                </Text>
                <Text size="sm" c="dimmed">
                    {t(`steps.${key}.description`)}
                </Text>
            </Stack>
        </Group>
    );
}