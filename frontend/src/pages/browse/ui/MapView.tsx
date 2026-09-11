import { MapContainer, Marker, Popup, TileLayer, useMap} from 'react-leaflet';
import {  Drawer, Paper, Stack, Title } from '@mantine/core';
import 'leaflet/dist/leaflet.css';
import L, { LatLngLiteral, Map } from 'leaflet';
import { useEffect, useMemo, useRef, useState } from 'react';
import { MediaCard, type MediaCardProps } from '@/entities/media';
import './MapView.css';
import { MediaCardCarousel } from '@/widgets/media-carousel';
import { IconDeviceDesktop } from '@tabler/icons-react';
import { renderToString } from 'react-dom/server';
import { useDisclosure } from '@mantine/hooks';
import { useTranslations } from 'next-intl';
import { getJoinedAddress } from '@/entities/media';

interface MapViewProps {
  center: LatLngLiteral;
  zoom: number;
  medias?: MediaCardProps[][];
  setMap: React.Dispatch<React.SetStateAction<Map|null>>;
  isMobile?: boolean;
  isMobileVertical?: boolean;
  
}

interface MediaMarkerProps{
  media: MediaCardProps[];
  isMobileVertical: boolean;
  open: () => void;
  setMediaList: React.Dispatch<React.SetStateAction<MediaCardProps[]>>;
  setAddressName: React.Dispatch<React.SetStateAction<string>>;
}



function MediaMarker({media, isMobileVertical, open, setMediaList, setAddressName}: MediaMarkerProps){
  const sortedMedia = [...media].sort((a, b) => a.price - b.price);
  const mediaCount = sortedMedia.length;
  const map = useMap();
  const markerRef = useRef<L.Marker>(null);
  const popupRef = useRef<L.Popup>(null);
  const iconHtml = renderToString(<IconDeviceDesktop/>);
  const location = sortedMedia[0].mediaLocation;
  const address = getJoinedAddress([location?.street, location?.city, location?.province] )


  const mediaMarkerIcon = L.divIcon({
    className: 'media-marker',
    html: `<a id='MediaMarker${location?.businessId}'><span>${iconHtml}${mediaCount}</span></a>`,
    iconSize: [60, 24]
  });

  useEffect(() => {
    const marker = markerRef.current;
    if (!marker) return;

    const handleClick = () => {
      if (isMobileVertical) {
        setMediaList(media);
        setAddressName(address);
        open();
      }
    };

    const handlePopupOpen = () => {
      if (!isMobileVertical) {
        const popup = popupRef.current;
        if (!popup) return;

        requestAnimationFrame(() => {
          const popupEl = popup.getElement();
          const markerEl = marker.getElement();
          if (!popupEl || !markerEl) return;

          const markerRect = markerEl.getBoundingClientRect();
          const popupRect = popupEl.getBoundingClientRect();
          const mapRect = map.getContainer().getBoundingClientRect();

          let offsetX = 0;
          let offsetY = 0;

          if (markerRect.top < mapRect.top + mapRect.height / 2) {
            offsetY = popupRect.height + markerRect.height * 2 ;
          }

          if (popupRect.right > mapRect.right) {
            offsetX = mapRect.right - popupRect.right - 10;
          }
          if (popupRect.left < mapRect.left) {
            offsetX = mapRect.left - popupRect.left + 10;
          }
          if (popupRect.top + offsetY < mapRect.top) {
            offsetY = mapRect.top - popupRect.top + 10;
          }

          popup.options.offset = L.point(offsetX, offsetY);
          popup.update();
          });
        }
      };

    marker.on("click", handleClick);
    marker.on("popupopen", handlePopupOpen);

    return () => {
      marker.off("click", handleClick);
      marker.off("popupopen", handlePopupOpen);
    };
  
  }, [address, isMobileVertical, map, markerRef, media, open, popupRef, setAddressName, setMediaList]);

  if (mediaCount <= 0 || !location) return;

  return (
    
    <Marker ref={markerRef} position={{lat: location.latitude, lng: location.longitude}} icon={mediaMarkerIcon}>
      {
        !isMobileVertical &&
        <Popup ref={popupRef} minWidth={250} maxWidth={250} autoPan={false}>
          <MediaCardCarousel medias={media} slideSize={"100%"}/>
        </Popup>
      }
    </Marker> 
    
  )
}

export default function MapView({center, zoom, setMap, medias, isMobile, isMobileVertical}: MapViewProps){
  const t = useTranslations('browse');
  
  const [opened, { open, close }] = useDisclosure(false);
  const [mediaList, setMediaList] = useState<MediaCardProps[]>([]);
  const [addressName, setAddressName] = useState<string>("");


  const displayMap = useMemo(
    () => (
        <MapContainer
          center={center}
          zoom={zoom}
          scrollWheelZoom={true}
          ref={setMap}
          
          style={{height: "100%", width: "100%"}}>
          <TileLayer
            attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
            url="https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png"
          />
          {medias?.map((media: MediaCardProps[]) => (
            <MediaMarker key={media[0].index} media={media} isMobileVertical={isMobileVertical ?? false} setMediaList={setMediaList} open={open} setAddressName={setAddressName}/>
          ))}
        </MapContainer>
      ) ,
    [center, isMobileVertical, medias, open, setMap, zoom],
  )

    return(
        <Paper radius={isMobile ? 0 : "lg"} style={{position:"sticky", overflow: "hidden", height:(isMobile ? "100%" : "90vh")}}>
           {displayMap}
           {(isMobileVertical) &&
            <Drawer opened={opened} onClose={close} position="bottom" size={(mediaList.length > 1) ? "sm" : "xs"} withCloseButton={false} title={addressName}>
              {
                mediaList.length > 0 ?
                <Stack mt="sm">
                  {mediaList.map((m) => (
                    <MediaCard
                        key={m.index}
                        index={m.index}
                        href={m.href}
                        imageUrl={m.imageUrl}
                        title={m.title}
                        organizationId={m.organizationId}
                        organizationName={m.organizationName}
                        typeOfDisplay={m.typeOfDisplay}
                        price={m.price} 
                        dailyImpressions={m.dailyImpressions}
                        schedule={m.schedule}
                        resolution={m.resolution} 
                    />
                  ))}
                  </Stack> :
                  <Title>{t('nomedia.notfound')}</Title>
                }
            </Drawer>
          }
            
        </Paper>
    )

}