"use client";

import {Modal, Stack, Text, Group, Button, Textarea} from "@mantine/core";
import {useTranslations} from "next-intl";
import {OrganizationResponseDTO} from "@/entities/organization";
import {useState} from "react";

interface OrganizationDetailsModalProps {
    opened: boolean;
    onClose: () => void;
    organization: OrganizationResponseDTO | null;
    onApprove: () => void;
    onReject: (reason: string) => void;
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

    if (!organization) return null;

    const handleDenyClick = () => {
        setShowReasonInput(true);
    };

    const handleApproveClick = () => {
        setShowApproveConfirm(true);
    };

    const handleConfirmDeny = () => {
        onReject(reason);
        setShowReasonInput(false);
        setReason("");
    };

    const handleConfirmApprove = () => {
        onApprove();
        setShowApproveConfirm(false);
    };

    const handleCancelDeny = () => {
        setShowReasonInput(false);
        setReason("");
    };

    const handleCancelApprove = () => {
        setShowApproveConfirm(false);
    };

    const handleModalClose = () => {
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
            overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}
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
                            <Button variant="default" onClick={handleCancelDeny}>
                                {t("cancel")}
                            </Button>
                            <Button color="red" variant="outline" onClick={handleConfirmDeny} disabled={!reason.trim()}>
                                {t("deny")}
                            </Button>
                        </>
                    ) : showApproveConfirm ? (
                        <>
                            <Button variant="default" onClick={handleCancelApprove}>
                                {t("cancel")}
                            </Button>
                            <Button variant="gradient" onClick={handleConfirmApprove}>
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