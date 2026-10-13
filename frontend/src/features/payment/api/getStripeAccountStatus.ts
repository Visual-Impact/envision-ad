import { axiosInstance } from "@/shared/api";

/** Mirrors the backend's StripeOnboardingState. */
export type StripeOnboardingState = 'NOT_CONNECTED' | 'ACTION_REQUIRED' | 'PENDING_VERIFICATION' | 'COMPLETE';

export interface StripeAccountStatus {
    connected: boolean;
    onboardingComplete: boolean;
    chargesEnabled: boolean;
    payoutsEnabled: boolean;
    stripeAccountId?: string;
    state: StripeOnboardingState;
}

export const getStripeAccountStatus = async (businessId: string): Promise<StripeAccountStatus> => {
    const response = await axiosInstance.get('/payments/account-status', {
        params: { businessId }
    });
    return response.data;
};
