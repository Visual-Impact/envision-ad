export interface InvitationRequest {
    email: string;
    // Optional — only used if the invitee still has no Auth0 account when the
    // invitation is accepted (P5 FR 3.2).
    name?: string;
}

export interface InvitationResponse {
    invitationId: string;
    email: string;
    timeExpires: string;
}

export type InvitationAcceptStatus = "ACCEPTED" | "LOGIN_REQUIRED" | "PROVISIONED";

// Mirrors the backend's InvitationAcceptResponseModel (P5 FR 3.2).
export interface InvitationAcceptResponse {
    status: InvitationAcceptStatus;
    employee: { employeeId: string; userId: string } | null;
}