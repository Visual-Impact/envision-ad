"use client";

import { createContext, useContext } from "react";

export interface PermissionsContextValue {
    permissions: string[];
    refreshPermissions: () => Promise<void>;
    loading: boolean;
}

// The provider lives in app/providers (it talks to the Auth0 token route); the context and
// its hook live here so pages and widgets can read permissions without importing the app layer.
export const PermissionsContext = createContext<PermissionsContextValue | undefined>(undefined);

export function usePermissions() {
    const context = useContext(PermissionsContext);
    if (context === undefined) {
        throw new Error('usePermissions must be used within PermissionsProvider');
    }
    return context;
}
