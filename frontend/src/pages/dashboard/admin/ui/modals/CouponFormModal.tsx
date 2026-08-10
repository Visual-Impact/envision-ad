"use client";

import { Alert, Button, Group, Modal, NumberInput, SegmentedControl, Select, Stack, TextInput } from "@mantine/core";
import { DatePickerInput } from "@mantine/dates";
import { IconInfoCircle } from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { CouponDuration, CouponRequestDTO, DiscountType } from "@/entities/coupon";

interface CouponFormModalProps {
    opened: boolean;
    onClose: () => void;
    onSave: (data: CouponRequestDTO) => Promise<void>;
}

/**
 * Create-only — there is no edit mode. Once created, a coupon's discount shape is
 * immutable at Stripe's own API level, and so is expiresAt/maxRedemptions (verified
 * against the installed SDK, not just assumed — see P3-PROGRESS.md D5). The only
 * thing genuinely editable afterward is `active`, which the table's inline Switch
 * already covers — a modal with every field disabled except one toggle would just be
 * a worse version of that switch.
 */
export function CouponFormModal({ opened, onClose, onSave }: CouponFormModalProps) {
    const t = useTranslations("couponManagement.form");
    const [code, setCode] = useState("");
    const [discountType, setDiscountType] = useState<DiscountType>("PERCENT");
    const [percentOff, setPercentOff] = useState<number | "">("");
    const [amountOff, setAmountOff] = useState<number | "">("");
    const [duration, setDuration] = useState<CouponDuration>("ONCE");
    const [durationInMonths, setDurationInMonths] = useState<number | "">(2);
    // Mantine 8's DatePickerInput onChange gives a plain "YYYY-MM-DD" string
    // (DateStringValue), not a Date object, despite what its type once implied —
    // confirmed via node_modules/@mantine/dates' actual .d.ts, not assumed.
    const [expiresAt, setExpiresAt] = useState<string | null>(null);
    const [maxRedemptions, setMaxRedemptions] = useState<number | "">("");
    const [saving, setSaving] = useState(false);
    const [errors, setErrors] = useState<Record<string, string>>({});

    const [prevOpened, setPrevOpened] = useState(opened);
    if (opened !== prevOpened) {
        setPrevOpened(opened);
        if (opened) {
            setCode("");
            setDiscountType("PERCENT");
            setPercentOff("");
            setAmountOff("");
            setDuration("ONCE");
            setDurationInMonths(2);
            setExpiresAt(null);
            setMaxRedemptions("");
            setErrors({});
        }
    }

    const validate = (): boolean => {
        const newErrors: Record<string, string> = {};
        const normalizedCode = code.trim().toUpperCase();
        if (normalizedCode.length < 3 || normalizedCode.length > 40 || !/^[A-Z0-9_-]+$/.test(normalizedCode)) {
            newErrors.code = t("codeInvalid");
        }
        if (discountType === "PERCENT" && (percentOff === "" || percentOff < 1 || percentOff > 100)) {
            newErrors.percentOff = t("percentOffInvalid");
        }
        if (discountType === "FIXED_AMOUNT" && (amountOff === "" || amountOff <= 0)) {
            newErrors.amountOff = t("amountOffInvalid");
        }
        if (duration === "REPEATING" && (durationInMonths === "" || durationInMonths < 2 || durationInMonths > 12)) {
            newErrors.durationInMonths = t("durationInMonthsInvalid");
        }
        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = async () => {
        if (!validate()) return;
        setSaving(true);
        try {
            const data: CouponRequestDTO = {
                code: code.trim().toUpperCase(),
                discountType,
                duration,
                ...(discountType === "PERCENT" && { percentOff: percentOff as number }),
                ...(discountType === "FIXED_AMOUNT" && { amountOffCents: Math.round((amountOff as number) * 100) }),
                ...(duration === "REPEATING" && { durationInMonths: durationInMonths as number }),
                // expiresAt is a bare "YYYY-MM-DD" string; the backend's LocalDateTime
                // needs a full ISO-8601 datetime, so treat the code as expiring at the
                // end of the selected day.
                ...(expiresAt && { expiresAt: `${expiresAt}T23:59:59` }),
                ...(maxRedemptions !== "" && { maxRedemptions: maxRedemptions as number }),
            };
            await onSave(data);
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            opened={opened}
            onClose={onClose}
            title={t("createTitle")}
            size="md"
            centered
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55 }}
        >
            <Stack gap="md">
                <TextInput
                    label={t("codeLabel")}
                    placeholder={t("codePlaceholder")}
                    value={code}
                    onChange={(e) => setCode(e.currentTarget.value.toUpperCase())}
                    error={errors.code}
                    maxLength={40}
                    required
                />

                <SegmentedControl
                    value={discountType}
                    onChange={(value) => setDiscountType(value as DiscountType)}
                    data={[
                        { label: t("discountTypePercent"), value: "PERCENT" },
                        { label: t("discountTypeFixed"), value: "FIXED_AMOUNT" },
                    ]}
                />

                {discountType === "PERCENT" ? (
                    <NumberInput
                        label={t("percentOffLabel")}
                        value={percentOff}
                        onChange={(v) => setPercentOff(v === "" ? "" : Number(v))}
                        error={errors.percentOff}
                        min={1}
                        max={100}
                        suffix="%"
                        required
                    />
                ) : (
                    <NumberInput
                        label={t("amountOffLabel")}
                        value={amountOff}
                        onChange={(v) => setAmountOff(v === "" ? "" : Number(v))}
                        error={errors.amountOff}
                        min={0.01}
                        decimalScale={2}
                        prefix="$"
                        required
                    />
                )}

                <Select
                    label={t("durationLabel")}
                    value={duration}
                    onChange={(value) => setDuration((value ?? "ONCE") as CouponDuration)}
                    data={[
                        { label: t("durationOnce"), value: "ONCE" },
                        { label: t("durationRepeating"), value: "REPEATING" },
                        { label: t("durationForever"), value: "FOREVER" },
                    ]}
                    allowDeselect={false}
                />

                {duration === "REPEATING" && (
                    <NumberInput
                        label={t("durationInMonthsLabel")}
                        value={durationInMonths}
                        onChange={(v) => setDurationInMonths(v === "" ? "" : Number(v))}
                        error={errors.durationInMonths}
                        min={2}
                        max={12}
                        required
                    />
                )}

                {duration === "FOREVER" && (
                    <Alert color="yellow" icon={<IconInfoCircle size={18} />}>
                        {t("durationForeverWarning")}
                    </Alert>
                )}

                <DatePickerInput
                    label={t("expiresAtLabel")}
                    value={expiresAt}
                    onChange={setExpiresAt}
                    minDate={new Date()}
                    clearable
                />

                <NumberInput
                    label={t("maxRedemptionsLabel")}
                    value={maxRedemptions}
                    onChange={(v) => setMaxRedemptions(v === "" ? "" : Number(v))}
                    min={1}
                />

                <Group justify="flex-end" mt="md">
                    <Button variant="default" onClick={onClose}>
                        {t("cancel")}
                    </Button>
                    <Button variant="gradient" onClick={handleSubmit} loading={saving}>
                        {t("create")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}
