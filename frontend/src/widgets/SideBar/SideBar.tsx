import type { ComponentProps, ElementType } from "react";
import { NavLink, Stack, Accordion } from "@mantine/core";
import { Link, usePathname } from "@/shared/lib/i18n";
import {
    IconAd,
    IconDeviceTv,
    IconLayoutDashboard,
    IconUsers,
    IconShieldCheck,
    IconDiscountCheck,
    IconChartDots,
    IconFileDescription,
    IconRepeat,
    IconTag,
    IconStack2,
    IconSettings,
    IconTicket,
    IconUserPlus,
} from "@tabler/icons-react";
import { useTranslations } from "next-intl";
import { usePermissions } from "@/app/providers/PermissionProvider";
import { useOrganization } from "@/app/providers/OrganizationProvider";
import styles from "./SideBar.module.css";

function joinClassNames(...classes: Array<string | false | undefined>) {
    return classes.filter(Boolean).join(" ");
}

interface SideBarLinkProps {
    href: ComponentProps<typeof Link>["href"];
    label: string;
    icon: ElementType;
    active: boolean;
}

function SideBarLink({ href, label, icon: Icon, active }: SideBarLinkProps) {
    return (
        <NavLink
            component={Link}
            href={href}
            label={label}
            leftSection={
                <span className={joinClassNames(styles.iconChip, active && styles.iconChipActive)}>
                    <Icon size={16} stroke={1.75} />
                </span>
            }
            active={active}
            className={joinClassNames(styles.navLink, active && styles.navLinkActive)}
        />
    );
}

