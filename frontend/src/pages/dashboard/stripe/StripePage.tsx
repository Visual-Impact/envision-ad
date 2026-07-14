'use client';
import { useState, useEffect, useCallback } from 'react';
import { getStripeAccountStatus, createStripeConnection } from '@/features/payment';
import {
    Title, Paper, Text, Button, Loader, Alert, Stack, Group, ThemeIcon, rem
} from '@mantine/core';
import { IconCheck, IconX, IconAlertTriangle } from '@tabler/icons-react';
import { useTranslations } from "next-intl";
import { useSearchParams } from 'next/navigation';
import { notifications } from '@mantine/notifications';
import { useOrganization } from "@/app/providers";

interface StripeStatus {
    connected: boolean;
    onboardingComplete: boolean;
    chargesEnabled: boolean;
    payoutsEnabled: boolean;
    stripeAccountId?: string;
}

export default function StripePage() {
    const t = useTranslations('stripe');
    const { organization } = useOrganization();
    const searchParams = useSearchParams();

    const [status, setStatus] = useState<StripeStatus | null>(null);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [isConnecting, setIsConnecting] = useState(false);

    const fetchStatus = useCallback(async () => {
        if (!organization) return;
        try {
            setIsLoading(true);
            const stripeStatus = await getStripeAccountStatus(organization.businessId);
            setStatus(stripeStatus);
        } catch (e) {
            console.error("Failed to fetch Stripe status", e);
            setError(e instanceof Error ? e.message : t('errors.fetchFailed'));
        } finally {
            setIsLoading(false);
        }
    }, [organization, t]);

    useEffect(() => {
        if (!searchParams) return;

        const onboardingStatus = searchParams.get('onboarding');
        if (onboardingStatus === 'success') {
            notifications.show({
                title: t('notifications.success.title'),
                message: t('notifications.success.message'),
                color: 'teal',
                icon: <IconCheck />,
            });
            const timer = setTimeout(() => { void fetchStatus(); }, 2000);
            return () => clearTimeout(timer);
        } else if (onboardingStatus === 'refresh') {
            notifications.show({
                title: t('notifications.refresh.title'),
                message: t('notifications.refresh.message'),
                color: 'blue',
            });
        }
    }, [searchParams, t, fetchStatus]);

    // Initial load is inlined (rather than calling `fetchStatus`) so the
    // effect's own state updates stay local to the effect, with proper
    // unmount cancellation. `fetchStatus` remains for the onboarding-success
    // re-check above, which runs from a timer callback, not directly.
    useEffect(() => {
        if (!organization) return;
        let cancelled = false;

        (async () => {
            setIsLoading(true);
            try {
                const stripeStatus = await getStripeAccountStatus(organization.businessId);
                if (!cancelled) setStatus(stripeStatus);
            } catch (e) {
                console.error("Failed to fetch Stripe status", e);
                if (!cancelled) setError(e instanceof Error ? e.message : t('errors.fetchFailed'));
            } finally {
                if (!cancelled) setIsLoading(false);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [organization, t]);

    const handleConnect = async () => {
        if (!organization) return;

        setIsConnecting(true);
        try {
            const response = await createStripeConnection(organization.businessId);
            if (response?.onboardingUrl) {
                window.location.href = response.onboardingUrl;
            } else {
                setError(t('errors.connectFailed'));
            }
        } catch (e) {
            console.error("Failed to connect to Stripe", e);
            const apiMessage = (e as { response?: { data?: { message?: string } } })?.response?.data?.message;
            setError(apiMessage || (e instanceof Error ? e.message : t('errors.connectFailed')));
        } finally {
            setIsConnecting(false);
        }
    };

    const StatusCheck = ({ label, checked }: { label: string; checked: boolean }) => (
        <Group>
            <ThemeIcon color={checked ? 'teal' : 'red'} size={24} radius="xl">
                {checked
                    ? <IconCheck style={{ width: rem(16), height: rem(16) }} />
                    : <IconX style={{ width: rem(16), height: rem(16) }} />}
            </ThemeIcon>
            <Text>{label}</Text>
        </Group>
    );

    const renderStatus = () => {
        if (!status) return <Text>{t('noStatus')}</Text>;

        const isFullyOnboarded = status.connected && status.onboardingComplete && status.chargesEnabled && status.payoutsEnabled;

        if (isFullyOnboarded) {
            return (
                <Alert icon={<IconCheck size="1rem" />} title={t('status.connected.title')} color="teal">
                    <Text>{t('status.connected.description')}</Text>
                    <Text size="xs" c="dimmed" mt="sm">{t('status.connected.accountId')}: {status.stripeAccountId}</Text>
                </Alert>
            );
        }

        return (
            <Paper shadow="sm" radius="lg" p="md">
                <Stack>
                    <Title order={4}>{t('status.incomplete.title')}</Title>
                    <Text c="dimmed">{t('status.incomplete.description')}</Text>
                    <StatusCheck label={t('status.incomplete.checks.onboarding')} checked={status.onboardingComplete} />
                    <StatusCheck label={t('status.incomplete.checks.charges')} checked={status.chargesEnabled} />
                    <StatusCheck label={t('status.incomplete.checks.payouts')} checked={status.payoutsEnabled} />
                    <Button variant="gradient" onClick={handleConnect} loading={isConnecting} mt="md">
                        {status.connected ? t('buttons.continueOnboarding') : t('buttons.connect')}
                    </Button>
                </Stack>
            </Paper>
        );
    };

    return (
        <Stack gap="lg" p="md">
            <Title order={2}>{t('title')}</Title>
            {isLoading ? (
                <Loader />
            ) : error ? (
                <Alert icon={<IconAlertTriangle size="1rem" />} title="Error" color="red">{error}</Alert>
            ) : (
                renderStatus()
            )}
        </Stack>
    );
}