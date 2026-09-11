"use client";

import { useEffect, useState } from "react";
import { Alert, Button, Checkbox, Group, Modal, Stack } from "@mantine/core";
import { IconInfoCircle } from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import { AccountListItem, RoleRemovalEligibilityDTO } from "../../model/account";
import { Roles } from "@/entities/organization";
import { getRoleRemovalEligibility } from "../../api";

interface EditRolesModalProps {
    opened: boolean;
    onClose: () => void;
    onSave: (roles: Roles) => Promise<void>;
    account: AccountListItem | null;
}

// Admin-only (PATCH /api/v1/admin/accounts/{businessId}/roles) — this is deliberately
// separate from OrganizationDetailsForm's role checkboxes, which the business owner's
// self-service edit flow no longer exposes at all (roles are admin-only post-creation).
export function EditRolesModal({ opened, onClose, onSave, account }: EditRolesModalProps) {
    const t = useTranslations("accountManagement.rolesModal");
    const tRoles = useTranslations("organization.roles");
    const [roles, setRoles] = useState<Roles>({ advertiser: false, mediaOwner: false });
    const [saving, setSaving] = useState(false);
    const [validationError, setValidationError] = useState<string | null>(null);
    const [eligibility, setEligibility] = useState<RoleRemovalEligibilityDTO | null>(null);
    const [loadingEligibility, setLoadingEligibility] = useState(false);

    // Same render-time reset pattern as the other modals in this codebase (OrganizationModal,
    // CreateAccountModal) — seed the checkboxes from the account being edited each time the
    // modal opens, rather than in an effect.
    const [prevOpened, setPrevOpened] = useState(opened);
    if (opened !== prevOpened) {
        setPrevOpened(opened);
        if (opened && account) {
            setRoles({ advertiser: account.roles.advertiser, mediaOwner: account.roles.mediaOwner });
            setValidationError(null);
        }
    }

    // Fetches whether removing a currently-held role would actually be blocked, so the
    // checkbox/save UI can reflect it up front instead of the admin only finding out
    // after a failed save. A genuine network side effect, so this is a real useEffect
    // rather than the render-time reset pattern used for the synchronous fields above.
    useEffect(() => {
        let cancelled = false;

        (async () => {
            if (!opened || !account) {
                setEligibility(null);
                return;
            }
            setLoadingEligibility(true);
            try {
                const result = await getRoleRemovalEligibility(account.businessId);
                if (!cancelled) setEligibility(result);
            } catch {
                // Fail open — the PATCH endpoint still enforces the real constraint, so
                // worst case here is the admin finds out via the save-time toast instead
                // of proactively. Better than blocking the whole modal on this lookup.
                if (!cancelled) setEligibility(null);
            } finally {
                if (!cancelled) setLoadingEligibility(false);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [opened, account]);

    const removingMediaOwner = Boolean(account?.roles.mediaOwner) && !roles.mediaOwner;
    const removingAdvertiser = Boolean(account?.roles.advertiser) && !roles.advertiser;
    const mediaOwnerBlocked = removingMediaOwner && eligibility !== null && !eligibility.mediaOwnerRemovable;
    const advertiserBlocked = removingAdvertiser && eligibility !== null && !eligibility.advertiserRemovable;
    const isBlocked = mediaOwnerBlocked || advertiserBlocked;

    const blockedRoleNames = [
        mediaOwnerBlocked ? tRoles("mediaOwner") : null,
        advertiserBlocked ? tRoles("advertiser") : null,
    ].filter((name): name is string => name !== null);

    const handleSave = async () => {
        setValidationError(null);
        if (!roles.advertiser && !roles.mediaOwner) {
            setValidationError(t("errors.roleRequired"));
            return;
        }

        setSaving(true);
        try {
            await onSave(roles);
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            opened={opened}
            onClose={onClose}
            title={t("title", { business: account?.name ?? "" })}
            size="sm"
            centered
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55 }}
        >
            <Stack gap="md">
                {validationError && (
                    <Alert variant="light" color="red" icon={<IconInfoCircle />}>
                        {validationError}
                    </Alert>
                )}

                {isBlocked && (
                    <Alert variant="light" color="red" icon={<IconInfoCircle />}>
                        {t("blockedDescription", { roles: blockedRoleNames.join(", ") })}
                    </Alert>
                )}

                <Stack gap="xs">
                    <Checkbox
                        label={tRoles("advertiser")}
                        checked={roles.advertiser}
                        onChange={(e) => setRoles({ ...roles, advertiser: e.currentTarget.checked })}
                    />
                    <Checkbox
                        label={tRoles("mediaOwner")}
                        checked={roles.mediaOwner}
                        onChange={(e) => setRoles({ ...roles, mediaOwner: e.currentTarget.checked })}
                    />
                </Stack>

                <Group justify="flex-end" mt="md">
                    <Button variant="default" onClick={onClose} disabled={saving}>
                        {t("cancel")}
                    </Button>
                    <Button
                        variant="gradient"
                        onClick={handleSave}
                        loading={saving || loadingEligibility}
                        disabled={isBlocked}
                    >
                        {t("save")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}
