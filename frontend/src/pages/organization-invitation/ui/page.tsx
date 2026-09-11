'use client';

import { useTranslations } from 'next-intl';
import { useSearchParams, useRouter } from "next/navigation";
import { useUser } from "@auth0/nextjs-auth0/client";
import { useEffect, useRef, useState } from "react";
import { Button, Loader, Stack, Text, Title } from "@mantine/core";
import { IconCheck, IconMail, IconX } from "@tabler/icons-react";
import { addEmployeeToOrganization, getOrganizationById } from "@/features/organization-management";
import { AUTH0_ROLES } from "@/shared/lib/auth";
import { usePermissions } from "@/app/providers";
import { Link } from "@/shared/lib/i18n";

export default function OrganizationInvitationPage() {
    const t = useTranslations('invitation');
    const searchParams = useSearchParams();
    const router = useRouter();
    const { user, isLoading } = useUser();
    const { refreshPermissions } = usePermissions();

    const [status, setStatus] = useState<"loading" | "success" | "checkEmail" | "error">("loading");
    const [message, setMessage] = useState("");

    const token = searchParams?.get("token");
    const organizationId = searchParams?.get("businessId");
    const invitationProcessed = useRef(false);

    // P5 FR 3.2: the accept endpoint is the single source of truth now — this no longer
    // pre-emptively redirects a logged-out visitor to /auth/login before even knowing
    // whether they need to. It always calls accept first (the backend accepts an
    // anonymous caller, see BusinessServiceImpl.addBusinessEmployee) and only redirects
    // to login when the backend reports LOGIN_REQUIRED (an existing Auth0 account
    // matched the invitation email) — a genuinely new invitee gets provisioned in the
    // same call and never sees a login screen at all.
    useEffect(() => {
        if (!searchParams || isLoading || invitationProcessed.current) return;
        if (!token || !organizationId) return;

        invitationProcessed.current = true;

        const acceptInvitation = async () => {
            try {
                const result = await addEmployeeToOrganization(organizationId, token);

                if (result.status === "LOGIN_REQUIRED") {
                    const returnUrl = `/invite?businessId=${encodeURIComponent(organizationId)}&token=${encodeURIComponent(token)}`;
                    router.push(`/auth/login?returnTo=${encodeURIComponent(returnUrl)}`);
                    return;
                }

                if (result.status === "PROVISIONED") {
                    setStatus("checkEmail");
                    setMessage(t('checkEmail.message'));
                    return;
                }

                // ACCEPTED — only returned when the backend received a real session, so
                // `user` is guaranteed set here.
                const organization = await getOrganizationById(organizationId);

                await fetch(`/api/auth0/update-user-roles/${encodeURIComponent(user!.sub)}`, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify({
                        roles: [
                            ...(organization.roles.advertiser ? [AUTH0_ROLES.ADVERTISER] : []),
                            ...(organization.roles.mediaOwner ? [AUTH0_ROLES.MEDIA_OWNER] : []),
                        ],
                    }),
                });

                await refreshPermissions();
                setStatus("success");
                setMessage(t('success.message'));
                setTimeout(() => router.push("/dashboard/organization/overview"), 2000);
            } catch {
                setStatus("error");
                setMessage(t('error.message'));
            }
        };

        void acceptInvitation();
    }, [user, isLoading, token, organizationId, router, searchParams, refreshPermissions, t]);

    if (searchParams && (!token || !organizationId)) {
        return (
            <Stack align="center" justify="center" gap="md" style={{ minHeight: "calc(100vh - 340px)" }}>
                <IconX size={48} color="red" />
                <Title order={1} ta="center" size="h2">{t('invalidLink.title')}</Title>
                <Text ta="center">{t('invalidLink.description')}</Text>
                <Button size="sm" component={Link} href="/">{t('back')}</Button>
            </Stack>
        );
    }

    if (!searchParams || isLoading || status === "loading") {
        return (
            <Stack align="center" justify="center" gap="md" style={{ minHeight: "calc(100vh - 340px)" }}>
                <Loader size="xl" />
                <Text>{t('loading')}</Text>
            </Stack>
        );
    }

    return (
        <Stack align="center" justify="center" gap="md" style={{ minHeight: "calc(100vh - 340px)" }}>
            {status === "success" && (
                <>
                    <IconCheck size={48} color="green" />
                    <Title order={1} ta="center" size="h2">{t('success.title')}</Title>
                    <Text ta="center">{message}</Text>
                    <Text size="sm" c="dimmed">{t('success.redirecting')}</Text>
                </>
            )}
            {status === "checkEmail" && (
                <>
                    <IconMail size={48} color="blue" />
                    <Title order={1} ta="center" size="h2">{t('checkEmail.title')}</Title>
                    <Text ta="center">{message}</Text>
                    <Button size="sm" component={Link} href="/">{t('back')}</Button>
                </>
            )}
            {status === "error" && (
                <>
                    <IconX size={48} color="red" />
                    <Title order={1} ta="center" size="h2">{t('error.title')}</Title>
                    <Text ta="center">{message}</Text>
                    <Button size="sm" component={Link} href="/">{t('back')}</Button>
                </>
            )}
        </Stack>
    );
}
