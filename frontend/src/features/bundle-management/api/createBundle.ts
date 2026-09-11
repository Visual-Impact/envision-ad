import { Bundle, BundleRequestDTO } from "@/entities/bundle";
import { axiosInstance } from "@/shared/api";

export const createBundle = async (data: BundleRequestDTO): Promise<Bundle> => {
    const response = await axiosInstance.post("/bundles", data);
    return response.data;
};
