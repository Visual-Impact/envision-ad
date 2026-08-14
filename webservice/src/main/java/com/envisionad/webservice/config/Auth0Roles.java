package com.envisionad.webservice.config;

/**
 * Auth0 role IDs assignable by the backend (P5 admin account creation, brief §2.3.4).
 * Must stay in sync with frontend/src/shared/lib/auth/roles.ts's AUTH0_ROLES — there is
 * no shared source of truth between the two repos for these tenant-specific IDs.
 * ADMIN is intentionally omitted: never assignable by this or any other flow.
 */
public final class Auth0Roles {

    private Auth0Roles() {}

    public static final String BUSINESS_OWNER = "rol_fFGTiHiGm6EV36pD";
    public static final String ADVERTISER = "rol_q4MqASyi5dAiihSJ";
    public static final String MEDIA_OWNER = "rol_7n9XL2cqwJzjRfi4";
}
