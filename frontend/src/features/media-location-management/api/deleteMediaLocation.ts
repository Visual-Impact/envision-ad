import { axiosInstance } from "@/shared/api";

const BASE_URL = "/media-locations";

export const deleteMediaLocation = async (id: string): Promise<void> => {
    await axiosInstance.delete(`${BASE_URL}/${id}`);
};
