import { CreateAccountRequestDTO, CreateAccountResponseDTO } from "@/entities/account";
import axiosInstance from "@/shared/api/axios/axios";

export const createAccount = async (data: CreateAccountRequestDTO): Promise<CreateAccountResponseDTO> => {
    const response = await axiosInstance.post("/admin/accounts", data);
    return response.data;
};
