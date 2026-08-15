import { Address, OrganizationSize, Roles } from "@/entities/organization";

// Mirrors the backend's AdminAccountRequestModel (P5 FR 4.2) — the "business" sub-object
// is BusinessRequestModel, extended with the P4 hook field businessTypeVenueId.
export interface CreateAccountRequestDTO {
    email: string;
    name: string;
    business: {
        name: string;
        organizationSize: OrganizationSize;
        address: Address;
        roles: Roles;
        businessTypeVenueId: string | null;
    };
}

// Mirrors BusinessResponseModel as extended in P5 M2 (businessTypeVenueId + active).
export interface AccountBusinessResponseDTO {
    businessId: string;
    name: string;
    ownerId: string;
    organizationSize: OrganizationSize | string;
    address: Address;
    roles: Roles;
    verified: boolean;
    businessTypeVenueId: string | null;
    active: boolean;
    dateCreated: string;
}

// Mirrors AdminAccountResponseModel — warnings is non-empty when the account was
// created but a best-effort step (roles/ticket/email) failed post-commit (FR 4.2.d).
export interface CreateAccountResponseDTO {
    business: AccountBusinessResponseDTO;
    ownerUserId: string;
    warnings: string[];
}

// Mirrors AdminAccountListItemModel — one row of GET /admin/accounts.
export interface AccountListItem {
    businessId: string;
    name: string;
    ownerEmail: string | null;
    roles: Roles;
    businessTypeVenueId: string | null;
    active: boolean;
    dateCreated: string;
}

// Mirrors Spring Data's Page<AdminAccountListItemModel> JSON shape — same fields
// MediaListResponseDTO already relies on for /media/active, same backend serialization.
export interface AccountListPageResponse {
    content: AccountListItem[];
    totalElements: number;
    totalPages: number;
    number: number;
    size: number;
}
