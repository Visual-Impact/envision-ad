import { auth0 } from "@/shared/api/index.server";
import { Employee } from "@/entities/organization";
import { Auth0ManagementService } from "@/shared/api/index.server";

export async function getUserServer(): Promise<Employee | null> {
    const session = await auth0.getSession();

    if (!session || !session.user) {
        return null;
    }

    try {
        const response = await Auth0ManagementService.getUser(session.user.sub);
        if (response?.isStale) {
            console.warn(`Using stale user data for ${session.user.sub}`);
        }
        const auth0User = response?.data;
        return (auth0User as Employee) || (session.user as Employee);
    } catch (error) {
        console.error("Failed to fetch user data:", error)
        return session.user as Employee
    }
}