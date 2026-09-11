"use client";

import { createContext, useContext } from "react";
import type { OrganizationResponseDTO } from "./organization";

export interface CurrentOrganizationContextValue {
    organization: OrganizationResponseDTO | null;
    refreshOrganization: () => Promise<void>;
    loading: boolean;
}

// The provider lives in app/providers (it fetches through features/organization-management,
// which an entity may not import); the context and its hook live here so pages and widgets
// can read the current organization without importing the app layer.
export const OrganizationContext = createContext<CurrentOrganizationContextValue | undefined>(undefined);

export function useOrganization() {
    const context = useContext(OrganizationContext);
    if (context === undefined) {
        throw new Error('useOrganization must be used within OrganizationProvider');
    }
    return context;
}
