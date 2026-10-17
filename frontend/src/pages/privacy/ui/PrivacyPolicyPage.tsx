"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Anchor, Container, List, Stack, Text, Title } from "@mantine/core";
import { Link } from "@/shared/lib/i18n";
import { LegalParagraph as Para, LegalSection as Section } from "@/shared/ui";

// Same structure as the Terms of Service page: every clause written out so each legal judgment
// call carries a LAWYER REVIEW comment beside it, and "[TO CONFIRM: …]" placeholders in the copy
// for facts we don't have yet.

export default function PrivacyPolicyPage() {
    const t = useTranslations("privacyPage");

    const termsLink = (chunks: ReactNode) => (
        <Anchor component={Link} href="/terms">{chunks}</Anchor>
    );

    return (
        <Container size="md" py={80}>
            <Stack gap="xl">
                <Stack gap="xs">
                    <Title order={1}>{t("title")}</Title>
                    <Text c="dimmed" size="sm">{t("lastUpdated")}</Text>
                </Stack>

                {/* LAWYER REVIEW: written for Quebec's private-sector act (as amended by Law 25). Does federal PIPEDA also apply, e.g. for out-of-province users or cross-border transfers, and should the policy say so? */}
                <Para>{t.rich("intro", { link: termsLink })}</Para>

                <Section id="who" title={t("who.title")}>
                    <Para>{t("who.p1")}</Para>
                    {/* LAWYER REVIEW: envision-docs describes media owners as "businesses or individuals", but the Terms say the Platform is for businesses only. If individuals can be media owners, they are data subjects in their own right and this framing (and Terms §2) needs revisiting. */}
                    <Para>{t("who.p2")}</Para>
                </Section>

                <Section id="officer" title={t("officer.title")}>
                    {/* LAWYER REVIEW: by default this is the person with the highest authority in the enterprise unless delegated in writing; the law requires the title and contact details to be published on the website. */}
                    <Para>{t("officer.p1")}</Para>
                </Section>

                <Section id="collect" title={t("collect.title")}>
                    <Text>{t("collect.listIntro")}</Text>
                    <List spacing="xs">
                        <List.Item>{t("collect.items.account")}</List.Item>
                        <List.Item>{t("collect.items.organization")}</List.Item>
                        <List.Item>{t("collect.items.billing")}</List.Item>
                        <List.Item>{t("collect.items.payouts")}</List.Item>
                        {/* LAWYER REVIEW: ads and screen photos can show identifiable people. The advertiser or owner, not us, collected that information; confirm the Terms' content clause (no personal information without consent) is enough to put that responsibility on them. */}
                        <List.Item>{t("collect.items.content")}</List.Item>
                        <List.Item>{t("collect.items.technical")}</List.Item>
                    </List>
                    {/* LAWYER REVIEW: the law requires disclosing any technology that identifies, locates or profiles people. True today (no analytics or trackers, verified in the code); it must be revisited if analytics are ever added. */}
                    <Para>{t("collect.p1")}</Para>
                </Section>

                <Section id="how" title={t("how.title")}>
                    {/* LAWYER REVIEW: invited employees' email addresses are collected from a colleague, not from them. Is collection from a third party (their own employer) covered without separate consent? */}
                    <Para>{t("how.p1")}</Para>
                </Section>

                <Section id="purposes" title={t("purposes.title")}>
                    <Text>{t("purposes.listIntro")}</Text>
                    <List spacing="xs">
                        <List.Item>{t("purposes.items.accounts")}</List.Item>
                        <List.Item>{t("purposes.items.service")}</List.Item>
                        <List.Item>{t("purposes.items.payments")}</List.Item>
                        <List.Item>{t("purposes.items.emails")}</List.Item>
                        <List.Item>{t("purposes.items.support")}</List.Item>
                        <List.Item>{t("purposes.items.security")}</List.Item>
                        <List.Item>{t("purposes.items.legal")}</List.Item>
                    </List>
                    {/* LAWYER REVIEW: consent model. Is consent implied by using a B2B service enough for these purposes, and do CASL rules matter if marketing emails are ever sent? */}
                    <Para>{t("purposes.p1")}</Para>
                </Section>

                <Section id="sharing" title={t("sharing.title")}>
                    {/* LAWYER REVIEW: media owners receive the advertiser's name and ads by email and keep them; once sent, we can't control their retention. Should the Terms bind owners to privacy obligations for what they receive? */}
                    <Para>{t("sharing.p1")}</Para>
                    <Text>{t("sharing.listIntro")}</Text>
                    <List spacing="xs">
                        <List.Item>{t("sharing.items.auth0")}</List.Item>
                        <List.Item>{t("sharing.items.stripe")}</List.Item>
                        <List.Item>{t("sharing.items.cloudinary")}</List.Item>
                        <List.Item>{t("sharing.items.aws")}</List.Item>
                        <List.Item>{t("sharing.items.email")}</List.Item>
                        <List.Item>{t("sharing.items.maps")}</List.Item>
                    </List>
                    {/* LAWYER REVIEW: the law requires a written agreement with each service provider covering protection of the information. Confirm the providers' standard DPAs qualify and are accepted. */}
                    <Para>{t("sharing.p2")}</Para>
                </Section>

                <Section id="outside-quebec" title={t("outsideQuebec.title")}>
                    {/* LAWYER REVIEW: a privacy impact assessment is required before communicating personal information outside Quebec (s. 17). Has one been done for Auth0, Stripe, Cloudinary and the email provider? The policy states that we do it. */}
                    <Para>{t("outsideQuebec.p1")}</Para>
                </Section>

                <Section id="retention" title={t("retention.title")}>
                    {/* LAWYER REVIEW: retention periods, which must match Terms §11 (billing records kept as long as the law requires). Tax rules usually mean 6 years for billing records; confirm. */}
                    <Para>{t("retention.p1")}</Para>
                </Section>

                <Section id="cookies" title={t("cookies.title")}>
                    <Text>{t("cookies.listIntro")}</Text>
                    <List spacing="xs">
                        <List.Item>{t("cookies.items.session")}</List.Item>
                        <List.Item>{t("cookies.items.locale")}</List.Item>
                        <List.Item>{t("cookies.items.stripe")}</List.Item>
                    </List>
                    {/* LAWYER REVIEW: only strictly necessary cookies are used, so no consent banner is shown. Confirm none is needed. */}
                    <Para>{t("cookies.p1")}</Para>
                </Section>

                <Section id="security" title={t("security.title")}>
                    <Para>{t("security.p1")}</Para>
                    {/* LAWYER REVIEW: this commits us to an incident register and notification process. Confirm one exists before publishing. */}
                    <Para>{t("security.p2")}</Para>
                </Section>

                <Section id="rights" title={t("rights.title")}>
                    <Text>{t("rights.listIntro")}</Text>
                    <List spacing="xs">
                        <List.Item>{t("rights.items.access")}</List.Item>
                        <List.Item>{t("rights.items.correct")}</List.Item>
                        <List.Item>{t("rights.items.withdraw")}</List.Item>
                        {/* LAWYER REVIEW: data portability (in force since September 2024). There is no export feature in the app, so it would be fulfilled by hand. */}
                        <List.Item>{t("rights.items.portability")}</List.Item>
                        <List.Item>{t("rights.items.explain")}</List.Item>
                    </List>
                    <Para>{t("rights.p1")}</Para>
                    <Para>{t("rights.p2")}</Para>
                </Section>

                <Section id="minors" title={t("minors.title")}>
                    <Para>{t("minors.p1")}</Para>
                </Section>

                <Section id="changes" title={t("changes.title")}>
                    {/* LAWYER REVIEW: the law also requires publishing the enterprise's governance policies for personal information (or a summary of them). Is a separate page needed in addition to this policy? */}
                    <Para>{t("changes.p1")}</Para>
                </Section>

                <Section id="contact" title={t("contact.title")}>
                    <Para>{t("contact.p1")}</Para>
                    <Stack gap={0}>
                        <Text>{t("contact.entity")}</Text>
                        <Text>{t("contact.address")}</Text>
                        <Text>{t("contact.email")}</Text>
                    </Stack>
                </Section>

                <Text c="dimmed">{t.rich("termsLink", { link: termsLink })}</Text>
            </Stack>
        </Container>
    );
}
