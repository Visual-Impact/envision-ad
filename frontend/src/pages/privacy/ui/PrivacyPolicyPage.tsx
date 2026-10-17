"use client";

import { useTranslations } from "next-intl";
import { Anchor, Container, Stack, Text, Title } from "@mantine/core";
import { Link } from "@/shared/lib/i18n";

export default function PrivacyPolicyPage() {
    const t = useTranslations("privacyPage");

    return (
        <Container size="sm" py={80}>
            <Stack gap="md" align="center" ta="center">
                <Title order={1}>{t("title")}</Title>
                <Text c="dimmed">{t("placeholder")}</Text>
                <Text c="dimmed">
                    {t.rich("termsLink", {
                        link: (chunks) => <Anchor component={Link} href="/terms">{chunks}</Anchor>,
                    })}
                </Text>
            </Stack>
        </Container>
    );
}
