import { CreateAccountRequestDTO, CreateAccountResponseDTO } from "../model/account";
import { axiosInstance } from "@/shared/api";

export const createAccount = async (data: CreateAccountRequestDTO): Promise<CreateAccountResponseDTO> => {
    const response = await axiosInstance.post("/admin/accounts", data);
    return response.data;
};
