import React, { useState } from 'react';
import { Modal, TextInput, Button, Group, Box, Text, Stack, ThemeIcon, Alert, Badge } from '@mantine/core';
import { useForm } from '@mantine/form';
import { IconUpload, IconCheck, IconAlertCircle } from '@tabler/icons-react';
import { AdRequestDTO } from "@/entities/ad";
import { VenueMultiSelectPicker } from "@/features/venue-management";
import { CldUploadWidget, CloudinaryUploadWidgetResults } from 'next-cloudinary';
import { useTranslations } from 'next-intl'

const MAX_VIDEO_DURATION_SECONDS = 30;

interface AddAdModalProps {
    opened: boolean;
    onClose: () => void;
    onSuccess: (payload: AdRequestDTO) => Promise<void>;
}

export function AddAdModal({ opened, onClose, onSuccess }: AddAdModalProps) {
    const t = useTranslations('AddAdModal');
    const [submitting, setSubmitting] = useState(false);

    // Cloudinary State
    const [uploadedFileUrl, setUploadedFileUrl] = useState<string | null>(null);
    const [videoTooLong, setVideoTooLong] = useState(false);

    // Venue tags — empty is valid and is the common case (universal creative).
    const [venueIds, setVenueIds] = useState<string[]>([]);

    const form = useForm({
        initialValues: {
            name: "",
            adType: "IMAGE",
        },
        validate: {
            name: (value) => (value.length < 2 ? t('validation.nameTooShort') : null),
        },
    });

    // --- Reset Logic ---
    const handleReset = () => {
        form.reset();
        setUploadedFileUrl(null);
        setVideoTooLong(false);
        setVenueIds([]);
    };

    const handleClose = () => {
        handleReset();
        onClose();
    };

    // --- Cloudinary Success Callback ---
    const handleUploadSuccess = (results: CloudinaryUploadWidgetResults) => {
        if (typeof results.info === 'object' && results.info.secure_url) {
            const type = results.info.resource_type === 'video' ? 'VIDEO' : 'IMAGE';
            form.setFieldValue('adType', type);

            if (type === 'VIDEO' && results?.info?.duration != null) {
                const durationNum = Number(results.info.duration);
                if (Number.isFinite(durationNum) && durationNum > MAX_VIDEO_DURATION_SECONDS) {
                    setVideoTooLong(true);
                    setUploadedFileUrl(null);
                    return;
                }
            }

            setVideoTooLong(false);
            setUploadedFileUrl(results.info.secure_url);
        }
    };

    // --- Submit Handler ---
    const handleSubmit = async (values: typeof form.values) => {
        if (!uploadedFileUrl) return;

        setSubmitting(true);
        try {
            await onSuccess({
                name: values.name,
                adType: values.adType as "IMAGE" | "VIDEO",
                adUrl: uploadedFileUrl,
                venueIds,
            });

            // Only runs if no error was thrown
            handleReset();
        } catch {
            // Do nothing.
            // Parent already handled notifications.
            // Form remains intact.
        } finally {
            setSubmitting(false);
        }
    };

    const widgetOptions = {
        sources: ['local', 'url'] as ('local' | 'url')[],
        resourceType: 'auto',
        multiple: false,
        maxFileSize: 50000000,
        folder: 'envisionad/ads',
    };

    return (
        <Modal opened={opened} onClose={handleClose} title={t('title')} centered closeOnClickOutside={!submitting} radius="lg" overlayProps={{ backgroundOpacity: 0.55 }}>
            <Box pos="relative">
                <form onSubmit={form.onSubmit(handleSubmit)}>
                    <Stack gap="md">
                        <TextInput
                            label={t('labels.name')}
                            placeholder={t('placeholders.name')}
                            required
                            {...form.getInputProps('name')}
                        />

                        <Box>
                            <Text size="sm" fw={500} mb={6}>
                                {t('labels.venueTags')}
                            </Text>
                            <VenueMultiSelectPicker
                                selectedVenueIds={venueIds}
                                onChange={setVenueIds}
                                helperText={t('venueTags.helper')}
                                emptyText={t('venueTags.noVenues')}
                            />
                        </Box>

                        <Box>
                            <Text size="sm" fw={500} mb={4}>
                                {t('labels.media')} <span style={{ color: 'var(--mantine-color-red-text)' }}>*</span>
                            </Text>

                            <CldUploadWidget
                                signatureEndpoint="/api/cloudinary/sign-upload"
                                onSuccess={handleUploadSuccess}
                                options={widgetOptions}
                            >
                                {({ open }) => (
                                    <Group>
                                        <Button
                                            variant={uploadedFileUrl ? "default" : "filled"}
                                            leftSection={<IconUpload size={14} />}
                                            onClick={() => open()}
                                            disabled={submitting}
                                        >
                                            {uploadedFileUrl ? t('buttons.changeFile') : t('buttons.uploadFile')}
                                        </Button>

                                        {uploadedFileUrl && (
                                            <Group gap="xs">
                                                <ThemeIcon color="green" size="sm" variant="light">
                                                    <IconCheck size={12} />
                                                </ThemeIcon>
                                                <Text size="sm" c="green">
                                                    {form.values.adType === 'VIDEO' ? t('status.videoUploaded') : t('status.imageUploaded')}
                                                </Text>
                                            </Group>
                                        )}
                                    </Group>
                                )}
                            </CldUploadWidget>
                            {!uploadedFileUrl && form.isTouched() && !videoTooLong && (
                                <Text c="red" size="xs" mt={4}>{t('validation.fileRequired')}</Text>
                            )}

                            {videoTooLong && (
                                <Alert
                                    variant="light"
                                    color="red"
                                    icon={<IconAlertCircle />}
                                    mt="sm"
                                >
                                    {t('validation.videoTooLong', { max: MAX_VIDEO_DURATION_SECONDS })}
                                </Alert>
                            )}

                            {/* Show detected media type under the upload widget as a non-interactive Badge */}
                            <Box mt="sm">
                                <Text size="sm" fw={500} mb={4}>{t('labels.type')}</Text>
                                <Group align="center">
                                    <Badge color={form.values.adType === 'VIDEO' ? 'blue' : 'gray'} variant="filled">
                                        {form.values.adType === 'VIDEO' ? t('type.video') : t('type.image')}
                                    </Badge>
                                    <Text size="xs" color="dimmed">{t('description.autoDetect')}</Text>
                                </Group>
                            </Box>
                        </Box>

                        <Group justify="flex-end" mt="md">
                            <Button variant="default" onClick={handleClose} disabled={submitting}>{t('buttons.cancel')}</Button>
                            <Button type="submit" variant="gradient" loading={submitting} disabled={!uploadedFileUrl}>
                                {t('buttons.createAd')}
                            </Button>
                        </Group>
                    </Stack>
                </form>
            </Box>
        </Modal>
    );
}
