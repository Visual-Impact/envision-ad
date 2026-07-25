"use client";

import { Badge, Card, Group, Stack, Text } from "@mantine/core";
import { useTranslations } from "next-intl";
import { MediaLocation } from "@/entities/media-location";

interface MediaLocationReviewCardProps {
  mediaLocation: MediaLocation | null | undefined;
}

/**
 * Admin-only summary of the media location a pending media belongs to. The
 * region drives bundle grouping, so it is called out explicitly (including when
 * the media owner left it empty) rather than buried in the address.
 */
export function MediaLocationReviewCard({
  mediaLocation,
}: MediaLocationReviewCardProps) {
  const t = useTranslations("admin.adminActions.mediaLocationReview");

  if (!mediaLocation) {
    return null;
  }

  const address = [
    mediaLocation.street,
    mediaLocation.city,
    mediaLocation.province,
    mediaLocation.postalCode,
    mediaLocation.country,
  ]
    .filter(Boolean)
    .join(", ");

  const region = mediaLocation.region?.trim();

  const rows: [string, string][] = [[t("name"), mediaLocation.name || "N/A"]];

  rows.push([t("address"), address || "N/A"]);

  return (
    <Card withBorder radius="lg" p="lg">
      <Stack gap="sm">
        <Text fw={600}>{t("title")}</Text>
        <Text size="sm" c="dimmed">
          {t("description")}
        </Text>

        <Group justify="space-between" wrap="nowrap" align="flex-start">
          <Text size="sm" c="dimmed">
            {t("region")}:
          </Text>
          {region ? (
            <Badge color="blue" variant="light" size="lg">
              {region}
            </Badge>
          ) : (
            <Badge color="gray" variant="outline" size="lg">
              {t("regionNotSet")}
            </Badge>
          )}
        </Group>

        {rows.map(([label, value]) => (
          <Group key={label} justify="space-between" wrap="nowrap" align="flex-start">
            <Text size="sm" c="dimmed">
              {label}:
            </Text>
            <Text size="sm" ta="right">
              {value}
            </Text>
          </Group>
        ))}
      </Stack>
    </Card>
  );
}
