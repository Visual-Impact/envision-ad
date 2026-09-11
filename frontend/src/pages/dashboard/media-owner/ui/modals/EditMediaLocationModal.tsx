import React, { useRef, useState } from "react";
import { useForm } from "@mantine/form";
import { Modal, TextInput, Group, Button, Stack, Anchor, Text, Loader } from "@mantine/core";
import { useLocale, useTranslations } from "next-intl";
import { MediaLocation, MediaLocationRequestDTO, addressDetailsToLocationFields } from "@/entities/media-location";
import { updateMediaLocation } from "@/features/media-location-management";
import { notifications } from "@mantine/notifications";
import { AddressAutocomplete, PinDropMap } from "@/shared/ui";
import { AddressDetails, ReverseGeocode } from "@/shared/lib/geolocation";

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

const DEFAULT_MAP_CENTER = { lat: 45.516476848520064, lng: -73.52053208741675 };

export function EditMediaLocationModal({ opened, onClose, location, onSuccess }: EditMediaLocationModalProps) {
    const t = useTranslations("mediaLocations.modals.edit");
    const locale = useLocale();
    const [submitting, setSubmitting] = useState(false);
    const [showPinMap, setShowPinMap] = useState(false);
    const [resolvingPinAddress, setResolvingPinAddress] = useState(false);
    const [lastInitializedLocationId, setLastInitializedLocationId] = useState<string | null>(null);
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
            businessId: "",
            manualCoordinates: false,
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

    // Reset once the modal transitions to closed, and (re)populate once it transitions to
    // open on a new location. Adjusting state during render (rather than in an effect) for
    // a prop change is the pattern React recommends — see
    // https://react.dev/reference/react/useState#storing-information-from-previous-renders
    const [prevOpened, setPrevOpened] = useState(opened);
    if (opened !== prevOpened) {
        setPrevOpened(opened);
        if (!opened) {
            setLastInitializedLocationId(null);
            setShowPinMap(false);
            setResolvingPinAddress(false);
        }
    }

    if (opened && location && location.id !== lastInitializedLocationId) {
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
            manualCoordinates: false,
        });
        setLastInitializedLocationId(location.id);
    }

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

    const pinValue = form.values.latitude || form.values.longitude
        ? { lat: form.values.latitude, lng: form.values.longitude }
        : null;

    return (
        <Modal opened={opened} onClose={onClose} title={t('title')} size="lg" closeOnClickOutside={!submitting} radius="lg" overlayProps={{ backgroundOpacity: 0.55 }}>
            <form onSubmit={form.onSubmit(handleSubmit)} noValidate>
                <Stack>
                    <TextInput label={t('fields.name')} placeholder={t('placeholders.name')} {...form.getInputProps("name")} />

                    <AddressAutocomplete
                        label={t('fields.addressSearch')}
                        placeholder={t('placeholders.addressSearch')}
                        description={t('descriptions.addressSearch')}
                        noResultsText={t('addressSearch.noResults')}
                        language={locale}
                        onSelect={handleAddressSelect}
                    />

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
