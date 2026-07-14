"use client";

import { Button, Group, Modal, Text } from "@mantine/core";
import styles from "./ConfirmationModal.module.css";

interface ConfirmationModalProps {
    opened: boolean;
    title?: string;
    message: string | React.ReactNode;
    confirmLabel?: string;
    cancelLabel?: string;
    confirmColor?: string;
    loading?: boolean;
    onConfirm: () => void;
    onCancel: () => void;
}

export function ConfirmationModal({
    opened,
    title,
    message,
    confirmLabel,
    cancelLabel,
    confirmColor,
    loading = false,
    onConfirm,
    onCancel,
}: ConfirmationModalProps) {
    return (
        <Modal
            opened={opened}
            onClose={onCancel}
            title={title}
            centered
            padding="lg"
            radius="lg"
            shadow="lg"
            closeOnClickOutside={!loading}
            closeOnEscape={!loading}
            withCloseButton={!loading}
            overlayProps={{ backgroundOpacity: 0.55, blur: 2 }}
            classNames={{ content: styles.modalContent }}
        >
            <Text size="sm">
                {message}
            </Text>

            <Group justify="flex-end" mt="lg">
                <Button variant="default" radius="md" className={styles.cancelButton} onClick={onCancel} disabled={loading}>
                    {cancelLabel}
                </Button>

                <Button color={confirmColor} radius="md" className={styles.confirmButton} onClick={onConfirm} loading={loading}>
                    {confirmLabel}
                </Button>
            </Group>
        </Modal>
    );
}

