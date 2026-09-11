"use client";

import React from "react";
import Image from "next/image";
import { Group, Text, ActionIcon } from "@mantine/core";
import { IconBrandLinkedin, IconBrandInstagram } from "@tabler/icons-react";
import { notifications } from "@mantine/notifications";
import { useTranslations } from "next-intl";
import { Link } from "@/shared/lib/i18n";
import styles from "./Footer.module.css";

interface FooterProps {
    bookMeetingUrl: string | null;
}

export default function Footer({ bookMeetingUrl }: FooterProps) {
    const t = useTranslations("footer");
    const tNav = useTranslations("nav");

    const handleBookMeeting = (e: React.MouseEvent) => {
        if (!bookMeetingUrl) {
            e.preventDefault();
            notifications.show({
                title: tNav("bookMeetingUrlNotSet"),
                message: "",
                color: "red",
            });
        }
    };

    return (
        <footer className={styles.footer}>
            <div className={styles.gradientBar} aria-hidden="true" />

            <div className={styles.container}>
                <div className={styles.grid}>
                    {/* Brand */}
                    <div className={styles.brandSection}>
                        <Link href="/" className={styles.brandLink}>
                            <Group gap="sm" align="center" wrap="nowrap">
                                <div className={styles.logoBadge}>
                                    <Image
                                        src="/images/logo-envision-ad.png"
                                        alt={tNav("images.envisionAdLogo.alt")}
                                        width={28}
                                        height={28}
                                        style={{ objectFit: "contain" }}
                                    />
                                </div>
                                <Text component="span" size="lg" fw={700} className={styles.brandName}>
                                    {tNav("platformName")}
                                </Text>
                            </Group>
                        </Link>
                        <Text className={styles.tagline}>{t("tagline")}</Text>
                    </div>

                    {/* Quick Links */}
                    <div className={styles.section}>
                        <h2 className={styles.heading}>{t("quickLinks")}</h2>
                        <ul className={styles.list}>
                            <li><Link href="/">{tNav("home")}</Link></li>
                            <li><Link href="/browse">{tNav("browse")}</Link></li>
                            <li>
                                <a
                                    href={bookMeetingUrl ?? "#"}
                                    target={bookMeetingUrl ? "_blank" : undefined}
                                    rel={bookMeetingUrl ? "noopener noreferrer" : undefined}
                                    onClick={handleBookMeeting}
                                >
                                    {tNav("bookMeeting")}
                                </a>
                            </li>
                        </ul>
                    </div>

                    {/* Contact Information */}
                    <div className={styles.section}>
                        <h2 className={styles.heading}>{t("contactUs")}</h2>
                        <ul className={styles.list}>
                            <li>{t("email")}</li>
                        </ul>
                    </div>

                    {/* Social Media */}
                    <div className={styles.section}>
                        <h2 className={styles.heading}>{t("followUs")}</h2>
                        <Group gap="sm" className={styles.socialGroup}>
                            <ActionIcon
                                component="a"
                                href="https://www.linkedin.com/company/visual-impact-lhamidi/"
                                target="_blank"
                                rel="noopener noreferrer"
                                aria-label={t("linkedin")}
                                variant="default"
                                radius="xl"
                                size={40}
                                className={styles.socialIcon}
                            >
                                <IconBrandLinkedin size={20} stroke={1.6} />
                            </ActionIcon>
                            <ActionIcon
                                component="a"
                                href="https://www.instagram.com/impactvisuel_/"
                                target="_blank"
                                rel="noopener noreferrer"
                                aria-label={t("instagram")}
                                variant="default"
                                radius="xl"
                                size={40}
                                className={styles.socialIcon}
                            >
                                <IconBrandInstagram size={20} stroke={1.6} />
                            </ActionIcon>
                        </Group>
                    </div>
                </div>

                {/* Bottom bar */}
                <div className={styles.bottomBar}>
                    <Text className={styles.copyright}>
                        {t("copyright", { year: new Date().getFullYear() })}
                    </Text>
                    <Link href="/privacy" className={styles.legalLink}>
                        {t("privacy")}
                    </Link>
                </div>
            </div>
        </footer>
    );
}
