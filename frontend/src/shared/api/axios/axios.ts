import axios, { InternalAxiosRequestConfig } from 'axios';
import { jwtDecode } from 'jwt-decode';

declare module 'axios' {
    export interface AxiosRequestConfig {
        handleLocally?: boolean;
    }
}

const API_URL = process.env.NEXT_PUBLIC_API_URL;
if (!API_URL) {
    throw new Error('NEXT_PUBLIC_API_URL is not defined in environment variables');
}

const axiosInstance = axios.create({
    baseURL: API_URL,
    headers: {
        'Content-Type': 'application/json',
    },
});

let cachedToken: string | null = null;
let cachedTokenExp = 0;
let noSessionUntil = 0;
let tokenFetchPromise: Promise<string | null> | null = null;

// Buffer in seconds — refetch before the token actually expires
const EXP_BUFFER = 60;

// How long to skip re-fetching after a confirmed "no session" response, so an anonymous
// visitor doesn't trigger a fresh /api/auth0/token 401 on every single API call. Safe to
// cache: login goes through a full-page Auth0 redirect, which gives this module a clean
// reload rather than needing to notice an in-place session change.
const NO_SESSION_TTL = 30;

export function resetTokenCache(): void {
    cachedToken = null;
    cachedTokenExp = 0;
}

async function getToken(): Promise<string | null> {
    const now = Math.floor(Date.now() / 1000);

    if (cachedToken && cachedTokenExp - EXP_BUFFER > now) {
        return cachedToken;
    }

    if (now < noSessionUntil) return null;

    // If a fetch is already in flight, reuse it to avoid duplicate requests
    if (tokenFetchPromise) return tokenFetchPromise;

    tokenFetchPromise = (async () => {
        try {
            const response = await fetch('/api/auth0/token');
            if (response.ok) {
                const { accessToken } = await response.json();
                if (accessToken) {
                    const { exp } = jwtDecode<{ exp: number }>(accessToken);
                    cachedToken = accessToken;
                    cachedTokenExp = exp;
                    return accessToken;
                }
            }
            cachedToken = null;
            noSessionUntil = now + NO_SESSION_TTL;
            return null;
        } catch (error) {
            console.error("Axios: Failed to inject auth token", error);
            cachedToken = null;
            return null;
        } finally {
            tokenFetchPromise = null;
        }
    })();

    return tokenFetchPromise;
}

axiosInstance.interceptors.request.use(
    async (config: InternalAxiosRequestConfig) => {
        if (typeof window !== 'undefined') {
            const token = await getToken();
            if (token) {
                config.headers.Authorization = `Bearer ${token}`;
            }
        }
        return config;
    },
    (error) => Promise.reject(error)
);

axiosInstance.interceptors.response.use(
    (response) => response,
    (error) => {
        if (error.config?.handleLocally) return Promise.reject(error);

        if (error.response?.status === 401) {
            // Use window.location for client-side redirect
            // if (typeof window !== 'undefined') window.location.href = '/auth/login';
        }
        return Promise.reject(error);
    }
);

export default axiosInstance;