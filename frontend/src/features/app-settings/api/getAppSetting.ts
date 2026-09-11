import { axiosInstance } from "@/shared/api";

/**
 * Reads a single app setting value. Returns null when the key has never been set
 * (backend responds 404), so callers can fall back to a default.
 */
export const getAppSetting = async (key: string): Promise<string | null> => {
    try {
        const response = await axiosInstance.get(`/settings/${key}`);
        return response.data?.value ?? null;
    } catch {
        return null;
    }
};
