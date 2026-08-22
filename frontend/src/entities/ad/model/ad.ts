export interface Ad {
    adId: string;
    campaignId: string;
    name: string;
    adUrl: string;
    adType: "IMAGE" | "VIDEO";
    /** Venue types this creative targets. Empty means universal — suitable for every
     *  venue type. Always present: the API sends [] rather than omitting it. */
    venueIds: string[];
}

export interface AdRequestDTO {
    name: string;
    adUrl: string;
    adType: "IMAGE" | "VIDEO";
    venueIds: string[];
}
