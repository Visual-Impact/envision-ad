import { auth0 } from "@/shared/api/index.server";
import { NextResponse } from "next/server";

export async function GET() {
    try {
        const session = await auth0.getSession();

        if (!session) {
            return NextResponse.json(
                { error: "No active session" },
                { status: 401 }
            );
        }

        const { token } = await auth0.getAccessToken();
        if (!token) {
            return NextResponse.json(
                { error: "No access token found" },
                { status: 401 },
            );
        }
        return NextResponse.json({ accessToken: token });
    } catch (error) {
        console.error('Error getting access token:', error);
        return NextResponse.json({ accessToken: null }, { status: 500 });
    }
}

export async function POST() {
    try {
        const session = await auth0.getSession();

        if (!session) {
            return NextResponse.json(
                { error: "No active session" },
                { status: 401 }
            );
        }

        const { token } = await auth0.getAccessToken({
            refresh: true,
        });

        if (!token) {
            return NextResponse.json(
                { error: "No access token found" },
                { status: 401 },
            );
        }
        return NextResponse.json({ statusCode: 200 });
    } catch (error) {
        console.error('Error refreshing access token:', error);
        return NextResponse.json({ error: 'Error refreshing access token' }, { status: 500 });
    }
}