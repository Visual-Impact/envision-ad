"use client";

import { MapContainer, Marker, TileLayer, useMapEvents } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import L, { LatLngLiteral, LeafletMouseEvent } from "leaflet";
import { renderToString } from "react-dom/server";
import { IconMapPin } from "@tabler/icons-react";
import { Paper } from "@mantine/core";

interface PinDropMapProps {
    center: LatLngLiteral;
    value: LatLngLiteral | null;
    onChange: (position: LatLngLiteral) => void;
    height?: number | string;
}

const pinIcon = L.divIcon({
    className: "pin-drop-marker",
    html: renderToString(<IconMapPin size={32} stroke={2} color="#e03131" fill="#fff" />),
    iconSize: [32, 32],
    iconAnchor: [16, 32],
});

function ClickHandler({ onChange }: { onChange: (position: LatLngLiteral) => void }) {
    useMapEvents({
        click(event: LeafletMouseEvent) {
            onChange({ lat: event.latlng.lat, lng: event.latlng.lng });
        },
    });
    return null;
}

/**
 * Fallback for addresses OpenStreetMap can't resolve: the media owner places the
 * pin themselves, and those coordinates are trusted as-is (manualCoordinates) —
 * no re-geocoding is attempted.
 */
export function PinDropMap({ center, value, onChange, height = 260 }: PinDropMapProps) {
    return (
        <Paper withBorder radius="md" style={{ overflow: "hidden" }}>
            <MapContainer
                center={value ?? center}
                zoom={value ? 15 : 11}
                scrollWheelZoom
                style={{ height, width: "100%" }}
            >
                <TileLayer
                    attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
                    url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                />
                <ClickHandler onChange={onChange} />
                {value && (
                    <Marker
                        position={value}
                        icon={pinIcon}
                        draggable
                        eventHandlers={{
                            dragend: (event) => {
                                const position = event.target.getLatLng();
                                onChange({ lat: position.lat, lng: position.lng });
                            },
                        }}
                    />
                )}
            </MapContainer>
        </Paper>
    );
}
