'use client';

import { useTranslations } from 'next-intl';
import { Button, Stack, Text, Title } from "@mantine/core";
import { Link } from "@/shared/lib/i18n";
import { useSearchParams } from 'next/navigation';

export default function AuthErrorPage() {
    const t = useTranslations('auth.errors');
    const searchParams = useSearchParams();

    const error = searchParams?.get('error') || '';
    const errorDescription = searchParams?.get('error_description') || '';

    const errorMessages: Record<string, string> = {
        'unauthorized': t('unauthorized'),
        'access_denied': t('accessDenied'),
        'server_error': t('serverError'),
        'temporarily_unavailable': t('temporarilyUnavailable'),
        'invalid_request': t('invalidRequest'),
        'invalid_scope': t('invalidScope'),
        'unsupported_response_type': t('unsupportedResponseType'),
        'user is blocked': t('userBlocked'),
    };

    const errorMessage = errorMessages[error]
        || errorMessages[errorDescription.toLowerCase()]
        || t('generic');

    return (
        <Stack
            align="center"
            justify="center"
            gap="md"
            style={{ minHeight: "calc(100vh - 340px)", borderRadius: 0 }}
        >
            <Title order={1} ta="center" size="h2">
                {t('title')}
            </Title>
            <Text ta="center" maw={500}>
                {errorMessage}
            </Text>
            <Button size="sm" component={Link} href="/">
                {t('back')}
            </Button>
        </Stack>
    );
}