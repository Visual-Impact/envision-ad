import { NextRequest, NextResponse } from 'next/server';
import { auth0 } from "@/shared/api/index.server";
import { Auth0ManagementService } from "@/shared/api/index.server";
import {jwtDecode} from "jwt-decode";
import {Token} from "@/entities/auth";
import {
    getAllOrganizationEmployeesServer,
    getEmployeeOrganizationServer
} from "@/features/organization-management";

export async function GET(
    request: NextRequest,
    { params }: { params: Promise<{ id: string }> }
) {
    const { id } = await params;
    const session = await auth0.getSession();

    if (!session || !session.user) {
        return NextResponse.json({ error: 'Unauthorized' }, { status: 401 });
    }

    const decodedId = decodeURIComponent(id);
    const isSelfRequest = session.user.sub === decodedId;

    if (!isSelfRequest) {
        const { token } = await auth0.getAccessToken();
        const permissions = jwtDecode<Token>(token).permissions;

        if (!permissions.includes('read:employee')) {
            return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
        }

        const organization = await getEmployeeOrganizationServer(session.user.sub, token);

        if (!organization) {
            return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
        }

        const employees = await getAllOrganizationEmployeesServer(organization.businessId, token);

        if (!employees.some(employee => employee.user_id === decodedId)) {
            return NextResponse.json({ error: 'Forbidden' }, { status: 403 });
        }
    }

    try {
        const result = await Auth0ManagementService.getUser(decodedId);

        if (!result) {
            return NextResponse.json({ error: 'User not found' }, { status: 404 });
        }

        const { data } = result;
        return NextResponse.json(data);
    } catch (err) {
        console.error("Error fetching user:", err);
        return NextResponse.json({ error: 'Server error' }, { status: 500 });
    }
}