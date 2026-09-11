"use client";

import { Button, Stack, TextInput, Title, Text, Divider } from "@mantine/core";
import { useTranslations } from "next-intl";
import { useEffect, useState } from "react";
import { notifications } from "@mantine/notifications";
import { updateAppSetting } from "@/features/app-settings";
import { axiosInstance } from "@/shared/api";
import { HomepageGalleryManager } from "./HomepageGalleryManager";

export default function AppSettingsPage() {
    const t = useTranslations("admin.settingsPage");

    const [bookMeetingUrl, setBookMeetingUrl] = useState("");
    const [loading, setLoading] = useState(false);

    useEffect(() => {
        axiosInstance.get("/settings/book-meeting-url")
            .then((res) => setBookMeetingUrl(res.data.value ?? ""))
            .catch(() => {
                // not set yet — leave empty
            });
    }, []);

    const handleSave = async () => {
        setLoading(true);
        try {
            await updateAppSetting("book-meeting-url", bookMeetingUrl);
            notifications.show({ title: t("notifications.saved"), message: "", color: "green" });
        } catch {
            notifications.show({ title: t("notifications.saveFailed"), message: "", color: "red" });
        } finally {
            setLoading(false);
        }
    };

    return (
        <Stack component="main" gap="md" p="md" style={{ flex: 1, minWidth: 0, maxWidth: 800 }}>
            <Title order={1}>{t("title")}</Title>

            <Stack gap="xs" maw={600}>
                <TextInput
                    label={t("bookMeetingUrlLabel")}
                    value={bookMeetingUrl}
                    onChange={(e) => setBookMeetingUrl(e.currentTarget.value)}
                    placeholder="https://calendly.com/..."
                />
                <Text size="xs" c="dimmed">{t("bookMeetingUrlDescription")}</Text>
            </Stack>

            <Button variant="gradient" onClick={handleSave} loading={loading} style={{ alignSelf: "flex-start" }}>
                {t("save")}
            </Button>

            <Divider my="md" />

            <HomepageGalleryManager />
        </Stack>
    );
}
