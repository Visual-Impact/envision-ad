"use client";

import { useTranslations } from "next-intl";
import { Container, Stack, Text, Title } from "@mantine/core";

export default function PrivacyPolicyPage() {
    const t = useTranslations("privacyPage");

    return (
        <Container size="sm" py={80}>
            <Stack gap="md" align="center" ta="center">
                <Title order={1}>{t("title")}</Title>
                <Text c="dimmed">{t("placeholder")}</Text>
            </Stack>
        </Container>
    );
}
