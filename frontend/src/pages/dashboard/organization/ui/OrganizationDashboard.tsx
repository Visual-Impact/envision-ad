"use client";

import React, { useEffect, useState } from "react";
import { Center, Group, Loader, Stack, Title } from "@mantine/core";
import { OrganizationDetail } from "@/pages/dashboard/organization/ui/tables/OrganizationTable";
import { OrganizationModal } from "@/pages/dashboard/organization/ui/modals/OrganizationModal";
import { useLocale, useTranslations } from "next-intl";
import { useOrganizationForm } from "@/features/organization-management";
import { OrganizationSize } from "@/entities/organization";
import { updateOrganization } from "@/features/organization-management";
import { notifications } from "@mantine/notifications";
import { useOrganization } from "@/entities/organization";
import { Venue } from "@/entities/venue";
import { getAllVenues } from "@/features/venue-management";

export default function OrganizationDashboard() {
    const { formState, updateField, resetForm, setFormState } = useOrganizationForm();
    const [isModalOpen, setIsModalOpen] = useState(false);
    const { organization, refreshOrganization } = useOrganization();
    const [editingId, setEditingId] = useState<string | null>(null);
    const t = useTranslations("organization");
    const locale = useLocale();

    // The business type is admin-set (P4); the advertiser only sees it. The org response carries
    // the venue id, so resolve it to a name against the public venue list.
    const businessTypeVenueId = organization?.businessTypeVenueId ?? null;
    const [businessType, setBusinessType] = useState<Venue | null>(null);
    useEffect(() => {
        let cancelled = false;
        (async () => {
            if (!businessTypeVenueId) {
                setBusinessType(null);
                return;
            }
            try {
                const venues = await getAllVenues(locale);
                if (!cancelled) setBusinessType(venues.find((v) => v.venueId === businessTypeVenueId) ?? null);
            } catch {
                if (!cancelled) setBusinessType(null);
            }
        })();
        return () => {
            cancelled = true;
        };
    }, [businessTypeVenueId, locale]);
    const businessTypeName = businessType ? (locale === "fr" ? businessType.nameFr : businessType.nameEn) : null;

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
            // Roles are admin-only post-creation (PATCH /api/v1/admin/accounts/{businessId}/roles) —
            // formState.roles is not user-editable here (OrganizationDetailsForm's
            // showRoles={false}) and the backend ignores whatever this payload says
            // about roles regardless, so there is no Auth0 role resync to do from this form.
            await updateOrganization(editingId, formState);

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

    if (!organization) return <Center py="xl"><Loader /></Center>;

    return (
        <Stack gap="md" p="md">
            <Group justify="space-between">
                <Title order={1}>{t("title")}</Title>
            </Group>

            <OrganizationDetail organization={organization} onEdit={handleEdit} businessTypeName={businessTypeName} />

            <OrganizationModal
                opened={isModalOpen}
                onClose={() => { setIsModalOpen(false); setEditingId(null); resetForm(); }}
                onSave={handleSave}
                formState={formState}
                onFieldChange={updateField}
                editingId={editingId}
            />
        </Stack>
    );
}