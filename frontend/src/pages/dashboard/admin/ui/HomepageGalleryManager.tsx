"use client";

import { useEffect, useState } from "react";
import {
    Stack, Group, Title, Text, Image, ActionIcon, Loader,
    Center, Alert, Box,
} from "@mantine/core";
import {
    IconUpload, IconTrash, IconChevronLeft, IconChevronRight, IconInfoCircle,
} from "@tabler/icons-react";
import { CldUploadWidget, CloudinaryUploadWidgetResults } from "next-cloudinary";
import { notifications } from "@mantine/notifications";
import { useTranslations } from "next-intl";
import {
    getGalleryImages, saveGalleryImages, deleteGalleryImageAsset, applyGalleryCrop, type GalleryImage,
} from "@/features/app-settings/api";

// Signed upload into a dedicated folder so the delete endpoint can safely scope
// destructive calls to gallery assets only. 3:4 crop matches the homepage card.
const widgetOptions = {
    sources: ["local", "url"] as ("local" | "url")[],
    resourceType: "image",
    multiple: false,
    maxFileSize: 10_000_000,
    folder: "homepage-gallery",
    cropping: true,
    croppingAspectRatio: 3 / 4,
    croppingShowDimensions: true,
    showSkipCropButton: false,
    singleUploadAutoClose: true,
};

export function HomepageGalleryManager() {
    const t = useTranslations("admin.settingsPage.gallery");

    const [images, setImages] = useState<GalleryImage[]>([]);
    const [loading, setLoading] = useState(true);
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        getGalleryImages()
            .then(setImages)
            .catch(() => notifications.show({ title: t("loadFailed"), message: "", color: "red" }))
            .finally(() => setLoading(false));
    }, [t]);

    // Persist a new ordering/list, rolling back local state if the save fails so
    // the UI never drifts from what the homepage will actually render.
    const persist = async (next: GalleryImage[], previous: GalleryImage[]) => {
        setBusy(true);
        setImages(next);
        try {
            await saveGalleryImages(next);
            return true;
        } catch {
            setImages(previous);
            notifications.show({ title: t("saveFailed"), message: "", color: "red" });
            return false;
        } finally {
            setBusy(false);
        }
    };

    const handleUploadSuccess = async (results: CloudinaryUploadWidgetResults) => {
        if (typeof results.info !== "object") return;
        const info = results.info as {
            secure_url?: string;
            public_id?: string;
            coordinates?: { custom?: number[][] } | null;
        };
        const { secure_url: rawUrl, public_id: publicId } = info;
        if (!rawUrl || !publicId || !rawUrl.startsWith("https://res.cloudinary.com/")) return;

        // Signed uploads don't crop the master, so honour the widget crop by baking
        // the selected region into the delivery URL.
        const url = applyGalleryCrop(rawUrl, info.coordinates?.custom?.[0]);

        const previous = images;
        const ok = await persist([...previous, { url, publicId }], previous);
        if (ok) notifications.show({ title: t("added"), message: "", color: "green" });
    };

    const handleRemove = async (index: number) => {
        const previous = images;
        const target = previous[index];
        const next = previous.filter((_, i) => i !== index);
        setBusy(true);
        try {
            // 1. Destroy the Cloudinary asset first, while it is still in the
            //    persisted gallery — the endpoint verifies membership against it.
            await deleteGalleryImageAsset(target.publicId);
            // 2. Only then drop it from the setting.
            await saveGalleryImages(next);
            setImages(next);
            notifications.show({ title: t("removed"), message: "", color: "green" });
        } catch {
            // Leave the image in place so the admin can retry (destroy is
            // idempotent — a second attempt is safe).
            notifications.show({ title: t("removeFailed"), message: "", color: "red" });
        } finally {
            setBusy(false);
        }
    };

    const move = async (index: number, dir: -1 | 1) => {
        const target = index + dir;
        if (target < 0 || target >= images.length) return;
        const next = [...images];
        [next[index], next[target]] = [next[target], next[index]];
        await persist(next, images);
    };

    return (
        <Stack gap="sm">
            <div>
                <Title order={3}>{t("title")}</Title>
                <Text size="xs" c="dimmed">{t("description")}</Text>
            </div>

            {loading ? (
                <Center h={160}><Loader size="sm" /></Center>
            ) : (
                <>
                    {images.length === 0 && (
                        <Alert icon={<IconInfoCircle size={16} />} color="blue" variant="light">
                            {t("emptyDefaults")}
                        </Alert>
                    )}

                    <Group gap="md" align="flex-start">
                        {images.map((img, index) => (
                            <Box key={img.publicId} w={150}>
                                <Box style={{ position: "relative", borderRadius: 12, overflow: "hidden" }}>
                                    <Image
                                        src={img.url}
                                        alt={t("imageAlt", { index: index + 1 })}
                                        w={150}
                                        h={200}
                                        fit="cover"
                                    />
                                    <ActionIcon
                                        color="red"
                                        variant="filled"
                                        size="sm"
                                        radius="xl"
                                        disabled={busy}
                                        onClick={() => handleRemove(index)}
                                        aria-label={t("remove")}
                                        style={{ position: "absolute", top: 6, right: 6 }}
                                    >
                                        <IconTrash size={14} />
                                    </ActionIcon>
                                </Box>
                                <Group justify="center" gap={6} mt={6}>
                                    <ActionIcon
                                        variant="default" size="sm" radius="xl"
                                        disabled={busy || index === 0}
                                        onClick={() => move(index, -1)}
                                        aria-label={t("moveLeft")}
                                    >
                                        <IconChevronLeft size={14} />
                                    </ActionIcon>
                                    <Text size="xs" c="dimmed">{index + 1}</Text>
                                    <ActionIcon
                                        variant="default" size="sm" radius="xl"
                                        disabled={busy || index === images.length - 1}
                                        onClick={() => move(index, 1)}
                                        aria-label={t("moveRight")}
                                    >
                                        <IconChevronRight size={14} />
                                    </ActionIcon>
                                </Group>
                            </Box>
                        ))}

                        <CldUploadWidget
                            signatureEndpoint="/api/cloudinary/sign-upload"
                            onSuccess={handleUploadSuccess}
                            options={widgetOptions}
                        >
                            {({ open }) => (
                                <Box
                                    w={150} h={200}
                                    onClick={() => !busy && open()}
                                    style={{
                                        border: "2px dashed var(--mantine-color-gray-4)",
                                        borderRadius: 12,
                                        display: "flex",
                                        flexDirection: "column",
                                        alignItems: "center",
                                        justifyContent: "center",
                                        cursor: busy ? "not-allowed" : "pointer",
                                        backgroundColor: "var(--mantine-color-gray-0)",
                                        opacity: busy ? 0.6 : 1,
                                    }}
                                >
                                    <IconUpload size={28} color="var(--mantine-color-gray-5)" />
                                    <Text size="xs" c="dimmed" mt="xs" ta="center" px="xs">
                                        {t("addImage")}
                                    </Text>
                                </Box>
                            )}
                        </CldUploadWidget>
                    </Group>
                </>
            )}
        </Stack>
    );
}
