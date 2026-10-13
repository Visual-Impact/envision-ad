'use client';
import { useState, useEffect } from 'react';
import { getStripeAccountStatus, createStripeConnection, type StripeAccountStatus } from '@/features/payment';
import {
    Title, Paper, Text, Button, Loader, Alert, Stack, Group
} from '@mantine/core';
import { IconCheck, IconAlertTriangle, IconClock } from '@tabler/icons-react';
import { useTranslations } from "next-intl";
import { useSearchParams } from 'next/navigation';
import { useOrganization } from "@/entities/organization";
import { usePathname, useRouter } from "@/shared/lib/i18n";

// After a return from Stripe, keep re-checking while Stripe is still verifying,
// for up to ~30 seconds. Verification often finishes within that window.
const POLL_INTERVAL_MS = 3000;
const POLL_ATTEMPTS = 10;

export default function StripePage() {
    const t = useTranslations('stripe');
    const { organization } = useOrganization();
    const businessId = organization?.businessId;
    const searchParams = useSearchParams();
    const router = useRouter();
    const pathname = usePathname();

    // Read once: the query param is stripped from the URL below, so a reload
    // doesn't replay the return-from-Stripe flow.
    const [returnFlow] = useState(() => searchParams?.get('onboarding') ?? null);

    const [status, setStatus] = useState<StripeAccountStatus | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [isChecking, setIsChecking] = useState(returnFlow === 'success');
    const [isRefreshing, setIsRefreshing] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [isConnecting, setIsConnecting] = useState(returnFlow === 'refresh');

    useEffect(() => {
        if (returnFlow) router.replace(pathname);
    }, [returnFlow, router, pathname]);

    // The backend asks Stripe directly whenever the account isn't fully
    // onboarded, so each fetch reflects Stripe's current view of the account.
    useEffect(() => {
        if (!businessId) return;
        let cancelled = false;
        let timer: ReturnType<typeof setTimeout> | undefined;

        const load = async (attempt: number) => {
            try {
                const stripeStatus = await getStripeAccountStatus(businessId);
                if (cancelled) return;
                setStatus(stripeStatus);
                const stillVerifying = stripeStatus.state === 'PENDING_VERIFICATION';
                if (returnFlow === 'success' && stillVerifying && attempt < POLL_ATTEMPTS) {
                    timer = setTimeout(() => { void load(attempt + 1); }, POLL_INTERVAL_MS);
                    return;
                }
                setIsChecking(false);
            } catch (e) {
                console.error("Failed to fetch Stripe status", e);
                if (cancelled) return;
                setError(t('errors.fetchFailed'));
                setIsChecking(false);
            } finally {
                if (!cancelled) setIsLoading(false);
            }
        };
        void load(1);

        return () => {
            cancelled = true;
            clearTimeout(timer);
        };
    }, [businessId, returnFlow, t]);

    // Stripe sends the owner to the refresh URL when an onboarding link has
    // expired or was already used. Stripe's guidance is to issue a fresh link
    // and send them straight back, rather than asking them to start over.
    useEffect(() => {
        if (returnFlow !== 'refresh' || !businessId) return;

        (async () => {
            try {
                const response = await createStripeConnection(businessId);
                if (response?.onboardingUrl) {
                    window.location.href = response.onboardingUrl;
                    return;
                }
                setError(t('errors.connectFailed'));
            } catch (e) {
                console.error("Failed to reopen Stripe onboarding", e);
                setError(t('errors.connectFailed'));
            }
            setIsConnecting(false);
        })();
    }, [returnFlow, businessId, t]);

    const handleConnect = async () => {
        if (!businessId) return;

        setIsConnecting(true);
        try {
            const response = await createStripeConnection(businessId);
            if (response?.onboardingUrl) {
                window.location.href = response.onboardingUrl;
            } else {
                setError(t('errors.connectFailed'));
            }
        } catch (e) {
            console.error("Failed to connect to Stripe", e);
            setError(t('errors.connectFailed'));
        } finally {
            setIsConnecting(false);
        }
    };

    const handleCheckAgain = async () => {
        if (!businessId) return;
        setIsRefreshing(true);
        try {
            setStatus(await getStripeAccountStatus(businessId));
            setError(null);
        } catch (e) {
            console.error("Failed to fetch Stripe status", e);
            setError(t('errors.fetchFailed'));
        } finally {
            setIsRefreshing(false);
        }
    };

    const renderWaiting = (title: string, description?: string) => (
        <Paper shadow="sm" radius="lg" p="md">
            <Group wrap="nowrap" align="flex-start">
                <Loader size="sm" mt={2} />
                <Stack gap={4}>
                    <Text fw={500}>{title}</Text>
                    {description && <Text size="sm" c="dimmed">{description}</Text>}
                </Stack>
            </Group>
        </Paper>
    );

    const renderOnboardingPrompt = (title: string, description: string, buttonLabel: string) => (
        <Paper shadow="sm" radius="lg" p="md">
            <Stack>
                <Title order={4}>{title}</Title>
                <Text c="dimmed">{description}</Text>
                <Button variant="gradient" onClick={handleConnect} loading={isConnecting} mt="md">
                    {buttonLabel}
                </Button>
            </Stack>
        </Paper>
    );

    const renderStatus = () => {
        if (!status) return <Text>{t('noStatus')}</Text>;

        switch (status.state) {
            case 'COMPLETE':
                return (
                    <Alert icon={<IconCheck size="1rem" />} title={t('status.connected.title')} color="teal">
                        <Text>{t('status.connected.description')}</Text>
                        <Text size="xs" c="dimmed" mt="sm">{t('status.connected.accountId')}: {status.stripeAccountId}</Text>
                    </Alert>
                );
            case 'PENDING_VERIFICATION':
                return (
                    <Alert icon={<IconClock size="1rem" />} title={t('status.pending.title')} color="blue">
                        <Text>{t('status.pending.description')}</Text>
                        <Button variant="light" size="xs" mt="md" onClick={handleCheckAgain} loading={isRefreshing}>
                            {t('buttons.checkAgain')}
                        </Button>
                    </Alert>
                );
            case 'ACTION_REQUIRED':
                // Details submitted means Stripe came back with more questions;
                // otherwise the owner left the form before finishing it.
                return status.onboardingComplete
                    ? renderOnboardingPrompt(
                        t('status.actionRequired.title'),
                        t('status.actionRequired.description'),
                        t('buttons.continueOnboarding'))
                    : renderOnboardingPrompt(
                        t('status.incomplete.title'),
                        t('status.incomplete.description'),
                        t('buttons.continueOnboarding'));
            default:
                return renderOnboardingPrompt(
                    t('status.notConnected.title'),
                    t('status.notConnected.description'),
                    t('buttons.connect'));
        }
    };

    const renderBody = () => {
        if (returnFlow === 'refresh' && isConnecting) return renderWaiting(t('redirecting'));
        if (isChecking) return renderWaiting(t('status.checking.title'), t('status.checking.description'));
        if (isLoading) return <Loader />;
        // Without a status there's nothing to show beneath the error
        if (error && !status) return null;
        return renderStatus();
    };

    return (
        <Stack gap="lg" p="md">
            <Title order={2}>{t('title')}</Title>
            {error && (
                <Alert icon={<IconAlertTriangle size="1rem" />} title="Error" color="red">{error}</Alert>
            )}
            {renderBody()}
        </Stack>
    );
}
