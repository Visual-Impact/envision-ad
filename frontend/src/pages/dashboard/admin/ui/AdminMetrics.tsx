"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Card,
  Center,
  Divider,
  Group,
  Loader,
  SimpleGrid,
  Stack,
  Text,
  Title,
} from "@mantine/core";
import { DonutChart, type DonutChartCell } from "@mantine/charts";
import {
  IconBuilding,
  IconCurrencyDollar,
  IconDeviceTv,
  IconSpeakerphone,
  IconUser,
  IconUsers,
} from "@tabler/icons-react";
import { useTranslations } from "next-intl";

import {
  getAdminOverview,
  type AdminOverviewResponse,
} from "../api";

import { MetricCard } from "@/shared/ui";

export default function AdminMetricsPage() {
  const t = useTranslations("admin.metricsPage");

  const [data, setData] = useState<AdminOverviewResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    (async () => {
      setLoading(true);
      setError(null);
      try {
        const res = await getAdminOverview();
        if (!cancelled) setData(res);
      } catch (e) {
        console.error(e);
        if (!cancelled) setError(t("errors.loadFailed"));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [t]);

  const totalRoles = useMemo(() => {
    if (!data) return 0;
    return (data.totalMediaOwners ?? 0) + (data.totalAdvertisers ?? 0);
  }, [data]);

  const donutData: DonutChartCell[] = useMemo(() => {
    if (!data) return [];

    const items: DonutChartCell[] = [
      {
        name: t("chart.mediaOwners"),
        value: Number(data.totalMediaOwners ?? 0),
        color: "blue.6",
      },
      {
        name: t("chart.advertisers"),
        value: Number(data.totalAdvertisers ?? 0),
        color: "grape.6",
      },
    ];

    return items.filter((d) => d.value > 0);
  }, [data, t]);

  const pct = useCallback(
      (value: number) => {
        if (!totalRoles) return "0%";
        return `${Math.round((value / totalRoles) * 100)}%`;
      },
      [totalRoles],
  );

  if (loading) {
    return (
        <Center py="xl" role="status" aria-live="polite" aria-label={t("loading")}>
          <Loader role="img"/>
        </Center>
    );
  }

  if (error) {
    return (
        <Stack gap="sm" p="md" style={{ flex: 1, minWidth: 0 }}>
          <Group justify="space-between" align="flex-start">
            <Stack gap={2}>
              <Title order={3}>{t("title")}</Title>
            </Stack>
          </Group>

          <Text c="red" fw={500} role="alert">
            {error}
          </Text>
        </Stack>
    );
  }

  if (!data) return <Text p="md">{t("empty")}</Text>;

  return (
      <Stack component="main" gap="md" p="md" style={{ flex: 1, minWidth: 0 }}>
        <Group justify="space-between" align="flex-start">
          <Stack gap={2}>
            <Title order={1}>{t("title")}</Title>
          </Stack>
        </Group>

        <Divider />

        <SimpleGrid cols={{ base: 1, sm: 2, lg: 3 }}>
          {/*
            This tile reports MRR, not cumulative revenue (P1 M6, decision D43). Before bundles
            it summed every CONFIRMED reservation's total_price — a lifetime figure. Its bundle
            successor sums monthly_amount across ACTIVE/PAST_DUE subscriptions, which is a
            run-rate: the same number appears again next month rather than accumulating.
          */}
          <MetricCard
              label={t("cards.monthlyRecurringRevenue")}
              value={`$${Number(data.totalPlatformRevenue ?? 0).toFixed(2)}`}
              description={t("cards.monthlyRecurringRevenueDesc")}
              icon={<IconCurrencyDollar size={18} />}
          />
          <MetricCard
              label={t("cards.totalOrganizations")}
              value={`${data.totalOrganizations ?? 0}`}
              description={t("cards.totalOrganizationsDesc")}
              icon={<IconBuilding size={18} />}
          />
          <MetricCard
              label={t("cards.totalMediaListings")}
              value={`${data.totalMediaListings ?? 0}`}
              description={t("cards.totalMediaListingsDesc")}
              icon={<IconDeviceTv size={18} />}
          />
          <MetricCard
              label={t("cards.totalUsers")}
              value={`${data.totalUsers ?? 0}`}
              description={t("cards.totalUsersDesc")}
              icon={<IconUsers size={18} />}
          />
          <MetricCard
              label={t("cards.mediaOwners")}
              value={`${data.totalMediaOwners ?? 0}`}
              description={t("cards.mediaOwnersDesc")}
              icon={<IconUser size={18} />}
          />
          <MetricCard
              label={t("cards.advertisers")}
              value={`${data.totalAdvertisers ?? 0}`}
              description={t("cards.advertisersDesc")}
              icon={<IconSpeakerphone size={18} />}
          />
        </SimpleGrid>

        <Card shadow="sm" radius="lg" p="md">
          <Title order={2} mb="xs">
            {t("chart.title")}
          </Title>
          <Text c="dimmed" size="sm" mb="md">
            {t("chart.subtitle")}
          </Text>

          <Group
              align="center"
              justify="center"
              gap="xl"
              wrap="wrap"
              style={{ width: "100%" }}
          >
            <div
              role="img"
              aria-label={t("chart.ariaLabel", {
                total: totalRoles,
                mediaOwners: data.totalMediaOwners ?? 0,
                advertisers: data.totalAdvertisers ?? 0,
              })}
            >
                <DonutChart
                    h={260}
                    data={
                      donutData.length
                          ? donutData
                          : [{ name: t("chart.noData"), value: 1, color: "gray.4" }]
                    }
                    withTooltip={donutData.length > 0}
                    tooltipDataSource="segment"
                    strokeWidth={2}
                    thickness={22}
                    chartLabel={
                      donutData.length
                          ? `${totalRoles}\n${t("chart.users")}`
                          : t("chart.noDataLabel")
                    }
                />
            </div>

            <Stack gap="xs" style={{ minWidth: 220 }}>
              <Group justify="space-between">
                <Text size="sm">{t("chart.mediaOwners")}</Text>
                <Text fw={600}>
                  {data.totalMediaOwners ?? 0} (
                  {pct(Number(data.totalMediaOwners ?? 0))})
                </Text>
              </Group>

              <Group justify="space-between">
                <Text size="sm">{t("chart.advertisers")}</Text>
                <Text fw={600}>
                  {data.totalAdvertisers ?? 0} (
                  {pct(Number(data.totalAdvertisers ?? 0))})
                </Text>
              </Group>

              <Text c="dimmed" size="xs" mt="xs">
                {t("chart.basedOn")}
              </Text>
            </Stack>
          </Group>
        </Card>
      </Stack>
  );
}
