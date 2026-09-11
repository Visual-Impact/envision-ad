"use client";

import {
  Button,
  Container,
  Stack,
  Text,
  Box,
} from "@mantine/core";
import { useParams } from "next/navigation";
import { useEffect, useMemo, useState } from "react";
import { getMediaById, SpecialSort } from "@/features/media-management";
import { useLocale, useTranslations } from "next-intl";
import { Media } from "@/entities/media";
import { MediaCardCarouselLoader, MediaCardStackLoader } from "@/widgets/media-carousel";
import { FilteredActiveMediaProps } from "@/entities/media";
import type { LatLngLiteral } from "leaflet";
import { useMediaQuery } from "@mantine/hooks";
import { MediaDetails } from "@/widgets/media-details";

export default function MediaDetailsPage() {
  const t = useTranslations("mediaPage");
  const locale = useLocale();
  const isMobile = useMediaQuery("(max-width: 575px)");

  const params = useParams();
  const id = params?.id as string | undefined;

  const [media, setMedia] = useState<Media | null>(null); //The media displayed on the page
  const [loading, setLoading] = useState(true); //Whether the media for the current page is loading or not
  const [error, setError] = useState<string | null>(null); //The error message

  useEffect(() => {
    if (!id) return;

    const loadMedia = async () => {
      try {
        setLoading(true);
        setError(null);
        const data = await getMediaById(id);
        setMedia(data);
      } catch (err: unknown) {
        const msg =
          err instanceof Error ? err.message : t("errorLoading");
        setError(msg);
      } finally {
        setLoading(false);
      }
    };

    void loadMedia();
  }, [id, t]);


  const latlng: LatLngLiteral = useMemo(() => ({
    lat: media?.mediaLocation.latitude ?? 0,
    lng: media?.mediaLocation.longitude ?? 0,
  }), [media?.mediaLocation.latitude, media?.mediaLocation.longitude]);

  const filteredOrgMediaProps: FilteredActiveMediaProps = useMemo(() => ({
    sort: SpecialSort.nearest,
    businessId: media?.businessId,
    excludedId: media?.id,
    latlng,
    page: 0,
    size: 10
  }), [latlng, media?.businessId, media?.id]);

  return (
    <>
      <Container size="lg" py={20} px={isMobile? "sm" :80}>
        <Stack gap="xl">
          {/*
            activeAdsCount is null because no public source for it survives M6. It used to count
            distinct campaigns with a CONFIRMED reservation on this screen; the bundle-era
            equivalent lives behind an ownership-checked endpoint, and null hides the line
            rather than showing a misleading zero.
          */}
          <MediaDetails media={media} loading={loading} error={error} activeAdsCount={null} >
            {/*
              Screens are no longer bought individually (P1 M6, brief req. 18) — the reserve CTA
              and its modal are gone. The purchase path is a bundle subscription, so this points
              at the home page's bundle section instead of leaving the page with no next step.
            */}
            <Box w="100%">
              {/*
                A plain anchor rather than the typed next-intl Link: the typed href object has no
                `hash` field, and the target is the home page's #bundles section (D17), not a
                route of its own. localePrefix is "always", so the locale has to be included.
              */}
              <Button
                component="a"
                href={`/${locale}#bundles`}
                radius="xl"
                fullWidth
                aria-label={t("browseBundlesButton")}
              >
                {t("browseBundlesButton")}
              </Button>
            </Box>
            <Text size="xs" c="dimmed">
              {t("browseBundlesNote")}
            </Text>
          </MediaDetails>
          <Container w="100%" p="0">
            {
              media &&
              (isMobile ?
              <MediaCardStackLoader id="other-media-by-organization-list" title={t("otherMediaBy") + media.businessName} filteredMediaProps={filteredOrgMediaProps}/>
              :
              <MediaCardCarouselLoader id="other-media-by-organization-list" title={t("otherMediaBy") + media.businessName} filteredMediaProps={filteredOrgMediaProps}/>)
            }
          </Container>
        </Stack>
      </Container>

    </>
  );
}
