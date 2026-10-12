import { axiosInstance } from "@/shared/api";

export interface SavedProofLink {
    subscriptionId: string;
    url: string;
    updatedAt: string;
}

/** Creates or replaces a subscription's proof-of-display link. */
export const upsertProofLink = async (subscriptionId: string, url: string): Promise<SavedProofLink> => {
    const response = await axiosInstance.put<SavedProofLink>(
        `/proof-of-display/subscriptions/${subscriptionId}/link`,
        { url },
    );
    return response.data;
};
