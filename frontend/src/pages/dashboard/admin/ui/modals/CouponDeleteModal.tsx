"use client";

import { Button, Group, Modal, Stack, Text } from "@mantine/core";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { Coupon } from "@/entities/coupon";

interface CouponDeleteModalProps {
    opened: boolean;
    onClose: () => void;
    onConfirm: () => Promise<void>;
    coupon: Coupon | null;
}

export function CouponDeleteModal({ opened, onClose, onConfirm, coupon }: CouponDeleteModalProps) {
    const t = useTranslations("couponManagement.delete");
    const [deleting, setDeleting] = useState(false);

    if (!coupon) return null;

    const handleConfirm = async () => {
        setDeleting(true);
        try {
            await onConfirm();
        } finally {
            setDeleting(false);
        }
    };

    return (
        <Modal
            opened={opened}
            onClose={onClose}
            title={t("title")}
            size="sm"
            centered
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55 }}
        >
            <Stack gap="md">
                <Text>{t("message", { code: coupon.code })}</Text>

                <Group justify="flex-end" mt="md">
                    <Button variant="default" onClick={onClose}>
                        {t("cancel")}
                    </Button>
                    <Button color="red" onClick={handleConfirm} loading={deleting}>
                        {t("confirm")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}
