"use client";

import React from "react";
import { Button, Checkbox, Group, Select, Stack, Switch, Text } from "@mantine/core";
import type { MediaFormState } from "@/pages/dashboard/media-owner/model/useMediaForm";

import { useTranslations } from "next-intl";

const WEEK_DAYS = [
  "Monday",
  "Tuesday",
  "Wednesday",
  "Thursday",
  "Friday",
  "Saturday",
  "Sunday",
] as const;

// Build 30-minute time slots ("00:00" … "23:30"). Using a fixed option list
// guarantees every value is a valid "HH:mm" string, so malformed times can
// never reach the backend.
const TIME_OPTIONS: string[] = (() => {
  const out: string[] = [];
  for (let h = 0; h < 24; h++) {
    for (const m of [0, 30]) {
      out.push(`${String(h).padStart(2, "0")}:${String(m).padStart(2, "0")}`);
    }
  }
  return out;
})();

// Ensures a pre-existing value (e.g. an off-slot "09:15" saved before this
// picker existed) stays selectable instead of blanking out.
const optionsWith = (value: string | null | undefined): string[] =>
  value && !TIME_OPTIONS.includes(value)
    ? [...TIME_OPTIONS, value].sort()
    : TIME_OPTIONS;

interface ScheduleSelectorProps {
  formState: MediaFormState;
  onFieldChange: <K extends keyof MediaFormState>(
    field: K,
    value: MediaFormState[K]
  ) => void;
  onDayTimeChange: (day: string, part: "start" | "end", value: string) => void;
}

export function ScheduleSelector({
  formState,
  onFieldChange,
  onDayTimeChange,
}: ScheduleSelectorProps) {
  const t = useTranslations("mediaModal");

  // Quick-fill controls: pick one start/end and stamp it onto every open day.
  const [bulkStart, setBulkStart] = React.useState<string | null>("09:00");
  const [bulkEnd, setBulkEnd] = React.useState<string | null>("17:00");

  const setDayActive = (day: string, active: boolean) => {
    onFieldChange("activeDaysOfWeek", {
      ...formState.activeDaysOfWeek,
      [day]: active,
    });
  };

  const applyHoursToOpenDays = (start: string, end: string) => {
    WEEK_DAYS.forEach((day) => {
      if (formState.activeDaysOfWeek[day]) {
        onDayTimeChange(day, "start", start);
        onDayTimeChange(day, "end", end);
      }
    });
  };

  const applyPreset = (
    days: readonly string[],
    start: string,
    end: string
  ) => {
    const nextActive: Record<string, boolean> = {};
    WEEK_DAYS.forEach((d) => (nextActive[d] = days.includes(d)));
    onFieldChange("activeDaysOfWeek", nextActive);
    days.forEach((day) => {
      onDayTimeChange(day, "start", start);
      onDayTimeChange(day, "end", end);
    });
  };

  const allMonthsSelected = Object.values(formState.activeMonths).every(Boolean);

  return (
    <Stack gap="lg">
      {/* MONTHS */}
      <Stack gap="xs">
        <Group justify="space-between" align="center">
          <Text size="md" fw={600}>{t("sections.months")}</Text>
          <Button
            size="compact-xs"
            variant="subtle"
            onClick={() => {
              const next: Record<string, boolean> = {};
              Object.keys(formState.activeMonths).forEach(
                (m) => (next[m] = !allMonthsSelected)
              );
              onFieldChange("activeMonths", next);
            }}
          >
            {allMonthsSelected ? t("sections.clearAll") : t("sections.selectAll")}
          </Button>
        </Group>
        <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
          {Object.keys(formState.activeMonths).map((m) => (
            <Checkbox
              key={m}
              label={t(`calendar.months.${m.toLowerCase()}`)}
              checked={!!formState.activeMonths[m]}
              onChange={(e) =>
                onFieldChange("activeMonths", {
                  ...formState.activeMonths,
                  [m]: (e.target as HTMLInputElement).checked,
                })
              }
            />
          ))}
        </div>
      </Stack>

      {/* SCHEDULE */}
      <Stack gap="xs">
        <Text size="md" fw={600}>{t("sections.schedule")}</Text>

        {/* Presets */}
        <Group gap="xs">
          <Button
            size="compact-xs"
            variant="light"
            onClick={() => applyPreset(WEEK_DAYS, "00:00", "23:30")}
          >
            {t("sections.preset24h")}
          </Button>
          <Button
            size="compact-xs"
            variant="light"
            onClick={() =>
              applyPreset(
                ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday"],
                "09:00",
                "17:00"
              )
            }
          >
            {t("sections.presetBusiness")}
          </Button>
        </Group>

        {/* Quick fill */}
        <Group
          gap="xs"
          align="flex-end"
          wrap="nowrap"
          style={{
            background: "var(--mantine-color-gray-0)",
            border: "1px solid var(--mantine-color-gray-2)",
            borderRadius: 8,
            padding: 8,
          }}
        >
          <Stack gap={2} style={{ flex: 1, minWidth: 0 }}>
            <Text size="xs" c="dimmed">{t("sections.quickFill")}</Text>
            <Group gap="xs" wrap="nowrap">
              <Select
                aria-label={t("sections.start")}
                data={TIME_OPTIONS}
                value={bulkStart}
                onChange={setBulkStart}
                searchable
                comboboxProps={{ withinPortal: true }}
                maxDropdownHeight={220}
                style={{ flex: 1 }}
              />
              <Text size="sm" c="dimmed">–</Text>
              <Select
                aria-label={t("sections.end")}
                data={TIME_OPTIONS}
                value={bulkEnd}
                onChange={setBulkEnd}
                searchable
                comboboxProps={{ withinPortal: true }}
                maxDropdownHeight={220}
                style={{ flex: 1 }}
              />
            </Group>
          </Stack>
          <Button
            size="sm"
            variant="default"
            disabled={!bulkStart || !bulkEnd}
            onClick={() => {
              if (bulkStart && bulkEnd) applyHoursToOpenDays(bulkStart, bulkEnd);
            }}
          >
            {t("sections.applyToAll")}
          </Button>
        </Group>

        {/* Day rows */}
        <Stack gap={6} mt={4}>
          {WEEK_DAYS.map((day) => (
            <DayRow
              key={day}
              weekDay={day}
              formState={formState}
              onToggle={setDayActive}
              onDayTimeChange={onDayTimeChange}
            />
          ))}
        </Stack>
      </Stack>
    </Stack>
  );
}

