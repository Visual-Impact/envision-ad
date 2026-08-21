import {Auth0ManagementService} from "@/shared/api/auth0/management";
import {NextRequest, NextResponse} from "next/server";
import {auth0} from "@/shared/api/auth0/auth0";
import {jwtDecode} from "jwt-decode";
import {Token} from "@/entities/auth";
import {ASSIGNABLE_ROLE_IDS, AUTH0_ROLES} from "@/shared/lib/auth/roles";
import {
    getAllOrganizationEmployeesServer,
    getEmployeeOrganizationServer
} from "@/features/organization-management/api";

export async function POST(
    request: NextRequest,
    { params }: { params: Promise<{ id: string }> }
) {
    const { id } = await params;
    const { roles } = await request.json();
    const session = await auth0.getSession();

    if (!session || !session.user) {
        return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    if (!Array.isArray(roles) || roles.length === 0 || !roles.every(role => typeof role === 'string' && ASSIGNABLE_ROLE_IDS.includes(role))) {
        return NextResponse.json({ error: 'Bad Request' }, { status: 400 });
    }

    if (roles.includes(AUTH0_ROLES.ADMIN)){
        return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
    }

    const { token } = await auth0.getAccessToken();
    const permissions = jwtDecode<Token>(token).permissions;

    const decodedId = decodeURIComponent(id);
    const organization = await getEmployeeOrganizationServer(session.user.sub, token)
    if (!organization) {
        return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
    }

    // P5 FR 1.5: BUSINESS_OWNER can never be assigned through this end-user-callable
    // route anymore. It's now assigned exactly once, server-to-server, by
    // AdminAccountService via the Management API as part of admin account creation
    // (webservice/.../admin/businesslogiclayer/AdminAccountServiceImpl) — never by a
    // route reachable from an already-authenticated end-user session. This used to be a
    // narrower guard that still allowed a caller to self-assign BUSINESS_OWNER right
    // after self-creating their own organization; that self-creation path no longer
    // exists (see src/pages/dashboard/page.tsx), so the guard is now unconditional.
    if (roles.includes(AUTH0_ROLES.BUSINESS_OWNER)) {
        return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
    }

    const hasAdvertiserRole = roles.includes(AUTH0_ROLES.ADVERTISER);
    const hasMediaOwnerRole = roles.includes(AUTH0_ROLES.MEDIA_OWNER);

    // Zero-permission self-bootstrap — still needed by the invitation-accept flow so a
    // newly-joined employee can assign themselves ADVERTISER/MEDIA_OWNER for the org
    // they just joined (organization-invitation/ui/page.tsx). BUSINESS_OWNER is already
    // excluded by the guard above, so this can no longer be reached by the old
    // self-registration path.
    if (decodedId === session.user.sub && permissions.length === 0 && (!hasAdvertiserRole || organization.roles.advertiser) && (!hasMediaOwnerRole || organization.roles.mediaOwner)){
        await Auth0ManagementService.updateUserRole(decodedId, roles, 'POST');
        return Response.json({ success: true });
    }

    if (!roles.includes(AUTH0_ROLES.BUSINESS_OWNER)) {
        const employees = await getAllOrganizationEmployeesServer(organization.businessId, token);
        if ((hasAdvertiserRole || hasMediaOwnerRole) && (!permissions.includes('update:business') || !employees.some(employee => employee.user_id === decodedId) || (hasAdvertiserRole && !organization.roles.advertiser) || (hasMediaOwnerRole && !organization.roles.mediaOwner))){
            return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
        }
    }

    await Auth0ManagementService.updateUserRole(decodedId, roles, 'POST');

    return Response.json({ success: true });
}

export async function DELETE(
    request: NextRequest,
    { params }: { params: Promise<{ id: string }> }
) {
    const { id } = await params;
    const { roles } = await request.json();
    const session = await auth0.getSession();

    if (!session || !session.user) {
        return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    if (!Array.isArray(roles) || roles.length === 0 || !roles.every(role => typeof role === 'string' && ASSIGNABLE_ROLE_IDS.includes(role))) {
        return NextResponse.json({ error: 'Bad Request' }, { status: 400 });
    }

    if (roles.includes(AUTH0_ROLES.ADMIN) || roles.includes(AUTH0_ROLES.BUSINESS_OWNER)){
        return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
    }

    const { token } = await auth0.getAccessToken();
    const permissions = jwtDecode<Token>(token).permissions;
    const decodedId = decodeURIComponent(id);
    const organization = await getEmployeeOrganizationServer(session.user.sub, token)
    if (!organization) {
        return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
    }

    const employees = await getAllOrganizationEmployeesServer(organization.businessId, token)
    const hasAdvertiserRole = roles.includes(AUTH0_ROLES.ADVERTISER);
    const hasMediaOwnerRole = roles.includes(AUTH0_ROLES.MEDIA_OWNER);

    if (permissions.includes('delete:employee') && employees.some(employee => employee.user_id === decodedId) && (!organization.roles.mediaOwner || hasMediaOwnerRole) && (!organization.roles.advertiser || hasAdvertiserRole)){
        await Auth0ManagementService.updateUserRole(decodedId, roles, 'DELETE');
        return Response.json({ success: true });
    }

    if (hasAdvertiserRole !== hasMediaOwnerRole && (!permissions.includes('update:business') || !employees.some(employee => employee.user_id === decodedId) || (hasAdvertiserRole && organization.roles.advertiser) || (hasMediaOwnerRole && organization.roles.mediaOwner)) && roles.length !== 1){
        return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
    }

    await Auth0ManagementService.updateUserRole(decodedId, roles, 'DELETE');

    return Response.json({ success: true });
}