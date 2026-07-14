"use client";

import { Box, Container, Title, Text } from "@mantine/core";
import {
    IconCalendarEvent,
    IconUpload,
    IconShieldCheck,
    IconChartLine,
    IconRocket,
    type TablerIcon,
} from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import classes from "./AdvertiserTimeline.module.css";

const STEP_ICONS: TablerIcon[] = [IconCalendarEvent, IconUpload, IconShieldCheck, IconChartLine, IconRocket];

interface AdvertiserTimelineProps {
    /** When set (together with onBookMeetingClick), step 1 links out like the hero CTA. */
    bookMeetingUrl?: string | null;
    onBookMeetingClick?: (e: React.MouseEvent) => void;
}

// A comet continuously sweeps the rail from step 1 to step 5 (pure CSS,
// looping forever) and each step's big number pulses in turn as it passes —
// see the keyframes in AdvertiserTimeline.module.css. No scroll-triggered
// JS: prefers-reduced-motion freezes both to a static state via CSS alone.
export function AdvertiserTimeline({ bookMeetingUrl, onBookMeetingClick }: AdvertiserTimelineProps) {
    const t = useTranslations("homepage.timeline");
    const isLinkable = Boolean(onBookMeetingClick);

    const steps = [1, 2, 3, 4, 5].map((n) => ({
        number: n,
        Icon: STEP_ICONS[n - 1],
        title: t(`steps.step${n}.title`),
        description: t(`steps.step${n}.description`),
        isAbove: n % 2 === 1,
    }));

    return (
        <Box component="section" className={classes.section}>
            <Container size={1480} className={classes.container}>
                <Title order={2} className={`${classes.title} ${classes.heading}`}>
                    {t("titlePart1")} <span className={classes.titleGradient}>{t("titlePart2")}</span>
                </Title>
                <Text size="lg" c="gray.6" className={classes.subtitle}>
                    {t("subtitle")}
                </Text>

                <div className={classes.rail}>
                    <div className={classes.railTrack} aria-hidden="true" />
                    <div className={classes.railComet} aria-hidden="true">
                        <div className={classes.railCometBar} />
                    </div>

                    {steps.map(({ number, Icon, title, description, isAbove }) => {
                        const numeralBlock = (
                            <span className={classes.numeral} aria-hidden="true">
                                {number}
                            </span>
                        );
                        const bubbleBlock = (
                            <div className={classes.bubble}>
                                <Text className={classes.stepTitle}>{title}</Text>
                                <Text className={classes.stepDescription}>{description}</Text>
                            </div>
                        );

                        return (
                            <div
                                key={number}
                                className={classes.node}
                                style={{ "--step": number } as React.CSSProperties}
                            >
                                <div className={`${classes.topSlot} ${isAbove ? classes.slotBubble : classes.slotNumeral}`}>
                                    {isAbove ? (
                                        <>
                                            {bubbleBlock}
                                            <div className={classes.stem} aria-hidden="true" />
                                        </>
                                    ) : (
                                        numeralBlock
                                    )}
                                </div>

                                <div className={classes.iconCell}>
                                    {number === 1 && isLinkable ? (
                                        <a
                                            href={bookMeetingUrl ?? "#"}
                                            target={bookMeetingUrl ? "_blank" : undefined}
                                            rel={bookMeetingUrl ? "noopener noreferrer" : undefined}
                                            onClick={onBookMeetingClick}
                                            className={classes.iconCircle}
                                            aria-label={title}
                                        >
                                            <Icon size={32} stroke={1.7} />
                                        </a>
                                    ) : (
                                        <div className={classes.iconCircle}>
                                            <Icon size={32} stroke={1.7} />
                                        </div>
                                    )}
                                </div>

                                <div className={`${classes.bottomSlot} ${!isAbove ? classes.slotBubble : classes.slotNumeral}`}>
                                    {!isAbove ? (
                                        <>
                                            <div className={classes.stem} aria-hidden="true" />
                                            {bubbleBlock}
                                        </>
                                    ) : (
                                        numeralBlock
                                    )}
                                </div>
                            </div>
                        );
                    })}
                </div>
            </Container>
        </Box>
    );
}