function DayRow({
  weekDay,
  formState,
  onToggle,
  onDayTimeChange,
}: {
  weekDay: string;
  formState: MediaFormState;
  onToggle: (day: string, active: boolean) => void;
  onDayTimeChange: (day: string, part: "start" | "end", value: string) => void;
}) {
  const t = useTranslations("mediaModal");
  const isActive = !!formState.activeDaysOfWeek[weekDay];
  const dayLabel = t(`calendar.days.${weekDay.toLowerCase()}`);
  const hours = formState.dailyOperatingHours[weekDay] ?? { start: "", end: "" };

  const startError = formState.errors[`${weekDay}_start`];
  const endError = formState.errors[`${weekDay}_end`];

  return (
    <Group
      gap="sm"
      wrap="nowrap"
      align="center"
      style={{
        border: "1px solid var(--mantine-color-gray-2)",
        borderRadius: 8,
        padding: "8px 12px",
        opacity: isActive ? 1 : 0.65,
      }}
    >
      <Switch
        checked={isActive}
        onChange={(e) => onToggle(weekDay, e.currentTarget.checked)}
        label={dayLabel}
        styles={{ label: { fontWeight: 500 } }}
        style={{ width: 130, flexShrink: 0 }}
      />

      {isActive ? (
        <Group gap="xs" wrap="nowrap" style={{ flex: 1 }}>
          <Select
            aria-label={`${dayLabel} ${t("sections.start")}`}
            placeholder={t("sections.start")}
            data={optionsWith(hours.start)}
            value={hours.start || null}
            onChange={(v) => onDayTimeChange(weekDay, "start", v ?? "")}
            searchable
            comboboxProps={{ withinPortal: true }}
            maxDropdownHeight={220}
            error={startError || undefined}
            style={{ flex: 1 }}
          />
          <Text size="sm" c="dimmed">–</Text>
          <Select
            aria-label={`${dayLabel} ${t("sections.end")}`}
            placeholder={t("sections.end")}
            data={optionsWith(hours.end)}
            value={hours.end || null}
            onChange={(v) => onDayTimeChange(weekDay, "end", v ?? "")}
            searchable
            comboboxProps={{ withinPortal: true }}
            maxDropdownHeight={220}
            error={endError || undefined}
            style={{ flex: 1 }}
          />
        </Group>
      ) : (
        <Text size="sm" c="dimmed" style={{ flex: 1 }}>
          {t("sections.closed")}
        </Text>
      )}
    </Group>
  );
}
