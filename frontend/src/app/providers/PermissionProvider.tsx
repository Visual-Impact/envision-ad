"use client";

import React, { createContext, useContext, useState, useEffect, ReactNode, useCallback } from 'react';
import { useUser } from '@auth0/nextjs-auth0/client';
import {jwtDecode} from "jwt-decode";
import {Token} from "@/entities/auth";
import {resetTokenCache} from "@/shared/api/axios/axios";

interface PermissionsContextType {
    permissions: string[];
    refreshPermissions: () => Promise<void>;
    loading: boolean;
}

const PermissionsContext = createContext<PermissionsContextType | undefined>(undefined);

export function PermissionsProvider({ children }: { children: ReactNode }) {
    const [permissions, setPermissions] = useState<string[]>([]);
    const [loading, setLoading] = useState(true);
    const { user, isLoading } = useUser();

    const fetchPermissions = useCallback(async () => {
        if (!user) {
            setPermissions([]);
            setLoading(false);
            return;
        }

        try {
            const response = await fetch('/api/auth0/token');
            if (response.ok) {
                const { accessToken } = await response.json();
                if (accessToken) {
                    const permissions = jwtDecode<Token>(accessToken).permissions;
                    setPermissions(permissions);
                }
            }
        } catch (error) {
            console.error('Failed to fetch permissions:', error);
            setPermissions([]);
        } finally {
            setLoading(false);
        }
    }, [user]);

    const refreshPermissions = useCallback(async () => {
        if (!user) return;

        setLoading(true);
        try {
            await fetch('/api/auth0/token', { method: 'POST' });
            resetTokenCache();
            await fetchPermissions();
        } catch (error) {
            console.error('Failed to refresh permissions:', error);
            setLoading(false);
        }
    }, [fetchPermissions, user]);

    // Initial load is inlined (rather than calling `fetchPermissions`) so
    // the effect's own state updates stay local to the effect, with proper
    // unmount cancellation. `fetchPermissions` remains for
    // `refreshPermissions` above, which isn't run from an effect.
    useEffect(() => {
        if (isLoading) return;

        let cancelled = false;

        (async () => {
            if (!user) {
                setPermissions([]);
                setLoading(false);
                return;
            }
            try {
                const response = await fetch('/api/auth0/token');
                if (response.ok) {
                    const { accessToken } = await response.json();
                    if (accessToken && !cancelled) {
                        const decodedPermissions = jwtDecode<Token>(accessToken).permissions;
                        setPermissions(decodedPermissions);
                    }
                }
            } catch (error) {
                console.error('Failed to fetch permissions:', error);
                if (!cancelled) setPermissions([]);
            } finally {
                if (!cancelled) setLoading(false);
            }
        })();

        return () => {
            cancelled = true;
        };
    }, [isLoading, user]);

    return (
        <PermissionsContext.Provider value={{ permissions, refreshPermissions, loading: loading || isLoading }}>
            {children}
        </PermissionsContext.Provider>
    );
}

export function usePermissions() {
    const context = useContext(PermissionsContext);
    if (context === undefined) {
        throw new Error('usePermissions must be used within PermissionsProvider');
    }
    return context;
}