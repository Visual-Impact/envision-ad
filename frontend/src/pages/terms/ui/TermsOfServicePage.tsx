"use client";

import type { ReactNode } from "react";
import { useTranslations } from "next-intl";
import { Anchor, Container, List, Stack, Text, Title } from "@mantine/core";
import { Link } from "@/shared/lib/i18n";
import { LegalParagraph as Para, LegalSection as Section } from "@/shared/ui";

// Every clause is written out here rather than mapped from the messages file, so that each
// legal judgment call can carry a LAWYER REVIEW comment right next to it (JSON can't hold
// comments). Placeholders for facts we don't have yet are "[TO CONFIRM: …]" in the copy itself.

export default function TermsOfServicePage() {
    const t = useTranslations("termsPage");

    const privacyLink = (chunks: ReactNode) => (
        <Anchor component={Link} href="/privacy">{chunks}</Anchor>
    );

    return (
        <Container size="md" py={80}>
            <Stack gap="xl">
                <Stack gap="xs">
                    <Title order={1}>{t("title")}</Title>
                    <Text c="dimmed" size="sm">{t("lastUpdated")}</Text>
                </Stack>

                {/* LAWYER REVIEW: the Terms are accepted by use only. Nothing records a click-through or timestamp at account creation (a follow-up needing a migration); is browse-wrap enough for a contract of adhesion, especially for pilots already onboarded? */}
                <Para>{t("intro")}</Para>

                <Section id="parties" title={t("parties.title")}>
                    <Para>{t("parties.p1")}</Para>
                    <Para>{t("parties.p2")}</Para>
                    {/* LAWYER REVIEW: Visual Impact is merchant of record for the whole subscription charge and pays each owner afterwards. Does the advertiser contract for advertising with Visual Impact, or with each media owner through Visual Impact as agent? That choice drives liability, refunds and the tax treatment in section 7. */}
                    <Para>{t("parties.p3")}</Para>
                </Section>

                <Section id="accounts" title={t("accounts.title")}>
                    {/* LAWYER REVIEW: B2B-only declaration, meant to keep the platform outside Quebec's Consumer Protection Act. Does it hold for sole proprietors and very small businesses, and is a self-declaration enough? */}
                    <Para>{t("accounts.p1")}</Para>
                    <Para>{t("accounts.p2")}</Para>
                    <Para>{t("accounts.p3")}</Para>
                </Section>

                <Section id="subscriptions" title={t("subscriptions.title")}>
                    <Para>{t("subscriptions.p1")}</Para>
                    <Para>{t("subscriptions.p2")}</Para>
                    {/* LAWYER REVIEW: media owners, not the platform, decide which creative from the campaign runs and when. Is that allocation of control acceptable to advertisers? */}
                    <Para>{t("subscriptions.p3")}</Para>
                    {/* LAWYER REVIEW: today nothing happens when a screen in a running subscription goes offline or is delisted. The advertiser keeps paying the locked price. Decide the remedy. */}
                    <Para>{t("subscriptions.p4")}</Para>
                </Section>

                <Section id="billing" title={t("billing.title")}>
                    <Para>{t("billing.p1")}</Para>
                    <Para>{t("billing.p2")}</Para>
                    {/* LAWYER REVIEW: the platform currently charges no GST/QST at all. Checkout sends one tax-free CAD amount. Confirm registration status and how tax must be charged and shown before live billing. */}
                    <Para>{t("billing.p3")}</Para>
                    <Para>{t("billing.p4")}</Para>
                    {/* LAWYER REVIEW: Stripe retries failed renewals on the Dashboard's schedule. The app marks the subscription past due but does not suspend ads itself. */}
                    <Para>{t("billing.p5")}</Para>
                </Section>

                <Section id="cancellation" title={t("cancellation.title")}>
                    <Para>{t("cancellation.p1")}</Para>
                    {/* LAWYER REVIEW: a no-refund clause in a contract of adhesion. Could it be abusive under Civil Code art. 1437, e.g. when screens never showed the ad? Stripe live activation also needs a published refund policy. */}
                    <Para>{t("cancellation.p2")}</Para>
                </Section>

                <Section id="media-owners" title={t("mediaOwners.title")}>
                    <Para>{t("mediaOwners.p1")}</Para>
                    {/* LAWYER REVIEW: display is manual (owners act on our emails), so the load-time deadline is the only enforceable performance standard. Pick a number the owners can actually meet. */}
                    <Para>{t("mediaOwners.p2")}</Para>
                    {/* LAWYER REVIEW: owners may refuse an ad, but the advertiser is still billed. Who decides disputes, and does a refusal affect the owner's share? */}
                    <Para>{t("mediaOwners.p3")}</Para>
                    <Para>{t("mediaOwners.p4")}</Para>
                    {/* LAWYER REVIEW: proof of display exists only for single-owner subscriptions. Multi-owner bundles (e.g. Full Network) have none, so advertisers on those get no proof. Is "other reasonable proof" on request enough? */}
                    <Para>{t("mediaOwners.p5")}</Para>
                    <Para>{t("mediaOwners.p6")}</Para>
                </Section>

                <Section id="payouts" title={t("payouts.title")}>
                    <Para>{t("payouts.p1")}</Para>
                    <Para>{t("payouts.p2")}</Para>
                    <Para>{t("payouts.p3")}</Para>
                    {/* LAWYER REVIEW: if an owner isn't fully onboarded to Stripe Connect when an invoice is paid, that payout is skipped and never retried, so the owner effectively forfeits it. Is that enforceable, or must the platform pay it later? */}
                    <Para>{t("payouts.p4")}</Para>
                    {/* LAWYER REVIEW: Stripe's required Connected Account Agreement wording for Express accounts, adapted lightly. Check it against Stripe's current text before go-live. */}
                    <Para>{t("payouts.p5")}</Para>
                    {/* LAWYER REVIEW: the fee is read from config at payout time, not locked per subscription, so a change would also hit running subscriptions. Notice period and whether owners can exit. */}
                    <Para>{t("payouts.p6")}</Para>
                    {/* LAWYER REVIEW: nothing reverses a transfer today after a refund or chargeback. A clawback or set-off right needs a contractual basis. */}
                    <Para>{t("payouts.p7")}</Para>
                    {/* LAWYER REVIEW: tax characterization of the 70% payment (owner supply to Visual Impact, or Visual Impact acting as agent) and who invoices whom for GST/QST. */}
                    <Para>{t("payouts.p8")}</Para>
                </Section>

                <Section id="content" title={t("content.title")}>
                    <Para>{t("content.p1")}</Para>
                    <Text>{t("content.listIntro")}</Text>
                    {/* LAWYER REVIEW: confirm the prohibited-content list against federal (Competition Act, Tobacco and Vaping Products Act, Cannabis Act) and Quebec rules, including CPA ss. 248–249 on advertising to children under 13, and decide on political ads. */}
                    <List spacing="xs">
                        <List.Item>{t("content.items.law")}</List.Item>
                        <List.Item>{t("content.items.misleading")}</List.Item>
                        <List.Item>{t("content.items.children")}</List.Item>
                        <List.Item>{t("content.items.regulated")}</List.Item>
                        <List.Item>{t("content.items.political")}</List.Item>
                        <List.Item>{t("content.items.offensive")}</List.Item>
                        <List.Item>{t("content.items.rights")}</List.Item>
                        <List.Item>{t("content.items.personalInfo")}</List.Item>
                    </List>
                    {/* LAWYER REVIEW: how far the Charter of the French Language reaches into on-screen ads (French only, or French markedly predominant), and who bears that risk. */}
                    <Para>{t("content.p2")}</Para>
                    {/* LAWYER REVIEW: there is no in-app approval of creatives today. Removal is a contractual right we exercise by asking owners to take an ad down. Effect on billing when we remove one. */}
                    <Para>{t("content.p3")}</Para>
                </Section>

                <Section id="intellectual-property" title={t("ip.title")}>
                    <Para>{t("ip.p1")}</Para>
                    {/* LAWYER REVIEW: licence scope, notably keeping copies after the subscription ends and use in proof-of-display pages that owners host on third-party tools such as Canva. */}
                    <Para>{t("ip.p2")}</Para>
                    {/* LAWYER REVIEW: does "promote on the Platform" need widening to marketing outside it (homepage gallery, social media)? */}
                    <Para>{t("ip.p3")}</Para>
                    <Para>{t("ip.p4")}</Para>
                </Section>

                <Section id="acceptable-use" title={t("acceptableUse.title")}>
                    <Text>{t("acceptableUse.listIntro")}</Text>
                    <List spacing="xs">
                        <List.Item>{t("acceptableUse.items.unlawful")}</List.Item>
                        <List.Item>{t("acceptableUse.items.sharing")}</List.Item>
                        <List.Item>{t("acceptableUse.items.access")}</List.Item>
                        <List.Item>{t("acceptableUse.items.disrupt")}</List.Item>
                        <List.Item>{t("acceptableUse.items.scrape")}</List.Item>
                        <List.Item>{t("acceptableUse.items.malware")}</List.Item>
                        {/* LAWYER REVIEW: a non-circumvention clause restrains trade between the two sides. Is it enforceable in Quebec, and for how long after the relationship ends? */}
                        <List.Item>{t("acceptableUse.items.circumvent")}</List.Item>
                    </List>
                </Section>

                <Section id="termination" title={t("termination.title")}>
                    <Para>{t("termination.p1")}</Para>
                    {/* LAWYER REVIEW: suspension without prior notice, and whether cancelling a subscription for breach entitles anyone to a refund or payout. */}
                    <Para>{t("termination.p2")}</Para>
                    {/* LAWYER REVIEW: billing-record retention must match the privacy policy's retention periods under Law 25. */}
                    <Para>{t("termination.p3")}</Para>
                    <Para>{t("termination.p4")}</Para>
                </Section>

                <Section id="disclaimers" title={t("disclaimers.title")}>
                    {/* LAWYER REVIEW: how far an "as is" warranty exclusion holds under the Civil Code of Québec in a B2B contract of adhesion. */}
                    <Para>{t("disclaimers.p1")}</Para>
                    <Para>{t("disclaimers.p2")}</Para>
                    <Para>{t("disclaimers.p3")}</Para>
                </Section>

                <Section id="liability" title={t("liability.title")}>
                    <Para>{t("liability.p1")}</Para>
                    {/* LAWYER REVIEW: the cap amount, and whether it should differ for advertisers (fees paid) and media owners (platform fee earned). */}
                    <Para>{t("liability.p2")}</Para>
                    {/* LAWYER REVIEW: carve-out drafted to track Civil Code art. 1474 (no exclusion for intentional or gross fault, or for bodily or moral injury). */}
                    <Para>{t("liability.p3")}</Para>
                </Section>

                <Section id="indemnity" title={t("indemnity.title")}>
                    {/* LAWYER REVIEW: a one-way indemnity in a contract of adhesion. Should it be mutual, and does a duty to defend translate into Quebec civil law? */}
                    <Para>{t("indemnity.p1")}</Para>
                </Section>

                <Section id="privacy" title={t("privacy.title")}>
                    {/* LAWYER REVIEW: the "each party is responsible for the personal information it gives the Platform" split, and its fit with the privacy policy drafted alongside these Terms (sections 3, 6 and 7 there). */}
                    <Para>{t.rich("privacy.p1", { link: privacyLink })}</Para>
                </Section>

                <Section id="governing-law" title={t("governingLaw.title")}>
                    <Para>{t("governingLaw.p1")}</Para>
                    {/* LAWYER REVIEW: exclusive forum clause, and which judicial district. */}
                    <Para>{t("governingLaw.p2")}</Para>
                    {/* LAWYER REVIEW: Charter of the French Language s. 55 (as amended by Bill 96): a contract of adhesion must be given in French first, and the parties may be bound by the English version only at their express wish. The French version is drafted to govern. Confirm that this, and the "express choice" wording, works for browse-wrap acceptance. */}
                    <Para>{t("governingLaw.p3")}</Para>
                </Section>

                <Section id="changes" title={t("changes.title")}>
                    {/* LAWYER REVIEW: unilateral amendment with acceptance by continued use. Enforceability against existing subscribers on a price-locked subscription. */}
                    <Para>{t("changes.p1")}</Para>
                </Section>

                <Section id="general" title={t("general.title")}>
                    <Para>{t("general.p1")}</Para>
                    <Para>{t("general.p2")}</Para>
                </Section>

                <Section id="contact" title={t("contact.title")}>
                    <Para>{t("contact.p1")}</Para>
                    <Stack gap={0}>
                        <Text>{t("contact.entity")}</Text>
                        <Text>{t("contact.address")}</Text>
                        <Text>{t("contact.email")}</Text>
                    </Stack>
                </Section>
            </Stack>
        </Container>
    );
}
