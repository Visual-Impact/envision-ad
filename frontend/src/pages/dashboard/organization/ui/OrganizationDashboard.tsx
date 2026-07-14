"use client";

import React, { useEffect, useState } from "react";
import { Button, Center, Group, Loader, Stack, Title } from "@mantine/core";
import { OrganizationDetail } from "@/pages/dashboard/organization/ui/tables/OrganizationTable";
import { OrganizationModal } from "@/pages/dashboard/organization/ui/modals/OrganizationModal";
import { useTranslations } from "next-intl";
import { useOrganizationForm } from "@/pages/dashboard/organization/hooks/useOrganizationForm";
import { OrganizationSize } from "@/entities/organization";
import { VerificationResponseDTO } from "@/entities/organization/model/verification";
import {
    getAllOrganizationVerifications,
    requestOrganizationVerification,
    updateOrganization,
    getAllOrganizationEmployees
} from "@/features/organization-management/api";
import { VerificationHistoryTable } from "@/pages/dashboard/organization/ui/tables/VerificationTable";
import { notifications } from "@mantine/notifications";
import { ConfirmationModal } from "@/shared/ui";
import { AUTH0_ROLES } from "@/shared/lib/auth/roles";
import { useOrganization, usePermissions } from "@/app/providers";

export default function OrganizationDashboard() {
    const { formState, updateField, resetForm, setFormState } = useOrganizationForm();
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [verifications, setVerifications] = useState<VerificationResponseDTO[]>([]);
    const [loadingVerifications, setLoadingVerifications] = useState(false);
    const [requestingVerification, setRequestingVerification] = useState(false);
    const { organization, refreshOrganization } = useOrganization();
    const { permissions, refreshPermissions } = usePermissions();
    const [confirmVerificationRequest, setConfirmVerificationRequest] = useState(false);
    const [editingId, setEditingId] = useState<string | null>(null);
    const t = useTranslations("organization");

    useEffect(() => {
        if (!organization?.businessId || !permissions.includes('read:verification') || !permissions.includes('create:verification')) return;

        let ignored = false;

        const loadVerifications = async () => {
            try {
                setLoadingVerifications(true);
                const verificationHistory = await getAllOrganizationVerifications(organization.businessId);
                if (!ignored) setVerifications(verificationHistory);
            } catch (error) {
                console.error("Failed to load verification history", error);
            } finally {
                if (!ignored) setLoadingVerifications(false);
            }
        };

        void loadVerifications();

        return () => { ignored = true; };
    }, [organization?.businessId, permissions]);

    const handleEdit = () => {
        if (!organization) return;
        setFormState({
            name: organization.name ?? "",
            organizationSize: (typeof organization.organizationSize === "string"
                ? OrganizationSize[organization.organizationSize as keyof typeof OrganizationSize]
                : organization.organizationSize) ?? OrganizationSize.SMALL,
            address: {
                street: organization.address?.street ?? "",
                city: organization.address?.city ?? "",
                state: organization.address?.state ?? "",
                zipCode: organization.address?.zipCode ?? "",
                country: organization.address?.country ?? ""
            },
            roles: {
                advertiser: organization.roles.advertiser ?? false,
                mediaOwner: organization.roles.mediaOwner ?? false
            }
        });
        setEditingId(organization.businessId);
        setIsModalOpen(true);
    };

    const handleSave = async () => {
        if (!editingId || !organization) return;
        try {
            await updateOrganization(editingId, formState);

            const roleChanges = {
                toRemove: [
                    organization.roles.advertiser && !formState.roles.advertiser && AUTH0_ROLES.ADVERTISER,
                    organization.roles.mediaOwner && !formState.roles.mediaOwner && AUTH0_ROLES.MEDIA_OWNER,
                ].filter(Boolean) as string[],
                toAdd: [
                    !organization.roles.advertiser && formState.roles.advertiser && AUTH0_ROLES.ADVERTISER,
                    !organization.roles.mediaOwner && formState.roles.mediaOwner && AUTH0_ROLES.MEDIA_OWNER,
                ].filter(Boolean) as string[],
            };

            if (roleChanges.toRemove.length > 0 || roleChanges.toAdd.length > 0) {
                const employees = await getAllOrganizationEmployees(editingId);
                await Promise.all(
                    employees.map(async (employee) => {
                        const encodedUserId = encodeURIComponent(employee.user_id);
                        if (roleChanges.toRemove.length > 0) {
                            await fetch(`/api/auth0/update-user-roles/${encodedUserId}`, {
                                method: 'DELETE',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ roles: roleChanges.toRemove })
                            });
                        }
                        if (roleChanges.toAdd.length > 0) {
                            await fetch(`/api/auth0/update-user-roles/${encodedUserId}`, {
                                method: 'POST',
                                headers: { 'Content-Type': 'application/json' },
                                body: JSON.stringify({ roles: roleChanges.toAdd })
                            });
                        }
                    })
                );
                await refreshPermissions();
            }

            await refreshOrganization();
            notifications.show({ title: t("success.title"), message: t("success.update"), color: "green" });
            setIsModalOpen(false);
            setEditingId(null);
            resetForm();
        } catch (error) {
            console.error("Failed to update organization", error);
            notifications.show({ title: t("errors.error"), message: t("errors.updateFailed"), color: "red" });
            throw error;
        }
    };

    const handleRequestVerification = async () => {
        if (!organization?.businessId) return;
        try {
            setRequestingVerification(true);
            await requestOrganizationVerification(organization.businessId);
            const verificationHistory = await getAllOrganizationVerifications(organization.businessId);
            setVerifications(verificationHistory);
            setConfirmVerificationRequest(false);
            notifications.show({ title: t('success.title'), message: t('success.verificationRequest'), color: 'green' });
        } catch (error) {
            console.error("Failed to request verification", error);
            notifications.show({ title: t('errors.title'), message: t('errors.verificationRequestFailed'), color: 'red' });
        } finally {
            setRequestingVerification(false);
        }
    };

    if (!organization) return <Center py="xl"><Loader /></Center>;

    const isVerified = organization.verified ?? false;
    const hasPendingVerification = verifications.some(v => v.status === "PENDING");
    const canRequestVerification = !isVerified && !hasPendingVerification;

    return (
        <Stack gap="md" p="md">
            <Group justify="space-between">
                <Title order={1}>{t("title")}</Title>
            </Group>

            <OrganizationDetail organization={organization} onEdit={handleEdit} />

            <OrganizationModal
                opened={isModalOpen}
                onClose={() => { setIsModalOpen(false); setEditingId(null); resetForm(); }}
                onSave={handleSave}
                formState={formState}
                onFieldChange={updateField}
                editingId={editingId}
            />

            {(permissions.includes('read:verification') || permissions.includes('create:verification')) && (
                <Stack gap="md">
                    <Group justify="space-between" align="center">
                        <Title order={2}>{t("verificationHistory")}</Title>
                        <Button
                            variant="gradient"
                            onClick={() => setConfirmVerificationRequest(true)}
                            disabled={!canRequestVerification}
                            loading={requestingVerification}
                        >
                            {t("requestVerification")}
                        </Button>
                    </Group>

                    <ConfirmationModal
                        opened={confirmVerificationRequest}
                        title={t("modal.verification")}
                        message={t("modal.verificationMessage")}
                        cancelLabel={t("modal.cancel")}
                        confirmLabel={t("modal.confirm")}
                        confirmColor="green"
                        onCancel={() => setConfirmVerificationRequest(false)}
                        onConfirm={handleRequestVerification}
                    />

                    {loadingVerifications ? (
                        <Center py="xl"><Loader size="sm" /></Center>
                    ) : (
                        <VerificationHistoryTable verifications={verifications} />
                    )}
                </Stack>
            )}
        </Stack>
    );
}