"use client";

import {
    Paper,
    Stack,
    Group,
    Skeleton,
    Divider,
    AspectRatio,
} from "@mantine/core";
import { useMediaQuery } from "@mantine/hooks";

function SkeletonMediaCard() {
    const isMobile = useMediaQuery("(max-width: 575px)");
    const isXsMobile = useMediaQuery("(max-width: 420px)");

    const imageBlock = (
        <div style={{
            width: isMobile && !isXsMobile ? 170 : "100%",
            minWidth: isMobile && !isXsMobile ? 132 : undefined,
        }}>
            {isMobile && !isXsMobile ? (
                <Skeleton height={120} radius="md" />
            ) : (
                <AspectRatio ratio={1}>
                    <Skeleton radius="md" />
                </AspectRatio>
            )}
        </div>
    );

    const contentBlock = (
        <Stack
            gap="10px"
            p={isMobile ? 0 : "10px"}
            px={isMobile ? 0 : "10px"}
            style={{ flex: 1, minWidth: 0 }}
        >
            <Stack gap="4px">
                <Skeleton height={14} width="40%" />
                <Skeleton height={20} width="85%" />
                <Skeleton height={20} width="55%" />
                <Skeleton height={14} width="50%" />
                <Skeleton height={14} width="60%" />
            </Stack>

            <Divider />

            <Stack gap="8px">
                <Skeleton height={24} width="40%" />
                <Group gap="xs">
                    <Skeleton height={18} width={55} radius="xl" />
                    <Skeleton height={18} width={60} radius="xl" />
                    <Skeleton height={18} width={70} radius="xl" />
                </Group>
            </Stack>
        </Stack>
    );

    return (
        <Paper
            shadow="sm"
            radius="md"
            h="100%"
            p={isXsMobile ? "12px" : "14px"}
        >
            {isMobile ? (
                isXsMobile ? (
                    <Stack gap="12px">
                        {imageBlock}
                        {contentBlock}
                    </Stack>
                ) : (
                    <Group gap="12px" wrap="nowrap" align="flex-start">
                        {imageBlock}
                        {contentBlock}
                    </Group>
                )
            ) : (
                <Stack gap={0}>
                    {imageBlock}
                    {contentBlock}
                </Stack>
            )}
        </Paper>
    );
}

export default SkeletonMediaCard;
