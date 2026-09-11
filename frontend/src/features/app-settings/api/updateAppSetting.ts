import { axiosInstance } from "@/shared/api";

export const updateAppSetting = async (key: string, value: string): Promise<{ key: string; value: string }> => {
    const response = await axiosInstance.put(`/settings/${key}`, { value });
    return response.data;
};
