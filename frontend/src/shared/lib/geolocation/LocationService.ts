
import type { LatLngLiteral } from 'leaflet';

interface LatLngSession {
  lat: number;
  lng: number;
  expiryTime: number;
}

export type LocationStatus = 'idle' | 'loading' | 'success' | 'denied' | 'error';

// Stores the user location in the session storage for a set amount of time. 
// After that time is up, it will check the user's location again.
// That way you don't need to check the user's exact position every time you open the website.
const userLocationKey = "userLocation";

// TODO: Maybe check if the user is using a mobile device to set the expiry time to a shorter amount, since a mobile user might be moving more.
const expiryMinutes = 60; // 24 hours

function storeLatLngSession(latlng: LatLngLiteral) {
    const now = new Date();
    const expiryTime: number = now.getTime() + expiryMinutes * 60 * 1000; 
    
    const item: LatLngSession = {
        lat: latlng.lat,
        lng: latlng.lng,
        expiryTime: expiryTime,
    };
    sessionStorage.setItem(userLocationKey, JSON.stringify(item));
}

export interface AddressComponents {
  road?: string,
  house_number?: string,
  city?: string,
  town?: string,
  village?: string,
  suburb?: string,
  county?: string,
  state?: string,
  province?: string,
  country?: string,
  country_code?: string,
  postcode?: string,
}

export interface AddressDetails {
  display_name: string,
  lat: number,
  lng: number,
  address?: AddressComponents,
}

// Raw shape returned by Nominatim's /search endpoint: lat/lon come back as strings
// under the key `lon`, not `lng`.
interface NominatimResult {
  display_name: string,
  lat: string,
  lon: string,
  address?: AddressComponents,
}

function toAddressDetails(item: NominatimResult): AddressDetails {
  return {
    display_name: item.display_name,
    address: item.address,
    lat: parseFloat(item.lat),
    lng: parseFloat(item.lon),
  };
}

/**
 * Searches for locations using the OpenStreetMap Nominatim API and returns a list
 * of matching entries, each with its display name, structured address fields, and
 * coordinates — enough to populate an address form directly from a chosen result.
 *
 * If the search query is empty or shorter than three characters, no request is
 * sent to the API and the function immediately resolves to an empty array.
 *
 * @param {string} query - The text to search for (e.g. address or place name).
 * @param {string} language - The preferred language code for localized results.
 * @returns {Promise<AddressDetails[]>} A promise that resolves to an array of
 * matching address details.
 */
export async function SearchLocations(query: string, language: string): Promise<AddressDetails[]> {
  if (!query || query.length < 3) return [];

  try {
    const res = await fetch(
      `https://nominatim.openstreetmap.org/search?` +
        `format=json&addressdetails=1&limit=5` +
        `&q=${encodeURIComponent(query)}` +
        `&accept-language=${encodeURIComponent(language)}`,
      {
        headers: {
          "User-Agent": "Visual-Impact"
        }
      }
    );

    if (!res.ok) {
      console.error(
        `SearchLocations request failed with status ${res.status} ${res.statusText}`
      );
      return [];
    }

    const data: NominatimResult[] = await res.json();

    return data.map(toAddressDetails);
  } catch (error) {
    console.error("SearchLocations request error:", error);
    return [];
  }
}

/**
 * Fetches detailed address information for a given search query from the Nominatim API.
 *
 * @param query - The free-text search query used to look up an address.
 * @param language - The preferred language code (e.g. "en", "de") for the result.
 * @returns A promise that resolves to an {@link AddressDetails} object including display name,
 *          structured address fields, and latitude/longitude coordinates.
 */
export async function GetAddressDetails(query: string, language: string): Promise<AddressDetails | null> {
  const res = await fetch(
    `https://nominatim.openstreetmap.org/search?` +
      `format=json&addressdetails=1&limit=1` +
      `&q=${encodeURIComponent(query)}` +
      `&accept-language=${encodeURIComponent(language)}`,
    {
      headers: {
        "User-Agent": "Visual-Impact"
      }
    }
  );

  const data: NominatimResult[] = await res.json();

  if (data.length === 0) {
    return null;
  }

  return toAddressDetails(data[0]);
}

/**
 * Resolves the address at a given coordinate using Nominatim's reverse-geocoding
 * endpoint. Used to auto-fill the address fields after the user drops a pin on a
 * map, so they don't have to type the street/city/etc. by hand.
 *
 * @param lat - Latitude to resolve.
 * @param lng - Longitude to resolve.
 * @param language - The preferred language code (e.g. "en", "de") for the result.
 * @returns A promise that resolves to an {@link AddressDetails} object, or `null`
 *          if Nominatim has no address data for that coordinate (e.g. open water,
 *          unmapped area).
 */
export async function ReverseGeocode(lat: number, lng: number, language: string): Promise<AddressDetails | null> {
  try {
    const res = await fetch(
      `https://nominatim.openstreetmap.org/reverse?` +
        `format=json&addressdetails=1` +
        `&lat=${encodeURIComponent(lat)}` +
        `&lon=${encodeURIComponent(lng)}` +
        `&accept-language=${encodeURIComponent(language)}`,
      {
        headers: {
          "User-Agent": "Visual-Impact"
        }
      }
    );

    if (!res.ok) {
      console.error(
        `ReverseGeocode request failed with status ${res.status} ${res.statusText}`
      );
      return null;
    }

    const data = await res.json();

    if (!data || data.error || !data.address) {
      return null;
    }

    return toAddressDetails(data as NominatimResult);
  } catch (error) {
    console.error("ReverseGeocode request error:", error);
    return null;
  }
}

// Get the user's latitude and longitude
/**
 * Retrieves the user's current geographic location, using session storage to cache
 * a previously obtained position for a limited time to avoid repeated lookups.
 *
 * The promise resolves with a {@link LatLngLiteral} containing the current
 * latitude and longitude.
 *
 * The promise rejects in the following cases:
 * - When the Geolocation API is not supported by the browser, it rejects with
 *   an object of the form: `{ code: 'UNSUPPORTED' }`.
 * - When the Geolocation API is supported but `getCurrentPosition` fails
 *   (for example, if the user denies permission or another geolocation error
 *   occurs), it rejects with the error object provided by `getCurrentPosition`.
 */
export function GetUserGeoLocation(): Promise<LatLngLiteral> {
  return new Promise((resolve, reject) => {
    const storedLocation = sessionStorage.getItem("userLocation");

    if (storedLocation) {
      const location: LatLngSession = JSON.parse(storedLocation);

      if (new Date().getTime() <= location.expiryTime) {
        resolve({ lat: location.lat, lng: location.lng });
        return;
      }

      sessionStorage.removeItem(userLocationKey);
    }

    if (!("geolocation" in navigator)) {
      reject({ code: 'UNSUPPORTED' });
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (position) => {
        const coords: LatLngLiteral = {
          lat: position.coords.latitude,
          lng: position.coords.longitude,
        };

        storeLatLngSession(coords);
        resolve(coords);
      },
      (error) => {
        reject(error);
      }
    );
  });
}


