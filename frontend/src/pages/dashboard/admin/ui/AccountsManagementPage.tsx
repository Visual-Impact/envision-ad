"use client";

import { Button, Group, Loader, Pagination, Stack, Title } from "@mantine/core";
import { IconUserPlus } from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import { useCallback, useEffect, useState } from "react";
import { notifications } from "@mantine/notifications";
import axios from "axios";
import { AccountListItem, CreateAccountRequestDTO } from "../model/account";
import { Roles } from "@/entities/organization";
import { Venue } from "@/entities/venue";
import { createAccount, getAllAccounts, resendCredentials, setAccountActive, updateAccountRoles } from "../api";
import { getAllVenues } from "@/features/venue-management";
import { AccountsTable } from "@/pages/dashboard/admin/ui/tables/AccountsTable";
import { CreateAccountModal } from "@/pages/dashboard/admin/ui/modals/CreateAccountModal";
import { EditRolesModal } from "@/pages/dashboard/admin/ui/modals/EditRolesModal";

const PAGE_SIZE = 20;

export default function AccountsManagementPage() {
    const t = useTranslations("accountManagement");

    const [accounts, setAccounts] = useState<AccountListItem[]>([]);
    const [venues, setVenues] = useState<Venue[]>([]);
    const [loading, setLoading] = useState(true);
    const [createModalOpen, setCreateModalOpen] = useState(false);
    const [editRolesAccount, setEditRolesAccount] = useState<AccountListItem | null>(null);
    const [pendingBusinessId, setPendingBusinessId] = useState<string | null>(null);
    // 1-indexed for Mantine's Pagination, converted to 0-indexed on the request —
    // same convention BrowsePage.tsx uses for /media/active.
    const [activePage, setActivePage] = useState(1);
    const [totalPages, setTotalPages] = useState(1);

    const refresh = useCallback(async () => {
        try {
            const [accountsData, venuesData] = await Promise.all([
                getAllAccounts(activePage - 1, PAGE_SIZE),
                getAllVenues(),
            ]);
            setAccounts(accountsData.content);
            setTotalPages(accountsData.totalPages);
            setVenues(venuesData);
        } catch {
            notifications.show({ title: t("notifications.loadFailed"), message: "", color: "red" });
        } finally {
            setLoading(false);
        }
    }, [activePage, t]);

    // Initial/page-change load inlined (not calling `refresh`) so the effect's own state
    // updates stay local to it with proper unmount cancellation — same pattern as
    // VenueManagementPage; `refresh` is for the post-mutation refreshes below. Re-runs
    // whenever `activePage` changes, so paging re-fetches that page.
    useEffect(() => {
        let cancelled = false;

        (async () => {
            try {
                const [accountsData, venuesData] = await Promise.all([
                    getAllAccounts(activePage - 1, PAGE_SIZE),
                    getAllVenues(),
                ]);
                if (!cancelled) {
                    setAccounts(accountsData.content);
                    setTotalPages(accountsData.totalPages);
                    setVenues(venuesData);
                }
            } catch {
                if (!cancelled) {
                    notifications.show({ title: t("notifications.loadFailed"), message: "", color: "red" });
                }
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [activePage, t]);

    const handleCreate = async (data: CreateAccountRequestDTO) => {
        try {
            const result = await createAccount(data);
            notifications.show({
                title: result.warnings.length > 0 ? t("notifications.createdWithWarnings") : t("notifications.created"),
                message: "",
                color: result.warnings.length > 0 ? "yellow" : "green",
            });
            setCreateModalOpen(false);
            await refresh();
        } catch (error) {
            const isDuplicate = axios.isAxiosError(error) && error.response?.status === 409;
            notifications.show({
                title: isDuplicate ? t("notifications.duplicateEmail") : t("notifications.createFailed"),
                message: "",
                color: "red",
            });
        }
    };

    const handleResend = async (account: AccountListItem) => {
        setPendingBusinessId(account.businessId);
        try {
            await resendCredentials(account.businessId);
            notifications.show({ title: t("notifications.resent"), message: "", color: "green" });
        } catch {
            notifications.show({ title: t("notifications.resendFailed"), message: "", color: "red" });
        } finally {
            setPendingBusinessId(null);
        }
    };

    const handleToggleActive = async (account: AccountListItem) => {
        setPendingBusinessId(account.businessId);
        try {
            await setAccountActive(account.businessId, !account.active);
            notifications.show({ title: t("notifications.statusUpdated"), message: "", color: "green" });
            await refresh();
        } catch {
            notifications.show({ title: t("notifications.statusUpdateFailed"), message: "", color: "red" });
        } finally {
            setPendingBusinessId(null);
        }
    };

    const handleUpdateRoles = async (roles: Roles) => {
        if (!editRolesAccount) return;
        try {
            const result = await updateAccountRoles(editRolesAccount.businessId, roles);
            notifications.show({
                title: result.warnings.length > 0 ? t("notifications.rolesUpdatedWithWarnings") : t("notifications.rolesUpdated"),
                message: "",
                color: result.warnings.length > 0 ? "yellow" : "green",
            });
            setEditRolesAccount(null);
            await refresh();
        } catch (error) {
            // 409 covers both the removal-blocked guards (live subscription tied to the
            // role being removed) and the "at least one role required" rule — both are
            // the admin needing to resolve something before retrying, not a system error.
            const isBlocked = axios.isAxiosError(error) && error.response?.status === 409;
            notifications.show({
                title: isBlocked ? t("notifications.rolesUpdateBlocked") : t("notifications.rolesUpdateFailed"),
                message: "",
                color: "red",
            });
        }
    };

    return (
        <Stack component="main" gap="md" p="md" style={{ flex: 1, minWidth: 0 }}>
            <Group justify="space-between" align="center">
                <Title order={1}>{t("title")}</Title>
                <Button variant="gradient" leftSection={<IconUserPlus size={18} />} onClick={() => setCreateModalOpen(true)}>
                    {t("newAccount")}
                </Button>
            </Group>

            {loading ? (
                <Loader />
            ) : (
                <>
                    <AccountsTable
                        accounts={accounts}
                        venues={venues}
                        onResend={handleResend}
                        onToggleActive={handleToggleActive}
                        onEditRoles={setEditRolesAccount}
                        pendingBusinessId={pendingBusinessId}
                    />
                    {totalPages > 1 && (
                        <Group justify="center" mt="md">
                            <Pagination total={totalPages} value={activePage} onChange={setActivePage} />
                        </Group>
                    )}
                </>
            )}

            <CreateAccountModal
                opened={createModalOpen}
                onClose={() => setCreateModalOpen(false)}
                onSave={handleCreate}
                venues={venues}
            />

            <EditRolesModal
                opened={editRolesAccount !== null}
                onClose={() => setEditRolesAccount(null)}
                onSave={handleUpdateRoles}
                account={editRolesAccount}
            />
        </Stack>
    );
}
