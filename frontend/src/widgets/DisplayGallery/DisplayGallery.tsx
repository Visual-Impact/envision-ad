"use client";

import { useRef, useState } from "react";
import { Carousel } from "@mantine/carousel";
import type { EmblaCarouselType } from "embla-carousel";
import AutoScroll from "embla-carousel-auto-scroll";
import { Box, Container, Stack, Title, ActionIcon, Group, Image } from "@mantine/core";
import { IconChevronLeft, IconChevronRight } from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import classes from "./DisplayGallery.module.css";
import "@mantine/carousel/styles.css";

// Bundled fallback photos, shown until an admin uploads a custom set via
// Dashboard → App Settings. Files live in public/images/home-page-gallery-pictures.
const DEFAULT_IMAGES = [
    "IMG_5900.jpg",
    "IMG_6006.jpg",
    "IMG_6095.jpg",
    "IMG_6148.jpg",
    "IMG_5669.jpg",
    "IMG_6101.jpg",
].map((file) => `/images/home-page-gallery-pictures/${file}`);

interface DisplayGalleryProps {
    /** Admin-managed Cloudinary image URLs. When empty, the bundled defaults show. */
    images?: string[];
}

// Admin-managed via Dashboard → App Settings → Homepage Gallery.
export function DisplayGallery({ images }: DisplayGalleryProps) {
    const t = useTranslations("homepage.gallery");
    const [embla, setEmbla] = useState<EmblaCarouselType | null>(null);
    const slides = images && images.length > 0 ? images : DEFAULT_IMAGES;

    // Stable plugin instance (embla plugins must not be recreated every render).
    // Slow continuous drift; pauses while the admin/visitor drags, then resumes.
    const autoScroll = useRef(AutoScroll({ speed: 0.6, stopOnInteraction: false, stopOnMouseEnter: true }));

    return (
        <Box component="section" className={classes.section}>
            <Container size={1480} className={classes.container}>
                <Group justify="space-between" align="flex-end" wrap="nowrap" className={classes.header}>
                    <Stack gap={4} className={classes.heading}>
                        <Title order={2} className={classes.title}>
                            {t("titlePart1")}
                        </Title>
                        <Title order={2} className={`${classes.title} ${classes.titleGradient}`}>
                            {t("titlePart2")}
                        </Title>
                    </Stack>
                </Group>

                <Carousel
                    classNames={{ root: classes.carousel, viewport: classes.viewport, container: classes.slides }}
                    withControls={false}
                    slideSize={{ base: "78%", xs: "42%", sm: "32%", md: "25%" }}
                    slideGap="md"
                    emblaOptions={{ align: "start", dragFree: true, loop: true }}
                    plugins={[autoScroll.current]}
                    getEmblaApi={setEmbla}
                >
                    {slides.map((src, index) => (
                        <Carousel.Slide key={`${src}-${index}`}>
                            <div className={classes.card}>
                                    without next/image remote-domain config, like MediaCard. */}
                                <Image
                                    src={src}
                                    alt={t("imageAlt", { index: index + 1 })}
                                    className={classes.image}
                                />
                            </div>
                        </Carousel.Slide>
                    ))}
                </Carousel>

                <Group justify="flex-end" gap="sm" className={classes.controls}>
                    <ActionIcon
                        variant="default"
                        radius="xl"
                        size={48}
                        className={classes.control}
                        onClick={() => {
                            autoScroll.current.stop();
                            embla?.scrollPrev();
                        }}
                        aria-label={t("previous")}
                    >
                        <IconChevronLeft size={22} stroke={1.6} />
                    </ActionIcon>
                    <ActionIcon
                        variant="default"
                        radius="xl"
                        size={48}
                        className={classes.control}
                        onClick={() => {
                            autoScroll.current.stop();
                            embla?.scrollNext();
                        }}
                        aria-label={t("next")}
                    >
                        <IconChevronRight size={22} stroke={1.6} />
                    </ActionIcon>
                </Group>
            </Container>
        </Box>
    );
}
