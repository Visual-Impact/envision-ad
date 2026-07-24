import React, { useEffect, useRef, useState } from "react";
import { useForm } from "@mantine/form";
import { Modal, TextInput, Group, Button, Stack } from "@mantine/core";
import { useTranslations } from "next-intl";
import { MediaLocation, MediaLocationRequestDTO } from "@/entities/media-location/model/mediaLocation";
import { updateMediaLocation } from "@/features/media-location-management/api";
import { notifications } from "@mantine/notifications";

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

interface EditMediaLocationModalProps {
    opened: boolean;
    onClose: () => void;
    location: MediaLocation | null;
    onSuccess: () => void;
}

export function EditMediaLocationModal({ opened, onClose, location, onSuccess }: EditMediaLocationModalProps) {
    const t = useTranslations("mediaLocations.modals.edit");
    const [submitting, setSubmitting] = useState(false);
    const lastInitializedLocationId = useRef<string | null>(null);

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
            businessId: "",
        },
        validate: {
            name: (value) => (!value.trim() ? t('validation.nameRequired') : value.length < 2 ? t('validation.name') : null),
            street: (value) => (!value.trim() ? t('validation.streetRequired') : null),
            city: (value) => (!value.trim() ? t('validation.cityRequired') : null),
            province: (value) => (!value.trim() ? t('validation.provinceRequired') : null),
            country: (value) => (!value.trim() ? t('validation.countryRequired') : null),
            postalCode: (value) => (!value.trim() ? t('validation.postalCodeRequired') : null),
            latitude: (value) => (value < -90 || value > 90 ? t('validation.latitude') : null),
            longitude: (value) => (value < -180 || value > 180 ? t('validation.longitude') : null),
        },
    });

    useEffect(() => {
        if (opened && location && location.id !== lastInitializedLocationId.current) {
            form.setValues({
                name: location.name,
                street: location.street,
                city: location.city,
                province: location.province,
                region: location.region ?? "",
                country: location.country,
                postalCode: location.postalCode,
                latitude: location.latitude,
                longitude: location.longitude,
                businessId: location.businessId,
            });
            lastInitializedLocationId.current = location.id;
        }
        if (!opened) {
            lastInitializedLocationId.current = null;
        }
    }, [opened, location, form]);

    const handleSubmit = async (values: MediaLocationRequestDTO) => {
        if (!location) return;

        setSubmitting(true);
        try {
            await updateMediaLocation(location.id, values);
            notifications.show({
                title: t('success.title'),
                message: t('success.message'),
                color: "green",
            });
            onSuccess();
            onClose();
        } catch (error) {
            console.error(error);
            const validationResponse = getValidationResponse(error);
            const fieldErrors = validationResponse?.fieldErrors;
            const hasFieldErrors = !!fieldErrors && Object.keys(fieldErrors).length > 0;
            if (hasFieldErrors) {
                form.setErrors(mapServerFieldErrors(fieldErrors, t));
            }
            notifications.show({
                title: t('error.title'),
                message: validationResponse?.message || (hasFieldErrors ? t('error.addressGuidance') : t('error.message')),
                color: "red",
            });
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <Modal opened={opened} onClose={onClose} title={t('title')} size="lg" closeOnClickOutside={!submitting} radius="lg" overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}>
            <form onSubmit={form.onSubmit(handleSubmit)} noValidate>
                <Stack>
                    <TextInput label={t('fields.name')} placeholder={t('placeholders.name')} {...form.getInputProps("name")} />
                    <TextInput label={t('fields.street')} placeholder={t('placeholders.street')} {...form.getInputProps("street")} />
                    <Group grow>
                        <TextInput label={t('fields.city')} placeholder={t('placeholders.city')} {...form.getInputProps("city")} />
                        <TextInput label={t('fields.province')} placeholder={t('placeholders.province')} {...form.getInputProps("province")} />
                    </Group>
                    <Group grow>
                        <TextInput label={t('fields.postalCode')} placeholder={t('placeholders.postalCode')} {...form.getInputProps("postalCode")} />
                        <TextInput label={t('fields.country')} placeholder={t('placeholders.country')} {...form.getInputProps("country")} />
                    </Group>
                    <TextInput
                        label={t('fields.region')}
                        placeholder={t('placeholders.region')}
                        description={t('descriptions.region')}
                        {...form.getInputProps("region")}
                    />

                    <Group justify="flex-end" mt="md">
                        <Button type="button" variant="default" onClick={onClose} disabled={submitting}>
                            {t('buttons.cancel')}
                        </Button>
                        <Button type="submit" variant="gradient" loading={submitting}>{t('buttons.save')}</Button>
                    </Group>
                </Stack>
            </form>
        </Modal>
    );
}
