import type { Ad } from '@/entities/ad/@x/ad-campaign';



export interface AdCampaign {
    campaignId: string;
    name: string;
    startDate: string; // or Date
    endDate: string;   // or Date
    ads: Ad[];
}

export interface AdCampaignRequestDTO {
    name: string;
}