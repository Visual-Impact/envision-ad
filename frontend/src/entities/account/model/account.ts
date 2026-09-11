import { Address, OrganizationSize, Roles } from "@/entities/organization/@x/account";

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

// Mirrors UpdateRolesResponseModel — response for
// PATCH /admin/accounts/{businessId}/roles. warnings is non-empty when the role
// change committed but resyncing one or more employees' Auth0 roles failed —
// retrying the same PATCH is safe and re-applies the full target state.
export interface UpdateRolesResponseDTO {
    business: AccountBusinessResponseDTO;
    warnings: string[];
}

// Mirrors RoleRemovalEligibilityResponseModel — read-only precheck for the roles-edit
// UI, called before the admin tries to save so the modal can gray out the save action
// and explain why instead of surfacing the block only after a failed PATCH. `true`
// means "removing this role right now would succeed" — it says nothing about whether
// the business currently holds the role at all.
export interface RoleRemovalEligibilityDTO {
    mediaOwnerRemovable: boolean;
    advertiserRemovable: boolean;
}
