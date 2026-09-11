"use client";

import { ActionIcon, Button, Group, Menu, Modal, Text, Tooltip } from "@mantine/core";
import { useDisclosure } from "@mantine/hooks";
import { IconClock, IconEdit, IconMenu2, IconPower, IconTrash } from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { MediaRowData } from "./MediaRow";
import { MediaStatusEnum } from "@/entities/media";

interface MediaActionsProps {
    row: MediaRowData;
    onEdit?: (id: string | number) => void;
    onDelete?: (id: string | number) => void;
    onToggleStatus?: (
        id: string | number,
        nextStatus: MediaStatusEnum.ACTIVE | MediaStatusEnum.INACTIVE
    ) => void | Promise<void>;
}

export function MediaActions({ row, onEdit, onDelete, onToggleStatus }: MediaActionsProps) {
    const t = useTranslations("media");
    const [modalOpened, { open: openModal, close: closeModal }] = useDisclosure(false);
    const [pendingAction, setPendingAction] = useState<"activate" | "deactivate" | null>(null);

    const isActivating = pendingAction === "activate";

    const isLocked =
        row.status === MediaStatusEnum.PENDING || row.status === MediaStatusEnum.REJECTED;

    return (
        <>
            <Menu withinPortal position="bottom-end" shadow="md">
                <Menu.Target>
                    <ActionIcon variant="subtle" radius="xl" aria-label="Open media actions">
                        <IconMenu2 size={16} />
                    </ActionIcon>
                </Menu.Target>

                <Menu.Dropdown>
                    <Menu.Label>{t("actionsMenu.title")}</Menu.Label>

                    {onEdit && (
                        <Menu.Item leftSection={<IconEdit size={14} />} onClick={() => onEdit(row.id)}>
                            {t("actionsMenu.edit")}
                        </Menu.Item>
                    )}

                    {isLocked ? (
                        <Tooltip
                            label={
                                row.status === MediaStatusEnum.PENDING
                                    ? t("changeStatus.pendingToolTip")
                                    : t("changeStatus.rejectedToolTip")
                            }
                            withArrow
                            position="left"
                        >
                            <Menu.Item leftSection={<IconClock size={14} />} disabled style={{ opacity: 0.6 }}>
                                {row.status === MediaStatusEnum.PENDING
                                    ? t("changeStatus.waitApproval")
                                    : t("changeStatus.rejected")}
                            </Menu.Item>
                        </Tooltip>
                    ) : (
                        <Menu.Item
                            leftSection={<IconPower size={14} />}
                            onClick={() => {
                                const action = row.status === MediaStatusEnum.ACTIVE ? "deactivate" : "activate";
                                setPendingAction(action);
                                openModal();
                            }}
                        >
                            {row.status === MediaStatusEnum.ACTIVE
                                ? t("changeStatus.deactivate")
                                : t("changeStatus.activate")}
                        </Menu.Item>
                    )}

                    <Menu.Divider />
                    <Menu.Label>{t("actionsMenu.dangerZone")}</Menu.Label>

                    {onDelete && (
                        <Menu.Item
                            color="red"
                            leftSection={<IconTrash size={14} />}
                            onClick={() => onDelete(row.id)}
                        >
                            {t("actionsMenu.deleteMedia")}
                        </Menu.Item>
                    )}
                </Menu.Dropdown>
            </Menu>

            <Modal
                opened={modalOpened}
                onClose={closeModal}
                centered
                radius="lg"
                overlayProps={{ backgroundOpacity: 0.55 }}
                title={
                    isActivating
                        ? `${t("changeStatus.activate")} ${row.name}`
                        : `${t("changeStatus.deactivate")} ${row.name}`
                }
            >
                <Text mb="md">
                    {isActivating
                        ? t("changeStatus.activateModalMessage")
                        : t("changeStatus.deactivateModalMessage")}
                </Text>

                <Group justify="flex-end">
                    <Button variant="default" onClick={closeModal}>
                        {t("actionsMenu.cancel")}
                    </Button>

                    <Button
                        color={isActivating ? "blue" : "red"}
                        onClick={() => {
                            closeModal();
                            const nextStatus =
                                row.status === MediaStatusEnum.ACTIVE
                                    ? MediaStatusEnum.INACTIVE
                                    : MediaStatusEnum.ACTIVE;
                            onToggleStatus?.(row.id, nextStatus);
                        }}
                    >
                        {isActivating ? t("changeStatus.activate") : t("changeStatus.deactivate")}
                    </Button>
                </Group>
            </Modal>
        </>
    );
}
