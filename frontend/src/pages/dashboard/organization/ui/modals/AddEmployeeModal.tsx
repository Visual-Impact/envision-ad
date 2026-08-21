"use client";

import React, {useState} from "react";
import {Alert, Button, Group, Modal, Stack, TextInput} from "@mantine/core";
import {useTranslations} from "next-intl";
import {createInviteEmployeeToOrganization} from "@/features/organization-management/api";
import {IconInfoCircle} from "@tabler/icons-react";
import {notifications} from "@mantine/notifications";
import {Employee, InvitationResponse} from "@/entities/organization";

interface BusinessModalProps {
    opened: boolean;
    onClose: () => void;
    onSuccess: () => void;
    employees: Employee[];
    invitations: InvitationResponse[];
    organizationId: string;
}

export function AddEmployeeModal({
                                     opened,
                                     onClose,
                                     onSuccess,
                                     employees,
                                     invitations,
                                     organizationId,
                                 }: BusinessModalProps) {
    const t = useTranslations("organization.employees.form");
    const [saving, setSaving] = useState(false);
    const [invalidInputWarning, setInvalidInputWarning] = useState<string | null>(null);
    const [email, setEmail] = useState("");
    const [name, setName] = useState("");

    // Reset once the modal transitions to open. Adjusting state during
    // render (rather than in an effect) for a prop change is the pattern
    // React recommends — see
    // https://react.dev/reference/react/useState#storing-information-from-previous-renders
    const [prevOpened, setPrevOpened] = useState(opened);
    if (opened !== prevOpened) {
        setPrevOpened(opened);
        if (opened) {
            setInvalidInputWarning(null);
            setName("");
        }
    }

    const validateEmail = (email: string) => {
        const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
        return emailRegex.test(email);
    };

    const handleSave = async () => {
        if (!email || email.trim() === '') {
            setInvalidInputWarning(t("errors.emailRequired"));
            return;
        }

        if (!validateEmail(email)) {
            setInvalidInputWarning(t("errors.emailInvalid"));
            return;
        }

        if (employees.some(employee => employee.email === email)) {
            setInvalidInputWarning(t("errors.emailExists"));
            return;
        }

        if (invitations.some(invitation => invitation.email === email)) {
            setInvalidInputWarning(t("errors.emailInvited"));
            return;
        }

        setSaving(true);
        try {
            await createInviteEmployeeToOrganization(organizationId, { email, name: name.trim() || undefined });
            setEmail('');
            setName('');
            onSuccess();
            onClose();
            notifications.show({
                title: t("success.title"),
                message: t("success.invitation"),
                color: "green",
            });
        } catch (error) {
            console.error("Failed to add employee to organization", error);
            notifications.show({
                title: t("errors.error"),
                message: t("errors.saveFailed"),
                color: "red",
            });
        } finally {
            setSaving(false);
        }
    };

    return (
        <Modal
            opened={opened}
            onClose={onClose}
            title={t("title")}
            centered
            size="lg"
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55 }}
        >
            <Stack gap="md">
                {invalidInputWarning && (
                    <Alert
                        variant="light"
                        color="red"
                        title={t('errors.validationError')}
                        icon={<IconInfoCircle />}
                        mt="sm"
                    >
                        {invalidInputWarning}
                    </Alert>
                )}
                <TextInput
                    label={t("email")}
                    placeholder={t("placeholder")}
                    value={email}
                    onChange={(e) => setEmail(e.currentTarget.value)}
                    required
                />
                <TextInput
                    label={t("name")}
                    placeholder={t("namePlaceholder")}
                    description={t("nameHint")}
                    value={name}
                    onChange={(e) => setName(e.currentTarget.value)}
                />

                <Group justify="flex-end" mt="md">
                    <Button variant="default" onClick={onClose} disabled={saving}>
                        {t("cancel")}
                    </Button>
                    <Button variant="gradient" onClick={handleSave} loading={saving}>
                        {t("submit")}
                    </Button>
                </Group>
            </Stack>
        </Modal>
    );
}
