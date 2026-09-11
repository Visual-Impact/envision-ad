"use client";

import React from "react";
import { useMediaQuery } from "@mantine/hooks";
import {Box, Center, Group, Loader, Paper} from "@mantine/core";
import SideBar from "@/widgets/SideBar/SideBar";
import { useOrganization } from "@/entities/organization";
import { usePermissions } from "@/shared/lib/permissions";
import { isAdmin as computeIsAdmin } from "@/shared/lib/auth";

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
    const isMobile = useMediaQuery("(max-width: 768px)");
    const { organization, loading: orgLoading } = useOrganization();
    const { permissions, loading: permissionsLoading } = usePermissions();

    const isAdmin = computeIsAdmin(permissions);

    if (orgLoading || permissionsLoading) {
        return (
            <Center style={{ minHeight: "calc(100vh - 80px)" }}>
                <Loader />
            </Center>
        );
    }

    return (
        <Box>
            <Group align="flex-start" gap="lg" wrap="nowrap" px={{ base: 0, md: "lg" }} py={{ base: 0, md: "lg" }}>
                {!isMobile && (!!organization || isAdmin) && (
                    <Paper
                        w={260}
                        p="md"
                        radius="lg"
                        shadow="sm"
                        withBorder
                        bg="gray.0"
                        pos="sticky"
                        top={96}
                        style={{ flexShrink: 0, alignSelf: "flex-start", maxHeight: "calc(100vh - 128px)", overflowY: "auto" }}
                    >
                        <SideBar />
                    </Paper>
                )}
                <div style={{ flex: 1, minWidth: 0 }}>
                    {children}
                </div>
            </Group>
        </Box>
    );
}