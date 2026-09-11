"use client";

import React, { useMemo, useState } from "react";
import { Center, Group, Loader, Pagination, Stack, Text, Title } from "@mantine/core";
import { useTranslations } from "next-intl";
import { ApproveMediaRowData, ApproveMediaTable } from "@/pages/dashboard/admin/ui/tables/ApproveMediaTable";
import { useAdminPendingMedia } from "@/pages/dashboard/admin/model/useAdminPendingMedia";

import { MediaStatusEnum } from "@/entities/media";

const ITEMS_PER_PAGE = 20;

export default function ApprovingMediaDashboard() {
  const t = useTranslations("admin.adminActions");

  const [activePage, setActivePage] = useState(1);

  const {media, loading, error} = useAdminPendingMedia();

    const pendingRows: ApproveMediaRowData[] = useMemo(() => {
        return (media ?? []).map((m) => {
            const city = m.mediaLocation?.city ?? "";
            const province = m.mediaLocation?.province ?? "";
            const location = [city, province].filter(Boolean).join(", ") || "—";

            return {
                id: String(m.id),
                name: m.title ?? "—",
                image: m.imageUrl ?? null,
                businessName: m.businessName ?? "—",
                location,
                dailyImpressions: Number(m.dailyImpressions ?? 0),
                price: m.price != null ? `$${Number(m.price).toFixed(2)}` : "$0.00",
                status: m.status ?? MediaStatusEnum.PENDING,
            };
        });
    }, [media]);


  const totalPages = Math.max(1, Math.ceil(pendingRows.length / ITEMS_PER_PAGE));

  const paginatedRows = useMemo(() => {
    const start = (activePage - 1) * ITEMS_PER_PAGE;
    const end = start + ITEMS_PER_PAGE;
    return pendingRows.slice(start, end);
  }, [pendingRows, activePage]);

  React.useEffect(() => {
    (() => {
      if (activePage > totalPages) setActivePage(1);
    })();
  }, [activePage, totalPages]);

  return (
      <Stack component="main" gap="md" p="md" style={{flex: 1, minWidth: 0}}>
        <Group justify="space-between" align="center">
          <Title order={1}>{t("pendingMedia")}</Title>
        </Group>

        {loading ? (
            <Center role="status" aria-live="polite" py="xl" aria-label={t("loadingPendingMedia")}>
              <Loader/>
            </Center>
        ) : error ? (
            <Text c="red" fw={500} role="alert">
              {error}
            </Text>
        ) : (
            <>
              <section aria-label={t("pendingMedia")}>
                <ApproveMediaTable rows={paginatedRows} />
              </section>

              {pendingRows.length > ITEMS_PER_PAGE && (
                  <Group justify="center" mt="md">
                    <Pagination
                        total={totalPages}
                        value={activePage}
                        onChange={setActivePage}
                        size="md"
                        aria-label={t("pendingMediaPagination", {
                          start: (activePage - 1) * ITEMS_PER_PAGE + 1,
                          end: Math.min(activePage * ITEMS_PER_PAGE, pendingRows.length),
                          total: pendingRows.length,
                        })}
                    />
                  </Group>
              )}
            </>
        )}
      </Stack>
  );
}