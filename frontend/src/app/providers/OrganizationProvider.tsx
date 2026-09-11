"use client";

import React, { createContext, useContext, useState, useEffect, ReactNode, useCallback, useRef } from 'react';
import { useUser } from '@auth0/nextjs-auth0/client';
import { getEmployeeOrganization } from "@/features/organization-management";
import { OrganizationResponseDTO } from "@/entities/organization";
import { useRouter, usePathname } from "@/shared/lib/i18n";
import { usePermissions } from "@/app/providers/PermissionProvider";

interface OrganizationContextType {
    organization: OrganizationResponseDTO | null;
    refreshOrganization: () => Promise<void>;
    loading: boolean;
}

const OrganizationContext = createContext<OrganizationContextType | undefined>(undefined);

type OrganizationFetchResult =
    | { kind: "skip" }
    | { kind: "found"; organization: OrganizationResponseDTO | null }
    | { kind: "not-found" }
    | { kind: "error" };

// Pure data fetch — no setState here. Both the mount effect and
// `refreshOrganization` call this and apply the result themselves, since an
// effect isn't allowed to trigger setState indirectly through a shared
// named callback (see the two call sites below).
async function loadOrganization(
    userSub: string | undefined,
    permissions: string[],
    permissionsLoading: boolean,
): Promise<OrganizationFetchResult> {
    if (!userSub || permissionsLoading || (
        permissions.includes('patch:media_status') &&
        permissions.includes('readAll:verification') &&
        permissions.includes('update:verification'))
    ) {
        return { kind: "skip" };
    }

    try {
        const business = await getEmployeeOrganization(userSub);
        return { kind: "found", organization: business };
    } catch (error) {
        const status = (error as { response?: { status?: number } })?.response?.status;
        if (status === 404) {
            return { kind: "not-found" };
        }
        console.error('Failed to fetch organization:', error);
        return { kind: "error" };
    }
}

export function OrganizationProvider({ children }: { children: ReactNode }) {
    const [organization, setOrganization] = useState<OrganizationResponseDTO | null>(null);
    const [loading, setLoading] = useState(true);
    const { user, isLoading } = useUser();
    const { permissions, loading: permissionsLoading } = usePermissions();
    const router = useRouter();
    const pathname = usePathname();
    const hasRedirected = useRef(false);

    useEffect(() => {
        hasRedirected.current = false;
    }, [user?.sub]);

    const applyOrganizationResult = useCallback((result: OrganizationFetchResult) => {
        if (result.kind === "found") {
            setOrganization(result.organization);
        } else if (result.kind === "not-found") {
            setOrganization(null);
            if (!hasRedirected.current) {
                if (!pathname.endsWith('/invite')) {
                    router.push('/dashboard');
                }
                hasRedirected.current = true;
            }
        } else {
            setOrganization(null);
        }
        setLoading(false);
    }, [pathname, router]);

    const refreshOrganization = useCallback(async () => {
        if (!user) return;
        setLoading(true);
        const result = await loadOrganization(user.sub, permissions, permissionsLoading);
        applyOrganizationResult(result);
    }, [user, permissions, permissionsLoading, applyOrganizationResult]);

    // Initial load is inlined (rather than calling `refreshOrganization`) so
    // the effect's own state updates stay local to the effect, with proper
    // unmount cancellation.
    useEffect(() => {
        if (isLoading || permissionsLoading) return;
        let cancelled = false;

        (async () => {
            const result = await loadOrganization(user?.sub, permissions, permissionsLoading);
            if (cancelled) return;
            if (result.kind === "found") {
                setOrganization(result.organization);
            } else if (result.kind === "not-found") {
                setOrganization(null);
                if (!hasRedirected.current) {
                    if (!pathname.endsWith('/invite')) {
                        router.push('/dashboard');
                    }
                    hasRedirected.current = true;
                }
            } else {
                setOrganization(null);
            }
            setLoading(false);
        })();

        return () => {
            cancelled = true;
        };
    }, [isLoading, permissionsLoading, user?.sub, permissions, pathname, router]);

    return (
        <OrganizationContext.Provider value={{ organization, refreshOrganization, loading: loading || isLoading }}>
            {children}
        </OrganizationContext.Provider>
    );
}

export function useOrganization() {
    const context = useContext(OrganizationContext);
    if (context === undefined) {
        throw new Error('useOrganization must be used within OrganizationProvider');
    }
    return context;
}