import { Modal, TextInput, Button, Group, Stack } from "@mantine/core";
import { useForm } from "@mantine/form";
import { useTranslations } from "next-intl";
import { useState } from "react";
import { updateUser } from "../api/updateUser";
import { Employee } from "@/entities/organization";
import { useRouter } from "next/navigation";
import { notifications } from "@mantine/notifications";

interface EditProfileModalProps {
    opened: boolean;
    onClose: () => void;
    user: Employee;
}

export function EditProfileModal({ opened, onClose, user }: EditProfileModalProps) {
    const t = useTranslations("profilePage.editModal");
    const router = useRouter();
    const [loading, setLoading] = useState(false);

    const form = useForm({
        initialValues: {
            given_name: user.given_name || "",
            family_name: user.family_name || "",
            nickname: user.nickname || user.name || "",
        },
        validate: {
            nickname: (value) => (value.trim().length < 1 ? t("required") : null),
            given_name: (value) => (value.trim().length < 1 ? t("required") : null),
            family_name: (value) => (value.trim().length < 1 ? t("required") : null),
        },
    });

    // Reset form when the modal transitions to open. Adjusting state during
    // render (rather than in an effect) for a prop change is the pattern
    // React recommends — see
    // https://react.dev/reference/react/useState#storing-information-from-previous-renders
    const [prevOpened, setPrevOpened] = useState(false);
    if (opened !== prevOpened) {
        setPrevOpened(opened);
        if (opened) {
            form.setValues({
                given_name: user.given_name || "",
                family_name: user.family_name || "",
                nickname: user.nickname || user.name || ""
            });
        }
    }

    const handleSubmit = async (values: typeof form.values) => {
        setLoading(true);
        try {
            // Trim values to avoid whitespace issues
            const trimmedGivenName = values.given_name.trim();
            const trimmedFamilyName = values.family_name.trim();
            const trimmedNickname = values.nickname.trim();

            // Also update the full name to keep it consistent
            const name = `${trimmedGivenName} ${trimmedFamilyName}`.trim();
            const updateData = {
                given_name: trimmedGivenName,
                family_name: trimmedFamilyName,
                nickname: trimmedNickname,
                name: name || trimmedNickname
            };

            await updateUser(user.sub || user.user_id, updateData);
            notifications.show({
                title: t("successTitle"),
                message: t("successMessage"),
                color: "green",
            });
            router.refresh();
            onClose();
        } catch (error) {
            console.error("Failed to update profile", error);
            notifications.show({
                title: "Error",
                message: t("error") || "Failed to update profile",
                color: "red",
            });
        } finally {
            setLoading(false);
        }
    };

    return (
        <Modal opened={opened} onClose={onClose} title={t("title")} size="xl">
            <form onSubmit={form.onSubmit(handleSubmit)}>
                <Stack gap="md">
                    <TextInput
                        label={t("username")}
                        placeholder={t("usernamePlaceholder")}
                        {...form.getInputProps("nickname")}
                    />
                    <TextInput
                        label={t("firstName")}
                        placeholder={t("firstNamePlaceholder")}
                        {...form.getInputProps("given_name")}
                    />
                    <TextInput
                        label={t("lastName")}
                        placeholder={t("lastNamePlaceholder")}
                        {...form.getInputProps("family_name")}
                    />
                    <Group justify="flex-end" mt="md">
                        <Button variant="default" onClick={onClose}>{t("cancel")}</Button>
                        <Button type="submit" loading={loading}>{t("save")}</Button>
                    </Group>
                </Stack>
            </form>
        </Modal>
    );
}
