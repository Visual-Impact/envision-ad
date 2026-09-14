import React from "react";
import { Box, Image } from "@mantine/core";
import { Ad } from "@/entities/ad";

interface AdThumbnailProps {
    ad: Ad;
    width?: number;
    height?: number;
}

/** A creative's preview at a fixed size: a muted video frame or the image, with a placeholder. */
export function AdThumbnail({ ad, width = 120, height = 68 }: AdThumbnailProps) {
    return (
        <Box
            w={width}
            h={height}
            style={{
                overflow: "hidden",
                borderRadius: "8px",
                border: "1px solid var(--mantine-color-gray-3)",
                backgroundColor: "var(--mantine-color-gray-1)",
                flexShrink: 0,
            }}
        >
            {ad.adType === "VIDEO" ? (
                <video
                    src={ad.adUrl}
                    style={{ width: "100%", height: "100%", objectFit: "cover" }}
                    muted
                    playsInline
                />
            ) : (
                <Image
                    src={ad.adUrl}
                    w="100%"
                    h="100%"
                    fit="cover"
                    alt={ad.name}
                    fallbackSrc={`https://placehold.co/${width}x${height}?text=No+Image`}
                />
            )}
        </Box>
    );
}
