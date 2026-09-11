'use client'

import {ActionIcon, Autocomplete, Button, Container, Group, Loader, Pagination, Stack, Text, TextInput} from '@mantine/core';
import { MediaCardGrid, SkeletonMediaCardGrid } from './CardGrid';
import BrowseActions from './BrowseActions';
import { useCallback, useEffect, useMemo, useState } from 'react';
import {SpecialSort} from "@/features/media-management";
import { FilterPricePopover, FilterValuePopover } from './FilterPopover';
import { FilterVenuePopover } from './FilterVenuePopover';
import { useTranslations } from "next-intl";
import { IconMap, IconSearch } from '@tabler/icons-react';
import { AddressDetails, GetAddressDetails, GetUserGeoLocation, SearchLocations} from '@/shared/lib/geolocation';

import type { LatLngBounds, LatLngLiteral, Map } from 'leaflet';
import { MediaStatus } from '@/entities/media';
import { LocationStatus } from '@/shared/lib/geolocation';
import { useMediaList } from '@/features/media-management';
import { SortOptions } from '@/features/media-management';
import dynamic from 'next/dynamic';

const MapView = dynamic(() => import('./MapView'), { ssr: false });
import { useMediaQuery } from '@mantine/hooks';
import { groupBy } from '@/shared/lib/groupBy';

function SearchMobileViewer({children}: Readonly<{children: React.ReactNode;}>){
    const isMobileVertical = useMediaQuery("(max-width: 575px)");
    return(
      isMobileVertical ? 
      <Stack w="100%" gap="xs">{children}</Stack>:
      <Group w="100%" grow>{children}</Group>
    )
}

