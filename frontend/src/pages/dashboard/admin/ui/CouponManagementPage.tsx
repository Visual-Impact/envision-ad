"use client";

import { Button, Group, Loader, Stack, Switch, Title } from "@mantine/core";
import { IconPlus } from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { notifications } from "@mantine/notifications";
import { Coupon, CouponRequestDTO } from "@/entities/coupon";
import { getAllCoupons, createCoupon, updateCouponActive, archiveCoupon } from "@/features/coupon-management/api";
import { CouponTable } from "@/pages/dashboard/admin/ui/tables/CouponTable";
import { CouponFormModal } from "@/pages/dashboard/admin/ui/modals/CouponFormModal";
import { CouponDeleteModal } from "@/pages/dashboard/admin/ui/modals/CouponDeleteModal";

export default function CouponManagementPage() {
    const t = useTranslations("couponManagement");

    const [coupons, setCoupons] = useState<Coupon[]>([]);
    const [loading, setLoading] = useState(true);
    const [includeArchived, setIncludeArchived] = useState(false);

    const [formModalOpen, setFormModalOpen] = useState(false);

    const [deleteModalOpen, setDeleteModalOpen] = useState(false);
    const [deletingCoupon, setDeletingCoupon] = useState<Coupon | null>(null);

    const refreshCoupons = useCallback(async () => {
        try {
            const data = await getAllCoupons(includeArchived);
            setCoupons(data);
        } catch {
            notifications.show({ title: t("notifications.loadFailed"), message: "", color: "red" });
        } finally {
            setLoading(false);
        }
    }, [t, includeArchived]);

    // Re-runs whenever `includeArchived` flips, same effect driving both the initial
    // load and the toggle — inlined rather than calling `refreshCoupons` so the
    // effect's own state updates stay local to it, with proper unmount cancellation,
    // matching VenueManagementPage's established pattern in this codebase.
    useEffect(() => {
        let cancelled = false;
        setLoading(true);

        (async () => {
            try {
                const data = await getAllCoupons(includeArchived);
                if (!cancelled) setCoupons(data);
            } catch {
                if (!cancelled) {
                    notifications.show({ title: t("notifications.loadFailed"), message: "", color: "red" });
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [t, includeArchived]);

    const handleCreate = () => {
        setFormModalOpen(true);
    };

    const handleDeleteClick = (coupon: Coupon) => {
        setDeletingCoupon(coupon);
        setDeleteModalOpen(true);
    };

    const handleSave = async (data: CouponRequestDTO) => {
        try {
            await createCoupon(data);
            notifications.show({ title: t("notifications.created"), message: "", color: "green" });
            setFormModalOpen(false);
            await refreshCoupons();
        } catch {
            notifications.show({ title: t("notifications.createFailed"), message: "", color: "red" });
        }
    };

    const handleToggleActive = async (coupon: Coupon, active: boolean) => {
        // Optimistic: the switch should feel immediate, and a failure is rare
        // (it's just an active flip) — reverted via refresh on error.
        setCoupons((prev) =>
            prev.map((c) => (c.couponId === coupon.couponId ? { ...c, status: active ? "ACTIVE" : "INACTIVE" } : c))
        );
        try {
            await updateCouponActive(coupon.couponId, { active });
        } catch {
            notifications.show({ title: t("notifications.updateFailed"), message: "", color: "red" });
            await refreshCoupons();
        }
    };

    const handleDeleteConfirm = async () => {
        if (!deletingCoupon) return;
        try {
            await archiveCoupon(deletingCoupon.couponId);
            notifications.show({ title: t("notifications.deleted"), message: "", color: "green" });
            setDeleteModalOpen(false);
            setDeletingCoupon(null);
            await refreshCoupons();
        } catch {
            notifications.show({ title: t("notifications.deleteFailed"), message: "", color: "red" });
        }
    };

    return (
        <Stack component="main" gap="md" p="md" style={{ flex: 1, minWidth: 0 }}>
            <Group justify="space-between" align="center">
                <Title order={1}>{t("title")}</Title>
                <Group gap="md">
                    <Switch
                        label={t("table.showArchived")}
                        checked={includeArchived}
                        onChange={(e) => setIncludeArchived(e.currentTarget.checked)}
                    />
                    <Button variant="gradient" leftSection={<IconPlus size={18} />} onClick={handleCreate}>
                        {t("addCoupon")}
                    </Button>
                </Group>
            </Group>

            {loading ? (
                <Loader />
            ) : (
                <CouponTable
                    coupons={coupons}
                    onToggleActive={handleToggleActive}
                    onDelete={handleDeleteClick}
                />
            )}

            <CouponFormModal
                opened={formModalOpen}
                onClose={() => setFormModalOpen(false)}
                onSave={handleSave}
            />

            <CouponDeleteModal
                opened={deleteModalOpen}
                onClose={() => { setDeleteModalOpen(false); setDeletingCoupon(null); }}
                onConfirm={handleDeleteConfirm}
                coupon={deletingCoupon}
            />
        </Stack>
    );
}
