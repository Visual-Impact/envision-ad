'use client';

import { useTranslations } from 'next-intl';
import { Button, Stack, Text, Title } from "@mantine/core";
import {Link} from "@/shared/lib/i18n";

export default function ForbiddenPage() {
    const t = useTranslations('forbidden');

    return (
        <Stack
            align="center"
            justify="center"
            gap="md"
            style={{minHeight: "calc(100vh - 340px)", borderRadius: 0}}
        >
            <Title order={1} ta="center" size="h2">
                403 {t('title')}
            </Title>
            <Text ta="center">{t('description')}</Text>
            <Button size="sm" component={Link} href="/">
                {t('back')}
            </Button>
        </Stack>
    );
}