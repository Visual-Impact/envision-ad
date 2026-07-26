"use client";

import {
    Alert,
    Button,
    ColorInput,
    Group,
    Modal,
    NumberInput,
    Select,
    Stack,
    Switch,
    Textarea,
    TextInput,
} from "@mantine/core";
import { IconAlertTriangle } from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { Bundle, BundleRequestDTO, BundleRuleType } from "@/entities/bundle";
import { Venue } from "@/entities/venue";

const RULE_TYPES: BundleRuleType[] = ["FULL_NETWORK", "CITY", "REGION", "VENUE"];

/**
 * Mirrors stripe.platform-fee-percent. Only drives an advisory warning — the
 * server does not block a larger discount, since a loss-leader may be intentional.
 */
const PLATFORM_FEE_PERCENT = 30;

interface BundleFormModalProps {
    opened: boolean;
    onClose: () => void;
    onSave: (data: BundleRequestDTO) => Promise<void>;
    bundle: Bundle | null;
    venues: Venue[];
}

export function BundleFormModal({ opened, onClose, onSave, bundle, venues }: BundleFormModalProps) {
    const t = useTranslations("bundleManagement.form");

    const [nameEn, setNameEn] = useState("");
    const [nameFr, setNameFr] = useState("");
    const [descriptionEn, setDescriptionEn] = useState("");
    const [descriptionFr, setDescriptionFr] = useState("");
    const [idealForEn, setIdealForEn] = useState("");
    const [idealForFr, setIdealForFr] = useState("");
    const [badgeColor, setBadgeColor] = useState("#3B82F6");
    const [ruleType, setRuleType] = useState<BundleRuleType>("FULL_NETWORK");
    const [ruleValue, setRuleValue] = useState("");
    const [active, setActive] = useState(true);
    const [discountPercent, setDiscountPercent] = useState<number | string>(0);
    const [saving, setSaving] = useState(false);
    const [errors, setErrors] = useState<{
        nameEn?: string;
        nameFr?: string;
        badgeColor?: string;
        ruleValue?: string;
    }>({});

    const isEdit = bundle !== null;
    const needsRuleValue = ruleType !== "FULL_NETWORK";

    // Reset the form from `bundle` each time the modal transitions to open.
    // Adjusting state during render (rather than in an effect) for a prop change is
    // the pattern React recommends — same approach as VenueFormModal.
    const [prevOpened, setPrevOpened] = useState(opened);
    if (opened !== prevOpened) {
        setPrevOpened(opened);
        if (opened) {
            setNameEn(bundle?.nameEn ?? "");
            setNameFr(bundle?.nameFr ?? "");
            setDescriptionEn(bundle?.descriptionEn ?? "");
            setDescriptionFr(bundle?.descriptionFr ?? "");
            setIdealForEn(bundle?.idealForEn ?? "");
            setIdealForFr(bundle?.idealForFr ?? "");
            setBadgeColor(bundle?.badgeColor ?? "#3B82F6");
            setRuleType(bundle?.ruleType ?? "FULL_NETWORK");
            setRuleValue(bundle?.ruleValue ?? "");
            setActive(bundle?.active ?? true);
            setDiscountPercent(bundle?.discountPercent ?? 0);
            setErrors({});
        }
    }

    const handleRuleTypeChange = (value: string | null) => {
        if (!value) return;
        const next = value as BundleRuleType;
        setRuleType(next);
        // A FULL_NETWORK bundle must carry no rule value — the DB CHECK constraint
        // rejects one — and a VENUE id is meaningless as a city/region string.
        setRuleValue("");
    };

    const validate = (): boolean => {
        const newErrors: typeof errors = {};
        if (!nameEn.trim()) newErrors.nameEn = t("required");
        if (!nameFr.trim()) newErrors.nameFr = t("required");
        if (!badgeColor || !/^#[0-9A-Fa-f]{6}$/.test(badgeColor)) newErrors.badgeColor = t("invalidColor");
        if (needsRuleValue && !ruleValue.trim()) newErrors.ruleValue = t("required");
        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = async () => {
        if (!validate()) return;
        setSaving(true);
        try {
            await onSave({
                nameEn: nameEn.trim(),
                nameFr: nameFr.trim(),
                descriptionEn: descriptionEn.trim() || undefined,
                descriptionFr: descriptionFr.trim() || undefined,
                idealForEn: idealForEn.trim() || undefined,
                idealForFr: idealForFr.trim() || undefined,
                badgeColor,
                ruleType,
                ruleValue: needsRuleValue ? ruleValue.trim() : null,
                active,
                discountPercent: Number(discountPercent) || 0,
            });
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            opened={opened}
            onClose={onClose}
            title={isEdit ? t("editTitle") : t("createTitle")}
            size="lg"
            centered
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}
        >
            <Stack gap="md">
                <Group grow>
                    <TextInput
                        label={t("nameEnLabel")}
                        placeholder={t("nameEnPlaceholder")}
                        value={nameEn}
                        onChange={(e) => setNameEn(e.currentTarget.value)}
                        error={errors.nameEn}
                        required
                    />
                    <TextInput
                        label={t("nameFrLabel")}
                        placeholder={t("nameFrPlaceholder")}
                        value={nameFr}
                        onChange={(e) => setNameFr(e.currentTarget.value)}
                        error={errors.nameFr}
                        required
                    />
                </Group>

                <Group grow align="flex-start">
                    <Textarea
                        label={t("descriptionEnLabel")}
                        placeholder={t("descriptionEnPlaceholder")}
                        value={descriptionEn}
                        onChange={(e) => setDescriptionEn(e.currentTarget.value)}
                        autosize
                        minRows={2}
                    />
                    <Textarea
                        label={t("descriptionFrLabel")}
                        placeholder={t("descriptionFrPlaceholder")}
                        value={descriptionFr}
                        onChange={(e) => setDescriptionFr(e.currentTarget.value)}
                        autosize
                        minRows={2}
                    />
                </Group>

                <Group grow>
                    <TextInput
                        label={t("idealForEnLabel")}
                        placeholder={t("idealForEnPlaceholder")}
                        value={idealForEn}
                        onChange={(e) => setIdealForEn(e.currentTarget.value)}
                        maxLength={500}
                    />
                    <TextInput
                        label={t("idealForFrLabel")}
                        placeholder={t("idealForFrPlaceholder")}
                        value={idealForFr}
                        onChange={(e) => setIdealForFr(e.currentTarget.value)}
                        maxLength={500}
                    />
                </Group>

                <Select
                    label={t("ruleTypeLabel")}
                    description={t("ruleTypeDescription")}
                    value={ruleType}
                    onChange={handleRuleTypeChange}
                    data={RULE_TYPES.map((type) => ({ value: type, label: t(`ruleTypes.${type}`) }))}
                    allowDeselect={false}
                    required
                />

                {needsRuleValue &&
                    (ruleType === "VENUE" ? (
                        <Select
                            label={t("venueLabel")}
                            placeholder={t("venuePlaceholder")}
                            value={ruleValue || null}
                            onChange={(value) => setRuleValue(value ?? "")}
                            data={venues.map((venue) => ({ value: venue.venueId, label: venue.nameEn }))}
                            error={errors.ruleValue}
                            searchable
                            required
                        />
                    ) : (
                        <TextInput
                            label={ruleType === "CITY" ? t("cityLabel") : t("regionLabel")}
                            placeholder={ruleType === "CITY" ? t("cityPlaceholder") : t("regionPlaceholder")}
                            description={t("ruleValueDescription")}
                            value={ruleValue}
                            onChange={(e) => setRuleValue(e.currentTarget.value)}
                            error={errors.ruleValue}
                            required
                        />
                    ))}

                <ColorInput
                    label={t("badgeColorLabel")}
                    value={badgeColor}
                    onChange={setBadgeColor}
                    error={errors.badgeColor}
                    format="hex"
                    required
                />

                <NumberInput
                    label={t("discountLabel")}
                    description={t("discountDescription")}
                    value={discountPercent}
                    onChange={setDiscountPercent}
                    min={0}
                    max={100}
                    clampBehavior="strict"
                    suffix="%"
                    allowDecimal={false}
                />

                {/* The platform absorbs the discount out of its own fee, so past the
                    fee percentage each subscription pays owners more than it collects. */}
                {Number(discountPercent) > PLATFORM_FEE_PERCENT && (
                    <Alert color="orange" icon={<IconAlertTriangle size={18} />}>
                        {t("discountAboveFeeWarning", { fee: PLATFORM_FEE_PERCENT })}
                    </Alert>
                )}

                <Switch
                    label={t("activeLabel")}
                    description={t("activeDescription")}
                    checked={active}
                    onChange={(e) => setActive(e.currentTarget.checked)}
                />

                <Group justify="flex-end" mt="md">
                    <Button variant="default" onClick={onClose}>
                        {t("cancel")}
                    </Button>
                    <Button variant="gradient" onClick={handleSubmit} loading={saving}>
                        {isEdit ? t("update") : t("create")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}
