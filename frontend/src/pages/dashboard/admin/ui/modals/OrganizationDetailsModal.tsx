"use client";

import {Modal, Stack, Text, Group, Button, Textarea} from "@mantine/core";
import {useTranslations} from "next-intl";
import {OrganizationResponseDTO} from "@/entities/organization";
import {useState} from "react";

interface OrganizationDetailsModalProps {
    opened: boolean;
    onClose: () => void;
    organization: OrganizationResponseDTO | null;
    onApprove: () => void | Promise<void>;
    onReject: (reason: string) => void | Promise<void>;
}

export function OrganizationDetailsModal({
                                             opened,
                                             onClose,
                                             organization,
                                             onApprove,
                                             onReject
                                         }: OrganizationDetailsModalProps) {
    const t = useTranslations("admin.adminActions");
    const tOrg = useTranslations("organization");
    const [showReasonInput, setShowReasonInput] = useState(false);
    const [showApproveConfirm, setShowApproveConfirm] = useState(false);
    const [reason, setReason] = useState("");
    const [submitting, setSubmitting] = useState(false);

    if (!organization) return null;

    const handleDenyClick = () => {
        setShowReasonInput(true);
    };

    const handleApproveClick = () => {
        setShowApproveConfirm(true);
    };

    const handleConfirmDeny = async () => {
        setSubmitting(true);
        try {
            await onReject(reason);
            setShowReasonInput(false);
            setReason("");
        } finally {
            setSubmitting(false);
        }
    };

    const handleConfirmApprove = async () => {
        setSubmitting(true);
        try {
            await onApprove();
            setShowApproveConfirm(false);
        } finally {
            setSubmitting(false);
        }
    };

    const handleCancelDeny = () => {
        setShowReasonInput(false);
        setReason("");
    };

    const handleCancelApprove = () => {
        setShowApproveConfirm(false);
    };

    const handleModalClose = () => {
        if (submitting) return;
        setShowReasonInput(false);
        setShowApproveConfirm(false);
        setReason("");
        onClose();
    };

    return (
        <Modal
            opened={opened}
            onClose={handleModalClose}
            title={t("verificationDetails")}
            size="lg"
            closeButtonProps={{ "aria-label": t("close") }}
            radius="lg"
            overlayProps={{ backgroundOpacity: 0.55 }}
        >
            <Stack gap="md">
                <Stack gap={4}>
                    <Text fw={500} size="sm" c="dimmed">
                        {tOrg("form.nameLabel")}
                    </Text>
                    <Text size="lg">{organization.name}</Text>
                </Stack>

                <Stack gap={4}>
                    <Text fw={500} size="sm" c="dimmed">
                        {tOrg("form.sizeLabel")}
                    </Text>
                    <Text>{tOrg(`sizes.${organization.organizationSize}`)}</Text>
                </Stack>

                <Stack gap={4}>
                    <Text fw={500} size="sm" c="dimmed">
                        {tOrg("form.addressLabel")}
                    </Text>
                    <Text>
                        {organization.address.street}<br />
                        {organization.address.city}, {organization.address.state} {organization.address.zipCode}<br />
                        {organization.address.country}
                    </Text>
                </Stack>

                <Stack gap={4}>
                    <Text fw={500} size="sm" c="dimmed">
                        {tOrg("form.roleLabel")}
                    </Text>
                    <Text>
                        {organization.roles.advertiser && tOrg("roles.advertiser")}
                        {organization.roles.advertiser && organization.roles.mediaOwner && ", "}
                        {organization.roles.mediaOwner && tOrg("roles.mediaOwner")}
                    </Text>
                </Stack>

                {showReasonInput && (
                    <Textarea
                        label={t("denyReason")}
                        placeholder={t("denyReasonPlaceholder")}
                        value={reason}
                        onChange={(e) => setReason(e.currentTarget.value)}
                        required
                        minRows={3}
                        autoFocus
                    />
                )}

                {showApproveConfirm && (
                    <Text c="dimmed" size="sm">
                        {t("approveConfirmMessage")}
                    </Text>
                )}

                <Group justify="flex-end" mt="md">
                    {showReasonInput ? (
                        <>
                            <Button variant="default" onClick={handleCancelDeny} disabled={submitting}>
                                {t("cancel")}
                            </Button>
                            <Button
                                color="red"
                                variant="outline"
                                onClick={handleConfirmDeny}
                                disabled={!reason.trim() || submitting}
                                loading={submitting}
                            >
                                {t("deny")}
                            </Button>
                        </>
                    ) : showApproveConfirm ? (
                        <>
                            <Button variant="default" onClick={handleCancelApprove} disabled={submitting}>
                                {t("cancel")}
                            </Button>
                            <Button variant="gradient" onClick={handleConfirmApprove} disabled={submitting} loading={submitting}>
                                {t("approve")}
                            </Button>
                        </>
                    ) : (
                        <>
                            <Button color="red" variant="outline"  onClick={handleDenyClick}>
                                {t("deny")}
                            </Button>
                            <Button variant="gradient" onClick={handleApproveClick}>
                                {t("approve")}
                            </Button>
                        </>
                    )}
                </Group>
            </Stack>
        </Modal>
    );
}