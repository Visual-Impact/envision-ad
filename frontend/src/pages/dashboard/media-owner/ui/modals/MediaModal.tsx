"use client";

import {Button, Divider, Modal, Stepper, Text} from "@mantine/core";
import {useMemo, useState} from "react";
import {IconUpload} from "@tabler/icons-react";
import {CldUploadWidget, CloudinaryUploadWidgetResults} from "next-cloudinary";
import {notifications} from "@mantine/notifications";
import {MediaDetailsForm} from "./MediaDetailsForm";
import {ScheduleSelector} from "./ScheduleSelector";
import {ImageCornerSelector} from "../components/ImageCornerSelector";
import type {MediaFormState} from "@/pages/dashboard/media-owner/hooks/useMediaForm";
import {useTranslations} from "next-intl";

// Why do we need this?
interface MediaModalProps {
    opened: boolean;
    onClose: () => void;
    onSave: () => void;
    formState: MediaFormState;
    onFieldChange: <K extends keyof MediaFormState>(
        field: K,
        value: MediaFormState[K]
    ) => void;
    onDayTimeChange: (day: string, part: "start" | "end", value: string) => void;
    isEditing: boolean;
}

export function MediaModal({
                               opened,
                               onClose,
                               onSave,
                               formState,
                               onFieldChange,
                               onDayTimeChange,
                               isEditing,
                           }: MediaModalProps) {
    const t = useTranslations("mediaModal");

    if (!process.env.NEXT_PUBLIC_CLOUDINARY_UPLOAD_PRESET_SQUARE_IMAGES) {
        throw new Error('NEXT_PUBLIC_CLOUDINARY_UPLOAD_PRESET_SQUARE_IMAGES environment variable is not set');
    }

    const [active, setActive] = useState(0);

    // Cloudinary Widget Options
    const widgetOptions = {
        sources: ['local', 'url'] as ('local' | 'url')[],
        resourceType: 'image',
        multiple: false,
        maxFileSize: 10000000,
        cropping: true,
        showSkipCropButton: false, // Prevents users from bypassing the crop
        croppingAspectRatio: 1,
        croppingDefaultSelectionRatio: 1,
        singleUploadAutoClose: true,
        uploadPreset: process.env.NEXT_PUBLIC_CLOUDINARY_UPLOAD_PRESET_SQUARE_IMAGES
    };

    const handleUploadSuccess = (results: CloudinaryUploadWidgetResults) => {
        if (typeof results.info === 'object' && results.info.secure_url) {
            const secureUrl: string = results.info.secure_url;
            if (secureUrl.startsWith("https://res.cloudinary.com/")) {
                onFieldChange("imageUrl", secureUrl);
            }
        }
    };

    const initialCorners = useMemo(() =>
            formState.previewConfiguration
                ? JSON.parse(formState.previewConfiguration)
                : undefined
        , [formState.previewConfiguration]);

    // --- Per-step validation ------------------------------------------------

    // Validates the "Details" step. Returns field-level errors.
    const validateDetails = (): Record<string, string> => {
        const errors: Record<string, string> = {};

        if (!formState.mediaTitle.trim()) {
            errors["mediaTitle"] = t("errors.mediaTitleRequired");
        }

        if (!formState.displayType) {
            errors["displayType"] = t("errors.required");
        }

        if (formState.displayType === 'DIGITAL') {
            if (!formState.loopDuration) {
                errors["loopDuration"] = t("errors.loopDurationRequired");
            }
            if (!formState.resolution.trim() || !/^\d+x\d+$/.test(formState.resolution)) {
                errors["resolution"] = t("errors.resolutionRequired");
            }
        } else if (formState.displayType === 'POSTER') {
            if (!formState.widthCm) {
                errors["widthCm"] = t("errors.widthRequired");
            }
            if (!formState.heightCm) {
                errors["heightCm"] = t("errors.heightRequired");
            }
        }

        if (!formState.weeklyPrice) {
            errors["weeklyPrice"] = t("errors.priceRequired");
        }
        if (!formState.dailyImpressions) {
            errors["dailyImpressions"] = t("errors.impressionsRequired");
        }

        return errors;
    };

    // Validates the "Image" step. Uses notifications since these aren't
    // field-bound errors. Returns true when the step is valid.
    const validateImage = (): boolean => {
        if (!formState.imageUrl) {
            notifications.show({message: t("errors.mediaImageRequired"), color: "red"});
            return false;
        }
        if (!formState.previewConfiguration) {
            notifications.show({message: t("errors.previewCornersRequired"), color: "red"});
            return false;
        }
        return true;
    };

    // Validates the "Schedule" step. Returns field-level errors and surfaces
    // the "no active day" case via a notification.
    const validateSchedule = (): Record<string, string> | null => {
        const errors: Record<string, string> = {};

        const hasActiveDay = Object.values(formState.activeDaysOfWeek).some(v => v);
        if (!hasActiveDay) {
            notifications.show({message: t("errors.scheduleRequired"), color: "red"});
            return null;
        }

        for (const [day, isActive] of Object.entries(formState.activeDaysOfWeek)) {
            if (isActive) {
                const hours = formState.dailyOperatingHours[day] ?? { start: "", end: "" };
                const { start, end } = hours;
                const timePattern = /^\d{2}:\d{2}$/;
                if (!start) {
                    errors[`${day}_start`] = t("errors.required");
                } else if (!timePattern.test(start)) {
                    errors[`${day}_start`] = t("sections.timeFormatInvalid");
                }
                if (!end) {
                    errors[`${day}_end`] = t("errors.required");
                } else if (!timePattern.test(end)) {
                    errors[`${day}_end`] = t("sections.timeFormatInvalid");
                }
                // String comparison works for time in "HH:mm" format (e.g. "09:00" < "17:00")
                if (timePattern.test(start) && timePattern.test(end) && end <= start) {
                    errors[`${day}_end`] = t("errors.startTimeAfterEndTime");
                }
            }
        }

        return errors;
    };

    // --- Navigation ---------------------------------------------------------

    const handleNext = () => {
        if (active === 0) {
            const errors = validateDetails();
            if (Object.keys(errors).length > 0) {
                onFieldChange("errors", errors);
                return;
            }
            onFieldChange("errors", {});
        } else if (active === 1) {
            if (!validateImage()) {
                return;
            }
        }
        setActive((current) => Math.min(current + 1, 2));
    };

    const handleBack = () => {
        setActive((current) => Math.max(current - 1, 0));
    };

    const handleSave = () => {
        const errors = validateSchedule();
        if (errors === null) {
            return;
        }
        if (Object.keys(errors).length > 0) {
            onFieldChange("errors", errors);
            return;
        }
        onFieldChange("errors", {});
        onSave();
    };

    const handleClose = () => {
        setActive(0);
        onClose();
    };

    return (
        <Modal
            opened={opened}
            onClose={handleClose}
            title={isEditing ? t("title.update") : t("title.create")}
            size="xl"
            centered
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}
        >
            <div style={{paddingRight: 8, overflowX: 'hidden'}}>
                <Stepper
                    active={active}
                    // Allow jumping back to a completed step, but force forward
                    // moves through the "Next" button so each step is validated.
                    onStepClick={(step) => step < active && setActive(step)}
                    size="sm"
                    mb="lg"
                >
                    {/* STEP 1: DETAILS */}
                    <Stepper.Step
                        label={t("steps.details")}
                        description={t("steps.detailsDescription")}
                    >
                        <div style={{marginTop: 16}}>
                            <MediaDetailsForm
                                formState={formState}
                                onFieldChange={onFieldChange}
                            />
                        </div>
                    </Stepper.Step>

                    {/* STEP 2: IMAGE */}
                    <Stepper.Step
                        label={t("steps.image")}
                        description={t("steps.imageDescription")}
                    >
                        <div style={{marginTop: 16}}>
                            <Text size="md" fw={600} mb={4}>
                                {t("labels.mediaImage")} <span style={{color: "var(--mantine-color-red-6)"}}>*</span>
                            </Text>

                            {!formState.imageUrl ? (
                                <div>
                                    <div style={{
                                        border: '1px solid var(--mantine-color-gray-3)',
                                        borderRadius: '8px',
                                        padding: '8px',
                                        position: 'relative',
                                        backgroundColor: 'var(--mantine-color-gray-0)'
                                    }}>
                                        <CldUploadWidget
                                            signatureEndpoint="/api/cloudinary/sign-upload"
                                            onSuccess={handleUploadSuccess}
                                            options={widgetOptions}
                                        >
                                            {({open}) => (
                                                <div
                                                    style={{
                                                        border: '2px dashed var(--mantine-color-gray-4)',
                                                        borderRadius: '8px',
                                                        height: '300px',
                                                        display: 'flex',
                                                        flexDirection: 'column',
                                                        alignItems: 'center',
                                                        justifyContent: 'center',
                                                        cursor: 'pointer',
                                                        backgroundColor: 'var(--mantine-color-gray-0)',
                                                    }}
                                                    onClick={() => open()}
                                                >
                                                    <IconUpload size={40} color="var(--mantine-color-gray-5)"/>
                                                    <Text size="sm" c="dimmed" mt="sm">
                                                        {t("buttons.uploadFile")}
                                                    </Text>
                                                </div>
                                            )}
                                        </CldUploadWidget>
                                        <Text size="xs" ta="center" mt={4}>
                                            {t("warning.onlySquareImages")}
                                        </Text>
                                    </div>
                                </div>
                            ) : (
                                <div>
                                    <Text size="xs" c="dimmed" mb={4}>
                                        {t("labels.previewCorners")}
                                    </Text>

                                    <div style={{
                                        border: '1px solid var(--mantine-color-gray-3)',
                                        borderRadius: '8px',
                                        padding: '8px',
                                        position: 'relative',
                                        backgroundColor: 'var(--mantine-color-gray-0)',
                                        maxWidth: 420,
                                        margin: '0 auto'
                                    }}>
                                        <ImageCornerSelector
                                            imageUrl={formState.imageUrl}
                                            initialCorners={initialCorners}
                                            onChange={(corners) => {
                                                const config = JSON.stringify(corners);
                                                if (config !== formState.previewConfiguration) {
                                                    onFieldChange("previewConfiguration", config);
                                                }
                                            }}
                                        />

                                        <CldUploadWidget
                                            signatureEndpoint="/api/cloudinary/sign-upload"
                                            onSuccess={handleUploadSuccess}
                                            options={widgetOptions}
                                        >
                                            {({open}) => (
                                                <Button
                                                    size="xs"
                                                    variant="default"
                                                    style={{marginTop: 12, width: '100%'}}
                                                    onClick={() => open()}
                                                >
                                                    {t("buttons.changeFile")}
                                                </Button>
                                            )}
                                        </CldUploadWidget>

                                        <Text size="xs" c="dimmed" ta="center" mt={4}>
                                            {t("labels.dragCorners")}
                                        </Text>
                                    </div>
                                </div>
                            )}
                        </div>
                    </Stepper.Step>

                    {/* STEP 3: SCHEDULE */}
                    <Stepper.Step
                        label={t("steps.schedule")}
                        description={t("steps.scheduleDescription")}
                    >
                        <div style={{marginTop: 16}}>
                            <ScheduleSelector
                                formState={formState}
                                onFieldChange={onFieldChange}
                                onDayTimeChange={onDayTimeChange}
                            />
                        </div>
                    </Stepper.Step>
                </Stepper>
            </div>

            <Divider my="md"/>

            <div
                style={{
                    display: "flex",
                    justifyContent: "space-between",
                    marginTop: 12,
                }}
            >
                <Button variant="default" onClick={handleClose}>
                    {t("buttons.cancel")}
                </Button>

                <div style={{display: "flex", gap: 8}}>
                    {active > 0 && (
                        <Button variant="default" onClick={handleBack}>
                            {t("buttons.back")}
                        </Button>
                    )}
                    {active < 2 ? (
                        <Button variant="gradient" onClick={handleNext}>{t("buttons.next")}</Button>
                    ) : (
                        <Button variant="gradient" onClick={handleSave}>{t("buttons.save")}</Button>
                    )}
                </div>
            </div>
        </Modal>
    );
}
