import { redirect } from "next/navigation";
import { Container } from "@mantine/core";
import React from "react";
import { getUserServer } from "../api/getUserServer";
import ProfileContent from "@/pages/profile/ui/ProfileContent";

export default async function ProfilePage() {
    const user = await getUserServer();

    if (!user) {
        redirect("/auth/login");
    }

    return (
        <>
            <Container size="lg" py="xl">
                {/* Pass the server-fetched user data down to the client component*/}
                <ProfileContent user={user} />
            </Container>
        </>
    );

}
