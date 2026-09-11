'use client'

import {OrganizationVerificationTable} from "@/pages/dashboard/admin/ui/tables/OrganizationVerificationTable";
import {OrganizationDetailsModal} from "@/pages/dashboard/admin/ui/modals/OrganizationDetailsModal";
import {Alert, Button, Group, Stack, Title} from "@mantine/core";
import {IconAlertTriangle} from "@tabler/icons-react";
import {useCallback, useEffect, useRef, useState} from "react";
import {useTranslations} from "next-intl";
import {VerificationResponseDTO} from "@/entities/organization";
import {
    approveOrganizationVerification, denyOrganizationVerification,
    getAllVerificationRequests,
    getOrganizationById
} from "@/features/organization-management";
import {OrganizationResponseDTO} from "@/entities/organization";
import {notifications} from "@mantine/notifications";

type OrgDetailEntry =
    | { status: "loading" }
    | { status: "loaded"; data: OrganizationResponseDTO }
    | { status: "error" };

export default function VerifyingOrganizationDashboard() {
    const t = useTranslations("admin.adminActions");
    const [verificationRequests, setVerificationRequests] = useState<VerificationResponseDTO[]>([]);
    const [listLoadFailed, setListLoadFailed] = useState(false);
    const [listRefreshToken, setListRefreshToken] = useState(0);
    const [organizationDetails, setOrganizationDetails] = useState<Record<string, OrgDetailEntry>>({});
    const [selectedOrganization, setSelectedOrganization] = useState<OrganizationResponseDTO | null>(null);
    const [modalOpen, setModalOpen] = useState(false);

    // Gates which businessIds already have a fetch in flight or resolved, so a detail
    // resolving doesn't re-trigger the effect into re-fetching every other still-in-flight id.
    const requestedIdsRef = useRef<Set<string>>(new Set());

    useEffect(() => {
        let cancelled = false;

        (async () => {
            setListLoadFailed(false);
            try {
                const data = await getAllVerificationRequests();
                if (!cancelled) setVerificationRequests(data);
            } catch (error) {
                console.error("Failed to load verification requests:", error);
                if (!cancelled) setListLoadFailed(true);
            }
        })();

        return () => { cancelled = true; };
    }, [listRefreshToken]);

    const fetchOrganizationDetail = useCallback(async (businessId: string) => {
        requestedIdsRef.current.add(businessId);
        setOrganizationDetails(prev => ({...prev, [businessId]: {status: "loading"}}));

        try {
            const response = await getOrganizationById(businessId);
            setOrganizationDetails(prev => ({...prev, [businessId]: {status: "loaded", data: response}}));
        } catch (error) {
            console.error(`Failed to fetch organization details for ${businessId}:`, error);
            requestedIdsRef.current.delete(businessId);
            setOrganizationDetails(prev => ({...prev, [businessId]: {status: "error"}}));
        }
    }, []);

    useEffect(() => {
        verificationRequests.forEach(request => {
            if (!requestedIdsRef.current.has(request.businessId)) {
                void fetchOrganizationDetail(request.businessId);
            }
        });
    }, [verificationRequests, fetchOrganizationDetail]);

    const handleRequestRemoved = (id: string) => {
        setVerificationRequests(prev => prev.filter(request => request.verificationId !== id));
    };

    const handleRowClick = (request: VerificationResponseDTO) => {
        const entry = organizationDetails[request.businessId];
        if (entry?.status === "loaded") {
            setSelectedOrganization(entry.data);
            setModalOpen(true);
        }
    };

    const handleCloseModal = () => {
        setModalOpen(false);
        setSelectedOrganization(null);
    };

    const handleApprove = async () => {
        if (!selectedOrganization) return;

        const verificationRequest = verificationRequests.find(
            req => req.businessId === selectedOrganization.businessId
        );

        if (!verificationRequest) return;

        try {
            await approveOrganizationVerification(
                selectedOrganization.businessId,
                verificationRequest.verificationId
            );

            handleRequestRemoved(verificationRequest.verificationId);
            handleCloseModal();
            notifications.show({
                title: t('success.title'),
                message: t('success.approved'),
                color: 'green'
            });
        } catch {
            notifications.show({
                title: t('errors.title'),
                message: t('errors.approveFailed'),
                color: 'red'
            });
        }
    };

    const handleReject = async (reason: string) => {
        if (!selectedOrganization) return;

        const verificationRequest = verificationRequests.find(
            req => req.businessId === selectedOrganization.businessId
        );

        if (!verificationRequest) return;

        try {
            await denyOrganizationVerification(
                selectedOrganization.businessId,
                verificationRequest.verificationId,
                reason
            );
            handleRequestRemoved(verificationRequest.verificationId);
            handleCloseModal();
            notifications.show({
                title: t('success.title'),
                message: t('success.denied'),
                color: 'green'
            });
        } catch {
            notifications.show({
                title: t('errors.title'),
                message: t('errors.denyFailed'),
                color: 'red'
            });
        }
    };

    const getOrganizationName = (businessId: string): string => {
        const entry = organizationDetails[businessId];
        return entry?.status === "loaded" ? entry.data.name : businessId;
    };

    const getDetailStatus = (businessId: string): "loading" | "loaded" | "error" =>
        organizationDetails[businessId]?.status ?? "loading";

    const retryOrganizationDetail = (businessId: string) => {
        void fetchOrganizationDetail(businessId);
    };

    return (
        <Stack component="main" gap="md" p="md" style={{flex: 1, minWidth: 0}}>
            <Group justify="space-between" align="center">
                <Title order={1}>{t("pendingOrganization")}</Title>
            </Group>

            {listLoadFailed && (
                <Alert icon={<IconAlertTriangle size="1rem" />} color="red" title={t('errors.title')}>
                    <Group justify="space-between" align="center">
                        {t('errors.loadRequestsFailed')}
                        <Button size="xs" variant="light" color="red" onClick={() => setListRefreshToken(v => v + 1)}>
                            {t('retry')}
                        </Button>
                    </Group>
                </Alert>
            )}

            <OrganizationVerificationTable
                rows={verificationRequests}
                onRequestRemoved={handleRequestRemoved}
                onRowClick={handleRowClick}
                getOrganizationName={getOrganizationName}
                getDetailStatus={getDetailStatus}
                onRetryDetail={retryOrganizationDetail}
            />

            <OrganizationDetailsModal
                opened={modalOpen}
                onClose={handleCloseModal}
                organization={selectedOrganization}
                onApprove={handleApprove}
                onReject={handleReject}
            />
        </Stack>
    );
}