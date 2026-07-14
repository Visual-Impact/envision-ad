import { Box, Group, Paper, Stack, Text, Tooltip } from "@mantine/core";
import { useMemo } from "react";
import { useLocale, useTranslations } from "next-intl";

import type {
  MediaScreenTimelineRow,
  MediaScreenTimelineSegment,
} from "@/pages/dashboard/media-owner/ui/metrics-dashboard/types";

interface MediaScreensTimelineProps {
  data: { rows: MediaScreenTimelineRow[]; startMs: number; endMs: number };
}

const LANE_HEIGHT_PX = 14;
const LANE_GAP_PX = 2;
const TRACK_PADDING_PX = 4; // top + bottom padding inside the track

/**
 * Greedy interval-scheduling: assigns each segment to the first lane where
 * it does not overlap with the segment currently occupying that lane.
 * Returns an array of { segment, lane } and the total number of lanes used.
 */
function packSegmentsIntoLanes(segments: MediaScreenTimelineSegment[]) {
  // Sort by start time so we can assign lanes left-to-right
  const sorted = [...segments].sort((a, b) => a.startDateMs - b.startDateMs);

  // laneEndMs[i] = the endDateMs of the last segment placed in lane i
  const laneEndMs: number[] = [];

  const placed: { segment: MediaScreenTimelineSegment; lane: number }[] = [];

  for (const segment of sorted) {
    // Find the first lane that ended before this segment starts
    let assigned = laneEndMs.findIndex((end) => end <= segment.startDateMs);
    if (assigned === -1) {
      // No free lane — open a new one
      assigned = laneEndMs.length;
      laneEndMs.push(segment.endDateMs);
    } else {
      laneEndMs[assigned] = segment.endDateMs;
    }
    placed.push({ segment, lane: assigned });
  }

  return { placed, laneCount: laneEndMs.length };
}

export function MediaScreensTimeline({ data }: MediaScreensTimelineProps) {
  const t = useTranslations("mediaOwnerMetrics");
  const locale = useLocale();

  const { rows, startMs, endMs } = data;

  const totalDurationMs = endMs - startMs;

  // Memoize the lane-packing result so segments are only re-sorted when
  // the underlying data actually changes, not on every render.
  const packedRows = useMemo(
    () =>
      rows.map((row) => {
        const { placed, laneCount } = packSegmentsIntoLanes(row.segments);
        const trackHeight =
          TRACK_PADDING_PX * 2 +
          laneCount * LANE_HEIGHT_PX +
          (laneCount - 1) * LANE_GAP_PX;
        return { row, placed, trackHeight };
      }),
    [rows]
  );

  if (rows.length === 0 || totalDurationMs <= 0) {
    return (
      <Text size="sm" c="dimmed">
        {t("emptyState.noData")}
      </Text>
    );
  }

  const calculatePosition = (segmentStartMs: number, segmentEndMs: number) => {
    const leftValue = Math.max(0, segmentStartMs - startMs);
    const widthValue =
      Math.min(totalDurationMs, segmentEndMs - startMs) - leftValue;

    const leftPercentage = (leftValue / totalDurationMs) * 100;
    const widthPercentage = (widthValue / totalDurationMs) * 100;

    return {
      left: `${Math.max(0, leftPercentage)}%`,
      width: `${Math.max(0, widthPercentage)}%`,
    };
  };

  const formatDate = (ms: number) =>
    new Date(ms).toLocaleDateString(locale, {
      year: "numeric",
      month: "short",
      day: "numeric",
    });


  return (
    <Stack gap="md" mt="md" w="100%" style={{ overflowX: "auto" }}>
      {packedRows.map(({ row, placed, trackHeight }) => {
        return (
          <Group key={row.mediaId} wrap="nowrap" gap="md" align="center">
            <Text size="sm" fw={500} w={120} truncate>
              {row.mediaName}
            </Text>
            <Paper
              withBorder
              radius="lg"
              bg="gray.0"
              h={trackHeight}
              style={{ flex: 1, position: "relative", overflow: "hidden" }}
            >
              {placed.map(({ segment, lane }) => {
                const { left, width } = calculatePosition(
                  segment.startDateMs,
                  segment.endDateMs
                );
                const top =
                  TRACK_PADDING_PX + lane * (LANE_HEIGHT_PX + LANE_GAP_PX);

                return (
                  <Tooltip
                    key={segment.id}
                    label={`${formatDate(segment.startDateMs)} – ${formatDate(segment.endDateMs)}`}
                    withArrow
                    position="top"
                  >
                    <Box
                      bg={row.color}
                      style={{
                        position: "absolute",
                        left,
                        width,
                        top: `${top}px`,
                        height: `${LANE_HEIGHT_PX}px`,
                        borderRadius: "3px",
                        cursor: "pointer",
                        opacity: 0.85,
                      }}
                    />
                  </Tooltip>
                );
              })}
            </Paper>
          </Group>
        );
      })}

      <Group justify="space-between" mt="xs" px={120 + 16}>
        <Text size="xs" c="dimmed">
          {formatDate(startMs)}
        </Text>
        <Text size="xs" c="dimmed">
          {formatDate(endMs)}
        </Text>
      </Group>
    </Stack>
  );
}
