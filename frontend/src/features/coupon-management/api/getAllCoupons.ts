import { Coupon } from "@/entities/coupon";
import axiosInstance from "@/shared/api/axios/axios";

export const getAllCoupons = async (includeArchived: boolean = false): Promise<Coupon[]> => {
    const response = await axiosInstance.get("/coupons", { params: { includeArchived } });
    return response.data;
};
