"use client";

import { Table, Badge, Avatar, Text } from "@mantine/core";
import { useState } from "react";
import { useLocale, useTranslations } from "next-intl";
import { MediaActions } from "./MediaActions";
import { formatCurrency } from "@/shared/lib/formatCurrency";
import { MediaStatusEnum } from "@/entities/media";
import { Venue } from "@/entities/venue";

export interface MediaRowData {
  id: string | number;
  name: string;
  image: string | null;
  adsDisplayed: number;
  pending: number;
  status: MediaStatusEnum;
  timeUntil: string;
  price: string;
  venue?: Venue | null;
}

interface MediaRowProps {
  row: MediaRowData;
  onEdit?: (id: string | number) => void;
  onDelete?: (id: string | number) => void;
  onToggleStatus?: (
      id: string | number,
      nextStatus: MediaStatusEnum.ACTIVE | MediaStatusEnum.INACTIVE
  ) => void | Promise<void>;
}

export function MediaRow({ row, onEdit, onDelete, onToggleStatus }: MediaRowProps) {
  const [hovered, setHovered] = useState(false);
  const [focusWithin, setFocusWithin] = useState(false);

  const showActions = hovered || focusWithin;

  const statusColorMap: Record<MediaStatusEnum, string> = {
    [MediaStatusEnum.ACTIVE]: "green",
    [MediaStatusEnum.INACTIVE]: "gray",
    [MediaStatusEnum.PENDING]: "yellow",
    [MediaStatusEnum.REJECTED]: "red",
  };

  function getStatusColor(status: MediaStatusEnum) {
    return statusColorMap[status];
  }

  const locale = useLocale();
  const t = useTranslations("media");

  return (
      <Table.Tr
          onMouseEnter={() => setHovered(true)}
          onMouseLeave={() => setHovered(false)}
          onFocusCapture={() => setFocusWithin(true)}
          onBlurCapture={() => setFocusWithin(false)}
          style={{ position: "relative" }}
      >
        <Table.Td>
          <Avatar src={row.image} alt={row.name} size="md" radius="md" />
        </Table.Td>

        <Table.Td>
          <Text miw={120} fw={500} size="sm">
            {row.name}
          </Text>
        </Table.Td>

        <Table.Td ta="right">
          <Text size="md" fw={700} c="var(--mantine-color-blue-9)">
            {row.adsDisplayed}
          </Text>
        </Table.Td>

        <Table.Td ta="right">
          <Text size="md" fw={700} c="dark.9">
            {row.pending}
          </Text>
        </Table.Td>

        <Table.Td ta="right">
          <Badge color={getStatusColor(row.status)} variant="light" size="md" radius="sm">
            {t("status." + String(row.status).toLowerCase())}
          </Badge>
        </Table.Td>

        <Table.Td ta="right">
          <Text size="sm" c="dimmed">
            {row.timeUntil}
          </Text>
        </Table.Td>

        <Table.Td ta="right">
          <Text size="sm" fw={700} c="var(--mantine-color-teal-9)">
            {formatCurrency(parseFloat(row.price), { locale })}
          </Text>
        </Table.Td>

        <Table.Td>
          {row.venue ? (
            <Badge color={row.venue.colorCode} variant="filled" size="sm" radius="sm">
              {locale === "fr" ? row.venue.nameFr : row.venue.nameEn}
            </Badge>
          ) : (
            <Text size="xs" c="dimmed">—</Text>
          )}
        </Table.Td>

        {/* ACTIONS COLUMN */}
        <Table.Td w={50} miw={40} ta="center">
          <div
              className="actions-menu"
              style={{
                opacity: showActions ? 1 : 0,
                pointerEvents: showActions ? "auto" : "none",
                transition: "opacity 150ms ease",
              }}
          >
            <MediaActions
                row={row}
                onEdit={onEdit}
                onDelete={onDelete}
                onToggleStatus={onToggleStatus}
            />
          </div>
        </Table.Td>
      </Table.Tr>
  );
}