function BrowsePage() {

  const isMobile = useMediaQuery("(max-width: 768px)");
  const isMobileVertical = useMediaQuery("(max-width: 575px)");
  const defaultPos = {lat: 45.516476848520064, lng: -73.52053208741675};

  const t = useTranslations('browse');
  const sortNearest = t('browseactions.sort.nearest');
  const searchLanguage = `${t('languages.primary')},${t('languages.fallback')}`
  
  // Lists
  const ITEMS_PER_PAGE = 32;
  const [activePage, setActivePage] = useState<number>(1);
  
  // Filters
  const [draftTitleFilter, setDraftTitleFilter] = useState("");
  const [titleFilter, setTitleFilter] = useState("");
  const [draftAddressSearch, setDraftAddressSearch] = useState("");
  const [addressSearch, setAddressSearch] = useState("");
  const [locationOptions, setLocationOptions] = useState<string[]>([]);
  const [minPrice, setMinPrice] = useState<number|null>(null);
  const [maxPrice, setMaxPrice] = useState<number|null>(null);
  const [minImpressions, setMinImpressions] = useState<number|null>(null);
  const [venueIds, setVenueIds] = useState<string[]>([]);
  const [location, setLocation] = useState<LatLngLiteral | null>(null);
  

  const [sortBy, setSortBy] = useState<string>(SortOptions.priceAsc);
  
  const [mediaStatus, setMediaStatus] = useState<MediaStatus>('idle');
  const [locationStatus, setLocationStatus] = useState<LocationStatus>('idle');


  // Map
  const defaultZoom = 12;
  const [mapVisible, setMapVisible] = useState<boolean>(false);
  const [map, setMap] = useState<Map|null>(null);
  const [bbox, setBbox] = useState<LatLngBounds | null>(null);
  const [draftBbox, setDraftBbox] = useState<LatLngBounds | null>(null);
  const isMobileMapVisible = useMemo(() => (mapVisible && isMobile), [isMobile, mapVisible]);

  const filteredMediaProps = useMemo(() => ({
    title: titleFilter,
    minPrice,
    maxPrice,
    minWeeklyImpressions: minImpressions,
    venueIds: venueIds.length > 0 ? venueIds : null,
    sort: sortBy,
    latLng: location,
    bounds: bbox,
    page: activePage - 1,
    size: ITEMS_PER_PAGE
  }), [titleFilter, minPrice, maxPrice, minImpressions, venueIds, sortBy, location, bbox, activePage]);

  const { medias: media, totalPages } = useMediaList({
    filteredMediaProps: filteredMediaProps,
    loadingLocation: locationStatus === 'loading',
    setMediaStatus
  });

  // Reset to first page whenever any filter (not page itself) changes
  useEffect(() => {
    (() => {
      setActivePage(1);
    })();
  }, [titleFilter, minPrice, maxPrice, minImpressions, venueIds, sortBy, location, bbox]);

  const groupedMedia = useMemo(() => {
    const groups = groupBy(media, m => m.mediaLocation?.id ?? "unknown");
    return Object.values(groups);
  }, [media]);

  useEffect(() => {
    let cancelled = false;

    async function resolveLocation() {
      // Nothing to resolve when there's no address and sort isn't "nearest"
      if (sortBy !== SpecialSort.nearest && !addressSearch) {
        setLocation(null);
        setLocationStatus('idle');
        return;
      }

      setLocationStatus('loading');

      try {
        if (
          sortBy === SpecialSort.nearest &&
          (!addressSearch || addressSearch === sortNearest)
        ) {
          const coords = await GetUserGeoLocation();
          if (!cancelled) {
            setLocation(coords);
            setLocationStatus('success');
          }
        } else {
          const address = await GetAddressDetails(addressSearch, searchLanguage);

          if (!address){
            throw new Error();
          }

          if (!cancelled) {
            const coords = { lat: address.lat, lng: address.lng };
            setLocation(coords);
            setLocationStatus('success');
            map?.setView(coords, defaultZoom);
          }
        }
      } catch (err: unknown) {
        if (cancelled) return;

        if (err instanceof GeolocationPositionError && err.code === 1) {
          setLocationStatus('denied');
          if (sortBy === SpecialSort.nearest) {
            setSortBy(SortOptions.priceAsc);
          }
        } else {
          setLocationStatus('error');
        }
      }
    }
    resolveLocation();
    return () => { cancelled = true };
  }, [addressSearch, map, searchLanguage, sortBy, sortNearest]);



  useEffect(() => {
    (() => {
      if (addressSearch) {
        setMapVisible(true);
      }
    })();
  }, [addressSearch]);


  useEffect(() => {
    const timeout = setTimeout(async () => {
        if (!draftAddressSearch) {
          return;
        }

        const results: AddressDetails[] = await SearchLocations(draftAddressSearch, searchLanguage);

        const uniqueResults: string[] = results.map((r) => r.display_name);

        const nearest: string = sortNearest;
        const newResults = [...new Set([nearest].concat(uniqueResults))];

        setLocationOptions(newResults);
      }, 100);
      return () => clearTimeout(timeout);
  }, [draftAddressSearch, searchLanguage, sortNearest]);



  const onMove = useCallback(() => {
    setDraftBbox(map ? map.getBounds(): null)
  }, [map])

  useEffect(() => {
    const applyBbox = () => {
      if (!draftBbox) {
        setBbox(null);
        return;
      }
      setBbox(draftBbox);
    };

    const timeout = setTimeout(applyBbox, draftBbox ? 300 : 0);

    return () => clearTimeout(timeout);
  }, [draftBbox])
  
  
  useEffect(() => {
    map?.on('moveend', onMove);
    map?.on('load', onMove);
  }, [map, onMove])

  useEffect(() => {
    (() => {
      if (mapVisible) {
        setDraftBbox(map ? map.getBounds() : null);
      } else {
        setDraftBbox(null);
      }
    })();
  }, [mapVisible, map])

  const hasActiveFilters = titleFilter !== "" || addressSearch !== "" || minPrice !== null || maxPrice !== null || minImpressions !== null || venueIds.length > 0;

  function resetFilters() {
    setDraftTitleFilter("");
    setTitleFilter("");
    setDraftAddressSearch("");
    setAddressSearch("");
    setMinPrice(null);
    setMaxPrice(null);
    setMinImpressions(null);
    setVenueIds([]);
  }

  function filters(){
    return(
      <>
        <FilterPricePopover id='PriceFilter' minPrice={minPrice} maxPrice={maxPrice} setMinPrice={setMinPrice} setMaxPrice={setMaxPrice} />
        <FilterValuePopover id='ImpressionsFilter' value={minImpressions} setValue={setMinImpressions} label={t('browseactions.filters.impressions')} placeholder={t('browseactions.filters.impressions')} ariaLabel='Minimum Impressions Input'/>
        <FilterVenuePopover id='VenueFilter' selectedVenueIds={venueIds} setSelectedVenueIds={setVenueIds} />
        {hasActiveFilters && (
          <Button size="xs" variant="subtle" color="red" onClick={resetFilters}>
            {t('browseactions.filters.reset')}
          </Button>
        )}
      </>
    )
  }


  return (
      <Container size={map ? 1600 : "xl"} w="100%" pt={isMobile ? 0 : 20} pb={isMobileMapVisible ? 0 : 20} px={isMobileMapVisible ? 0: "xs"}>
        <Group grow h="100%" top="0" justify='flex-start'>
          <Stack gap="xs" mih={isMobile ? "85vh": "95vh"} top="0" justify='flex-start' align={isMobileMapVisible ? 'end': 'center'}>
            <Stack pt={isMobile ? 10 : 0} gap={0} align='flex-end' px={isMobileMapVisible ? "xs" : "0"} w={"100%"} pl={isMobileMapVisible ? "55px" : 0}>
              <SearchMobileViewer>
                <Autocomplete 
                  placeholder={t('searchAddress')}
                  id='AddressSearch'
                  data={locationOptions.map((o) => o)}
                  aria-label='Address Search Input'
                  value={draftAddressSearch}
                  onChange={ setDraftAddressSearch }
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      setAddressSearch(draftAddressSearch);
                    }
                  }}
                  rightSection={
                    <ActionIcon id='AddressSearchButton' onClick={() => setAddressSearch(draftAddressSearch)} aria-label='Enter Address Search Input'>
                      <IconSearch size={16} />
                    </ActionIcon>
                  }
                  style={{zIndex: 1}}
                />
                <TextInput
                  placeholder={t('searchTitle')}
                  id='TitleSearch'
                  aria-label='Title Search Input'
                  value={draftTitleFilter}
                  onChange={(event) => setDraftTitleFilter(event.currentTarget.value)}
                  onKeyDown={(event) => {
                    if (event.key === "Enter") {
                      setTitleFilter(draftTitleFilter);
                    }
                  }}
                  rightSection={
                    <ActionIcon id='TitleSearchButton' onClick={() => setTitleFilter(draftTitleFilter)} aria-label='Enter Title Search Input'>
                      <IconSearch size={16} />
                    </ActionIcon>
                  }
                  style={{zIndex: 1}}
                />
                
              </SearchMobileViewer>
            </Stack>
              <Group grow w="100%" px={(isMobileMapVisible && !isMobileVertical) ? 10: 0}>
                <BrowseActions filters={filters()} setSortBy={setSortBy} sortSelectValue={sortBy} isMobileVertical={isMobileVertical} isMobile={isMobile} mapVisible={mapVisible}/>
              </Group>
              {isMobileMapVisible ? 
                <Container style={{position: "absolute",  width: "100%", height: "85vh"}} p="0">
                  <MapView center={location ?? defaultPos} zoom={defaultZoom} medias={groupedMedia} setMap={setMap} isMobile={isMobile} isMobileVertical={isMobileVertical}/>
                </Container> 
                :
                locationStatus === 'loading' && sortBy === SpecialSort.nearest ? (
                  <Stack h="20em" justify="center" align="center">
                    <Loader />
                  </Stack>
                ) : locationStatus === 'denied' ? (
                  <Stack h="20em" justify="center" align="center">
                    <Text>{t('nomedia.locationDenied')}</Text>
                  </Stack>
                ) : mediaStatus === 'error' ? (
                  <Stack h="20em" justify="center" align="center">
                    <Text>{t('nomedia.failedToLoad')}</Text>
                  </Stack>
                ) : mediaStatus === 'empty' ? (
                  <Stack h="20em" justify="center" align="center">
                    <Text size="32px">{t('nomedia.notfound')}</Text>
                    <Text>{t('nomedia.changefilters')}</Text>
                  </Stack>
                ) : mediaStatus === 'loading' || mediaStatus === 'idle' ? ( // 'idle' covers the gap while location resolves before the first fetch

                  <SkeletonMediaCardGrid size={(!isMobile && mapVisible) ? 2 : 1} />
                ) : (
                  <MediaCardGrid medias={media} size={(!isMobile && mapVisible) ? 2 : 1} />
                )}
                {totalPages > 1 && (
                  <Group justify="center" mt="md">
                    <Pagination
                      total={totalPages}
                      value={activePage}
                      onChange={setActivePage}
                      size="md"
                    />
                  </Group>
                )
                }
              </Stack>
                
            {(!isMobile && mapVisible) && 
              <Container p={0} style={{position: "sticky", top: "5vh", bottom: "5vh"}}>
                <MapView center={location ?? defaultPos} zoom={defaultZoom} medias={groupedMedia} setMap={setMap}/>
              </Container>
        }
        
        </Group>
        <Group pos='fixed' justify='flex-end'  mt="xl" right='3vh' bottom='3vh' w='100%'>
          <Button size='lg' rightSection={<IconMap size={30} />} onClick={() => setMapVisible(!mapVisible)} radius='xl' >
            {mapVisible ? t('closeMap') : t('openMap')}
          </Button>
        </Group>
      </Container>
  );
}

export default BrowsePage;
