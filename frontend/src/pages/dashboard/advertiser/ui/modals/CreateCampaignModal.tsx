import React, { useState } from 'react';
import { Modal, TextInput, Button, Group, Box, Stack } from '@mantine/core';
import { useForm } from '@mantine/form';
import { AdCampaignRequestDTO } from '@/entities/ad-campaign';
import { useTranslations } from 'next-intl';

interface CreateCampaignModalProps {
    opened: boolean;
    onClose: () => void;
    onSuccess: (payload: AdCampaignRequestDTO) => Promise<void>;
}

export function CreateCampaignModal({ opened, onClose, onSuccess }: CreateCampaignModalProps) {
    const [submitting, setSubmitting] = useState(false);

    const t = useTranslations('adCampaigns.createCampaignModal');

    const form = useForm({
        initialValues: {
            name: '',
        },
        validate: {
            name: (value) => (value.trim().length < 2 ? t('validation.nameTooShort') : null),
        },
    });

    const handleSubmit = async (values: typeof form.values) => {
        setSubmitting(true);
        try {
            await onSuccess({ name: values.name.trim() } as AdCampaignRequestDTO);
            form.reset();
        } finally {
            setSubmitting(false);
        }
    };

    const handleClose = () => {
        form.reset();
        onClose();
    };

    return (
        <Modal opened={opened} onClose={handleClose} title={t('title')} centered closeOnClickOutside={!submitting} radius="lg" overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}>
            <Box>
                <form onSubmit={form.onSubmit(handleSubmit)}>
                    <Stack>
                        <TextInput label={t('labels.name')} placeholder={t('placeholders.name')} required {...form.getInputProps('name')} />

                        <Group justify="flex-end">
                            <Button variant="default" onClick={handleClose} disabled={submitting}>{t('buttons.cancel')}</Button>
                            <Button type="submit" variant="gradient" loading={submitting}>{t('buttons.create')}</Button>
                        </Group>
                    </Stack>
                </form>
            </Box>
        </Modal>
    );
}