export default function SideBar() {
    const { permissions } = usePermissions();
    const { organization } = useOrganization();
    const pathname = usePathname();
    const t = useTranslations("sideBar");

    const mediaOwnerNavItems = organization?.roles?.mediaOwner ? [
        permissions.includes("read:media") && (
            <SideBarLink
                key="metrics"
                href="/dashboard/media-owner/metrics"
                label={t("media-owner.metrics")}
                icon={IconChartDots}
                active={!!pathname?.includes("/media-owner/metrics")}
            />
        ),
        permissions.includes("create:media") && (
            <SideBarLink
                key="media"
                href="/dashboard/media-owner/locations"
                label={t("media-owner.media")}
                icon={IconDeviceTv}
                active={!!pathname?.includes("/media-owner/locations")}
            />
        ),
        permissions.includes("create:media") && (
            <SideBarLink
                key="proof"
                href="/dashboard/media-owner/proof"
                label={t("media-owner.proof")}
                icon={IconFileDescription}
                active={!!pathname?.endsWith("/media-owner/proof")}
            />
        ),
    ].filter(Boolean) : [];

    const advertiserNavItems = organization?.roles?.advertiser ? [
        permissions.includes("read:campaign") && (
            <SideBarLink
                key="metricOverview"
                href="/dashboard/advertiser/metrics"
                label={t("advertiser.metricOverview")}
                icon={IconChartDots}
                active={pathname === "/dashboard/advertiser/metrics"}
            />
        ),
        permissions.includes("read:campaign") && (
            <SideBarLink
                key="campaigns"
                href="/dashboard/advertiser/campaigns"
                label={t("advertiser.myAds")}
                icon={IconAd}
                active={!!pathname?.endsWith("/advertiser/campaigns")}
            />
        ),
        // Gated on read:campaign rather than a new Auth0 permission — it is already the
        // advertiser-role marker, and D21 established that this flow should not be made
        // hostage to a tenant change.
        permissions.includes("read:campaign") && (
            <SideBarLink
                key="subscriptions"
                href="/dashboard/advertiser/subscriptions"
                label={t("advertiser.subscriptions")}
                icon={IconRepeat}
                active={!!pathname?.endsWith("/advertiser/subscriptions")}
            />
        ),
    ].filter(Boolean) : [];

    const adminNavItems = [
        permissions.includes("patch:media_status") && (
            <SideBarLink
                key="adminMetrics"
                href="/dashboard/admin/metrics"
                label={t("admin.metrics")}
                icon={IconChartDots}
                active={!!pathname?.includes("/dashboard/admin/metrics")}
            />
        ),
        permissions.includes("update:verification") && (
            <SideBarLink
                key="pendingMedia"
                href="/dashboard/admin/media/pending"
                label={t("admin.pendingMedia")}
                icon={IconShieldCheck}
                active={!!pathname?.includes("/dashboard/admin/media/pending")}
            />
        ),
        permissions.includes("update:verification") && (
            <SideBarLink
                key="pendingOrganizations"
                href="/dashboard/admin/organization/verification"
                label={t("admin.pendingOrganizations")}
                icon={IconDiscountCheck}
                active={!!pathname?.includes("/dashboard/admin/organization/verification")}
            />
        ),
        permissions.includes("manage:venues") && (
            <SideBarLink
                key="venues"
                href="/dashboard/admin/venues"
                label={t("admin.venues")}
                icon={IconTag}
                active={!!pathname?.includes("/dashboard/admin/venues")}
            />
        ),
        permissions.includes("manage:bundles") && (
            <SideBarLink
                key="bundles"
                href="/dashboard/admin/bundles"
                label={t("admin.bundles")}
                icon={IconStack2}
                active={!!pathname?.includes("/dashboard/admin/bundles")}
            />
        ),
        permissions.includes("manage:coupons") && (
            <SideBarLink
                key="coupons"
                href="/dashboard/admin/coupons"
                label={t("admin.coupons")}
                icon={IconTicket}
                active={!!pathname?.includes("/dashboard/admin/coupons")}
            />
        ),
        permissions.includes("manage:accounts") && (
            <SideBarLink
                key="accounts"
                href="/dashboard/admin/accounts"
                label={t("admin.accounts")}
                icon={IconUserPlus}
                active={!!pathname?.includes("/dashboard/admin/accounts")}
            />
        ),
        permissions.includes("manage:settings") && (
            <SideBarLink
                key="settings"
                href="/dashboard/admin/settings"
                label={t("admin.settings")}
                icon={IconSettings}
                active={!!pathname?.includes("/dashboard/admin/settings")}
            />
        ),
    ].filter(Boolean);

    return (
        <Accordion
            multiple
            variant="separated"
            defaultValue={["organization", "media-owner", "advertiser", "admin"]}
            className={styles.accordion}
            classNames={{
                item: styles.accordionItem,
                control: styles.accordionControl,
                label: styles.accordionLabel,
                panel: styles.accordionPanel,
            }}
        >
            {advertiserNavItems.length > 0 && (
                <Accordion.Item value="advertiser">
                    <Accordion.Control>{t("advertiserTitle")}</Accordion.Control>
                    <Accordion.Panel>
                        <Stack gap="xs">{advertiserNavItems}</Stack>
                    </Accordion.Panel>
                </Accordion.Item>
            )}

            {mediaOwnerNavItems.length > 0 && (
                <Accordion.Item value="media-owner">
                    <Accordion.Control>{t("mediaOwnerTitle")}</Accordion.Control>
                    <Accordion.Panel>
                        <Stack gap="xs">{mediaOwnerNavItems}</Stack>
                    </Accordion.Panel>
                </Accordion.Item>
            )}

            {adminNavItems.length === 0 && (
                <Accordion.Item value="organization">
                    <Accordion.Control>{t("organizationTitle")}</Accordion.Control>
                    <Accordion.Panel>
                        <Stack gap="xs">
                            <SideBarLink
                                key="organizationOverview"
                                href="/dashboard/organization/overview"
                                label={t("organization.overview")}
                                icon={IconLayoutDashboard}
                                active={!!pathname?.endsWith("/organization/overview")}
                            />
                            {permissions.includes("read:employee") && (
                                <SideBarLink
                                    key="employees"
                                    href="/dashboard/organization/employees"
                                    label={t("organization.employees")}
                                    icon={IconUsers}
                                    active={!!pathname?.endsWith("/organization/employees")}
                                />
                            )}
                        </Stack>
                    </Accordion.Panel>
                </Accordion.Item>
            )}

            {adminNavItems.length > 0 && (
                <Accordion.Item value="admin">
                    <Accordion.Control>{t("adminTitle")}</Accordion.Control>
                    <Accordion.Panel>
                        <Stack gap="xs">{adminNavItems}</Stack>
                    </Accordion.Panel>
                </Accordion.Item>
            )}
        </Accordion>
    );
}
