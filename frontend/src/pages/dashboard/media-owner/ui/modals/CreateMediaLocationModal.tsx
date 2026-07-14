import React, { useState } from 'react';
import { Modal, TextInput, Button, Group, Stack } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useTranslations } from 'next-intl';
import { MediaLocationRequestDTO } from "@/entities/media-location/model/mediaLocation";
import { GetAddressDetails } from '@/shared/lib/geolocation/LocationService';

interface MediaLocationValidationErrorResponse {
    message?: string;
    fieldErrors?: Partial<Record<keyof MediaLocationRequestDTO, string>>;
}

const getValidationResponse = (error: unknown): MediaLocationValidationErrorResponse | null => {
    if (!error || typeof error !== "object") {
        return null;
    }
    if (!("response" in error)) {
        return null;
    }
    const response = (error as { response?: { data?: unknown } }).response;
    if (!response?.data || typeof response.data !== "object") {
        return null;
    }
    return response.data as MediaLocationValidationErrorResponse;
};

const mapServerFieldErrors = (
    fieldErrors: Partial<Record<keyof MediaLocationRequestDTO, string>>,
    t: ReturnType<typeof useTranslations>
): Partial<Record<keyof MediaLocationRequestDTO, string>> => {
    const mappedErrors: Partial<Record<keyof MediaLocationRequestDTO, string>> = {};
    if (fieldErrors.street) {
        mappedErrors.street = t("validation.streetInvalidServer");
    }
    if (fieldErrors.city) {
        mappedErrors.city = t("validation.cityInvalidServer");
    }
    if (fieldErrors.province) {
        mappedErrors.province = t("validation.provinceInvalidServer");
    }
    if (fieldErrors.country) {
        mappedErrors.country = t("validation.countryInvalidServer");
    }
    if (fieldErrors.postalCode) {
        mappedErrors.postalCode = t("validation.postalCodeInvalidServer");
    }
    return mappedErrors;
};

interface CreateMediaLocationModalProps {
    opened: boolean;
    onClose: () => void;
    onSuccess: (payload: MediaLocationRequestDTO) => Promise<void>;
}

export function CreateMediaLocationModal({ opened, onClose, onSuccess }: CreateMediaLocationModalProps) {
    const t = useTranslations('CreateMediaLocationModal');
    const [submitting, setSubmitting] = useState(false);
    const [fetchingCoords, setFetchingCoords] = useState(false);

    const form = useForm<MediaLocationRequestDTO>({
        initialValues: {
            name: "",

            street: "",
            city: "",
            province: "",
            country: "",
            postalCode: "",
            latitude: 0,
            longitude: 0,
        },
        validate: {
            name: (value) => (!value.trim() ? t('validation.nameRequired') : value.length < 2 ? t('validation.nameTooShort') : null),
            street: (value) => (!value.trim() ? t('validation.streetRequired') : null),
            city: (value) => (!value.trim() ? t('validation.cityRequired') : null),
            province: (value) => (!value.trim() ? t('validation.provinceRequired') : null),
            country: (value) => (!value.trim() ? t('validation.countryRequired') : null),
            postalCode: (value) => (!value.trim() ? t('validation.postalCodeRequired') : null),
        },
    });

    const handleClose = () => {
        setSubmitting(false);
        setFetchingCoords(false);
        form.reset();
        onClose();
    };

    const fetchCoordinates = async () => {
        const { street, city, province, country, postalCode } = form.values;
        if (!street.trim() || !city.trim() || !province.trim() || !country.trim() || !postalCode.trim()) return;

        setFetchingCoords(true);
        try {
            const query = `${street.trim()}, ${city.trim()}, ${province.trim()}, ${country.trim()}, ${postalCode.trim()}`;
            const details = await GetAddressDetails(query, 'en');

            if (details) {
                form.setFieldValue('latitude', parseFloat(details.lat.toString()));
                form.setFieldValue('longitude', parseFloat(details.lng.toString()));
            }
        } catch (error) {
            console.error("Failed to fetch coordinates", error);
        } finally {
            setFetchingCoords(false);
        }
    };

    const handleSubmit = async (values: MediaLocationRequestDTO) => {
        setSubmitting(true);
        try {
            // Ensure coordinates are fetched if 0,0 (though user should ideally verify)
            if (values.latitude === 0 && values.longitude === 0) {
                await fetchCoordinates();
            }
            // Re-read values in case fetchCoordinates updated them
            await onSuccess(form.values);
            handleClose();
        } catch (error) {
            const validationResponse = getValidationResponse(error);
            if (validationResponse?.fieldErrors && Object.keys(validationResponse.fieldErrors).length > 0) {
                form.setErrors(mapServerFieldErrors(validationResponse.fieldErrors, t));
            }
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Modal opened={opened} onClose={handleClose} title={t('title')} centered closeOnClickOutside={!submitting} radius="lg" overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}>
            <form onSubmit={form.onSubmit(handleSubmit)} noValidate>
                <Stack gap="md">
                    <TextInput
                        label={t('labels.name')}
                        placeholder={t('placeholders.name')}
                        required
                        {...form.getInputProps('name')}
                    />



                    <TextInput
                        label={t('labels.street')}
                        placeholder={t('placeholders.street')}
                        required
                        {...form.getInputProps('street')}
                    />

                    <Group grow>
                        <TextInput
                            label={t('labels.city')}
                            placeholder={t('placeholders.city')}
                            required
                            {...form.getInputProps('city')}
                        />
                        <TextInput
                            label={t('labels.province')}
                            placeholder={t('placeholders.province')}
                            required
                            {...form.getInputProps('province')}
                        />
                    </Group>

                    <Group grow>
                        <TextInput
                            label={t('labels.country')}
                            placeholder={t('placeholders.country')}
                            required
                            {...form.getInputProps('country')}
                        />
                        <TextInput
                            label={t('labels.postalCode')}
                            placeholder={t('placeholders.postalCode')}
                            required
                            {...form.getInputProps('postalCode')}
                        />
                    </Group>



                    <Group justify="flex-end" mt="md">
                        <Button type="button" variant="default" onClick={handleClose} disabled={submitting}>{t('buttons.cancel')}</Button>
                        <Button type="submit" variant="gradient" loading={submitting || fetchingCoords}>
                            {t('buttons.create')}
                        </Button>
                    </Group>
                </Stack>
            </form>
        </Modal>
    );
}
