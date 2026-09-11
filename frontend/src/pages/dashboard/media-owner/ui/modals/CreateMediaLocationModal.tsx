import React, { useRef, useState } from 'react';
import { Modal, TextInput, Button, Group, Stack, Anchor, Text, Loader } from '@mantine/core';
import { useForm } from '@mantine/form';
import { useLocale, useTranslations } from 'next-intl';
import { notifications } from '@mantine/notifications';
import { MediaLocationRequestDTO, addressDetailsToLocationFields } from "@/entities/media-location";
import { AddressAutocomplete, PinDropMap } from '@/shared/ui';
import { AddressDetails, ReverseGeocode } from '@/shared/lib/geolocation';

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

const DEFAULT_MAP_CENTER = { lat: 45.516476848520064, lng: -73.52053208741675 };

export function CreateMediaLocationModal({ opened, onClose, onSuccess }: CreateMediaLocationModalProps) {
    const t = useTranslations('CreateMediaLocationModal');
    const locale = useLocale();
    const [submitting, setSubmitting] = useState(false);
    const [showPinMap, setShowPinMap] = useState(false);
    const [resolvingPinAddress, setResolvingPinAddress] = useState(false);
    const pinRequestSeq = useRef(0);

    const form = useForm<MediaLocationRequestDTO>({
        initialValues: {
            name: "",

            street: "",
            city: "",
            province: "",
            region: "",
            country: "",
            postalCode: "",
            latitude: 0,
            longitude: 0,
            manualCoordinates: false,
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
        setShowPinMap(false);
        setResolvingPinAddress(false);
        form.reset();
        onClose();
    };

    const handleAddressSelect = (details: AddressDetails) => {
        const fields = addressDetailsToLocationFields(details);
        form.setValues({ ...fields, manualCoordinates: false });
        form.clearErrors();
    };

    const handlePinChange = async (position: { lat: number; lng: number }) => {
        form.setFieldValue('latitude', position.lat);
        form.setFieldValue('longitude', position.lng);
        form.setFieldValue('manualCoordinates', true);

        const requestId = ++pinRequestSeq.current;
        setResolvingPinAddress(true);
        try {
            const details = await ReverseGeocode(position.lat, position.lng, locale);
            if (requestId !== pinRequestSeq.current) return; // a newer pin drop superseded this lookup
            if (details) {
                const fields = addressDetailsToLocationFields(details);
                form.setValues({ ...fields, latitude: position.lat, longitude: position.lng, manualCoordinates: true });
                form.clearErrors();
            }
        } finally {
            if (requestId === pinRequestSeq.current) setResolvingPinAddress(false);
        }
    };

    const handleSubmit = async (values: MediaLocationRequestDTO) => {
        setSubmitting(true);
        try {
            await onSuccess(values);
            handleClose();
        } catch (error) {
            const validationResponse = getValidationResponse(error);
            const fieldErrors = validationResponse?.fieldErrors;
            const hasFieldErrors = !!fieldErrors && Object.keys(fieldErrors).length > 0;
            if (hasFieldErrors) {
                form.setErrors(mapServerFieldErrors(fieldErrors, t));
            }
            notifications.show({
                title: t('error.title'),
                message: validationResponse?.message || t('error.message'),
                color: "red",
            });
        } finally {
            setSubmitting(false);
        }
    };

    const pinValue = form.values.latitude || form.values.longitude
        ? { lat: form.values.latitude, lng: form.values.longitude }
        : null;

    return (
        <Modal opened={opened} onClose={handleClose} title={t('title')} centered closeOnClickOutside={!submitting} radius="lg" overlayProps={{ backgroundOpacity: 0.55 }}>
            <form onSubmit={form.onSubmit(handleSubmit)} noValidate>
                <Stack gap="md">
                    <TextInput
                        label={t('labels.name')}
                        placeholder={t('placeholders.name')}
                        required
                        {...form.getInputProps('name')}
                    />

                    <AddressAutocomplete
                        label={t('labels.addressSearch')}
                        placeholder={t('placeholders.addressSearch')}
                        description={t('descriptions.addressSearch')}
                        noResultsText={t('addressSearch.noResults')}
                        language={locale}
                        onSelect={handleAddressSelect}
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

                    <TextInput
                        label={t('labels.region')}
                        placeholder={t('placeholders.region')}
                        description={t('descriptions.region')}
                        {...form.getInputProps('region')}
                    />

                    <div>
                        <Anchor component="button" type="button" size="sm" onClick={() => setShowPinMap((prev) => !prev)}>
                            {showPinMap ? t('buttons.hidePin') : t('buttons.dropPin')}
                        </Anchor>
                        {showPinMap && (
                            <Stack gap="xs" mt="xs">
                                <Group gap="xs">
                                    <Text size="xs" c="dimmed">{t('descriptions.pinMap')}</Text>
                                    {resolvingPinAddress && (
                                        <Group gap={4}>
                                            <Loader size="xs" />
                                            <Text size="xs" c="dimmed">{t('descriptions.resolvingPin')}</Text>
                                        </Group>
                                    )}
                                </Group>
                                <PinDropMap center={DEFAULT_MAP_CENTER} value={pinValue} onChange={handlePinChange} />
                            </Stack>
                        )}
                    </div>

                    <Group justify="flex-end" mt="md">
                        <Button type="button" variant="default" onClick={handleClose} disabled={submitting}>{t('buttons.cancel')}</Button>
                        <Button type="submit" variant="gradient" loading={submitting}>
                            {t('buttons.create')}
                        </Button>
                    </Group>
                </Stack>
            </form>
        </Modal>
    );
}
