"use client";

import React from "react";
import {Checkbox, Select, TextInput} from "@mantine/core";
import {useTranslations} from "next-intl";
import {OrganizationRequestDTO, OrganizationSize} from "@/entities/organization";

interface BusinessDetailsFormProps {
    formState: OrganizationRequestDTO;
    onFieldChange: <K extends keyof OrganizationRequestDTO>(
        field: K,
        value: OrganizationRequestDTO[K]
    ) => void;
    // Roles are admin-only post-creation (PATCH /api/v1/admin/accounts/{businessId}/roles) —
    // the admin "create client account" flow still sets them here at creation time, but the
    // business owner's self-service "edit organization" flow (OrganizationModal) must not
    // expose them, since the backend now ignores whatever this form sends anyway.
    showRoles?: boolean;
}

export function OrganizationDetailsForm({
                                        formState,
                                        onFieldChange,
                                        showRoles = true,
                                    }: BusinessDetailsFormProps) {
    const t = useTranslations("organization");

    const organizationSizeOptions = Object.values(OrganizationSize).map((size) => ({
        value: size,
        label: t(`sizes.${size}`),
    }));

    return (
        <>
            <TextInput
                label={t("form.nameLabel")}
                placeholder={t("form.namePlaceholder")}
                value={formState.name}
                onChange={(e) => onFieldChange("name", e.currentTarget.value)}
                required
            />

            <Select
                label={t("form.sizeLabel")}
                placeholder={t("form.sizePlaceholder")}
                data={organizationSizeOptions}
                value={formState.organizationSize}
                onChange={(val) => {
                    if (val) onFieldChange("organizationSize", val as OrganizationSize);
                }}
                required
            />

            <h4>{t("form.addressLabel")}</h4>
            <TextInput
                label={t("form.streetLabel")}
                placeholder={t("form.streetPlaceholder")}
                value={formState.address.street}
                onChange={(e) => onFieldChange("address", {...formState.address, street: e.currentTarget.value,})}
                required
            />

            <div style={{display: "flex", gap: 12}}>
                <TextInput
                    label={t("form.cityLabel")}
                    placeholder={t("form.cityPlaceholder")}
                    value={formState.address.city}
                    onChange={(e) => onFieldChange("address", {...formState.address, city: e.currentTarget.value,})}
                    required
                    style={{flex: 1}}
                />
                <TextInput
                    label={t("form.stateLabel")}
                    placeholder={t("form.statePlaceholder")}
                    value={formState.address.state}
                    onChange={(e) => onFieldChange("address", {...formState.address, state: e.currentTarget.value,})}
                    required
                    style={{flex: 1}}
                />
            </div>

            <div style={{display: "flex", gap: 12}}>
                <TextInput
                    label={t("form.zipLabel")}
                    placeholder={t("form.zipPlaceholder")}
                    value={formState.address.zipCode}
                    onChange={(e) => onFieldChange("address", {...formState.address, zipCode: e.currentTarget.value,})}
                    required
                    style={{flex: 1}}
                />
                <TextInput
                    label={t("form.countryLabel")}
                    placeholder={t("form.countryPlaceholder")}
                    value={formState.address.country}
                    onChange={(e) => onFieldChange("address", {...formState.address, country: e.currentTarget.value,})}
                    required
                    style={{flex: 1}}
                />
            </div>

            {showRoles && (
                <>
                    <h4>{t("form.roleLabel")}</h4>
                    <div style={{display: "flex", flexDirection: "column", gap: 12}}>
                        <Checkbox
                            label={t("roles.advertiser")}
                            checked={formState.roles.advertiser}
                            onChange={(e) => onFieldChange("roles", {...formState.roles, advertiser: e.currentTarget.checked,})}
                        />
                        <Checkbox
                            label={t("roles.mediaOwner")}
                            checked={formState.roles.mediaOwner}
                            onChange={(e) => onFieldChange("roles", {...formState.roles, mediaOwner: e.currentTarget.checked,})}
                        />
                    </div>
                </>
            )}
        </>
    );
}
