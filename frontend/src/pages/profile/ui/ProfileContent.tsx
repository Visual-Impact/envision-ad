"use client";

import { Text, Group, Avatar, Stack, Badge, Title, Box, Divider, Paper, Button } from "@mantine/core";
import { IconPencil, IconArrowLeft } from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n";
import { useDisclosure } from "@mantine/hooks";
import { EditProfileModal } from "@/pages/profile/ui/EditProfileModal";
import { InfoRow } from "@/pages/profile/ui/InfoRow";
import React from "react";
import { Employee } from "@/entities/organization";
import { redirect } from "next/navigation";


// Helper for safe string display
const safeStr = (val: unknown, fallback = "-"): string => {
    if (typeof val === "string" && val.trim() !== "") return val;
    return fallback;
};

interface ProfileContentProps {
    user: Employee;
}

export default function ProfileContent({ user }: ProfileContentProps) {
    const t = useTranslations("profilePage");
    const [opened, { open, close }] = useDisclosure(false);

    if (!user) {
        redirect("/auth/login");
    }

    return (
        <Stack gap="lg">
            <Group>
                <Button
                    component={Link}
                    href="/"
                    variant="subtle"
                    color="gray"
                    leftSection={<IconArrowLeft size={16} />}
                >
                    {t("backToHome")}
                </Button>
            </Group>

            <Title order={1}>{t("title")}</Title>

            <Paper radius="md" withBorder shadow="sm" pos="relative" style={{ overflow: "hidden" }}>
                {/* Banner Section */}
                <Box h={140} bg="var(--mantine-color-blue-filled)" w="100%" />

                <Stack px="xl" pb="xl">
                    {/* Header with Avatar and Main Info - Overlapping Banner */}
                    <Group justify="space-between" align="flex-end" mt="-40px" mb="md">
                        <Group align="flex-end">
                            <Avatar
                                src={user.picture}
                                alt={safeStr(user.name, "User")}
                                size={120}
                                radius="100%"
                                style={{ border: '6px solid var(--mantine-color-body)' }}
                            />
                            <Box pb="xs">
                                <Title order={2}>{safeStr(user.name)}</Title>
                                <Text c="dimmed" size="sm">{t("memberStatus")}</Text>
                            </Box>
                        </Group>
                        <Button variant="light" leftSection={<IconPencil size={16} />} onClick={open}>
                            {t("editButton")}
                        </Button>
                    </Group>

                    {/* Main Content Sections */}
                    <Paper withBorder p="md" radius="md" bg="var(--mantine-color-gray-0)">
                        <Box>
                            <Title order={4} mb="xs" size="h5" tt="uppercase" c="dimmed">{t("personalInfo.title")}</Title>
                            <Stack gap="xs">
                                <InfoRow
                                    label={t("personalInfo.username")}
                                    value={safeStr(user.nickname || user.name)}
                                />
                                <Divider variant="dashed" />
                                <InfoRow
                                    label={t("personalInfo.email")}
                                    value={
                                        <Group gap="xs">
                                            {safeStr(user.email)}
                                            {user.email_verified && <Badge size="xs" color="green" variant="light">{t("personalInfo.emailVerified")}</Badge>}
                                        </Group>
                                    }
                                />
                                <Divider variant="dashed" />
                                <InfoRow
                                    label={t("personalInfo.firstName").toUpperCase()}
                                    value={safeStr(user.given_name)}
                                />
                                <Divider variant="dashed" />
                                <InfoRow
                                    label={t("personalInfo.lastName").toUpperCase()}
                                    value={safeStr(user.family_name)}
                                />
                            </Stack>
                        </Box>
                    </Paper>
                </Stack>
            </Paper>
            <EditProfileModal opened={opened} onClose={close} user={user} />
        </Stack>
    );
}
