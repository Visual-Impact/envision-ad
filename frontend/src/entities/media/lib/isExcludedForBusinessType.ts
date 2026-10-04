/**
 * P4 competitive exclusion, the same rule as the backend's
 * {@code BusinessTypeExclusionFilter.sharesBusinessType}: a screen is excluded for an advertiser
 * when its venue is their business type. A missing value on either side never matches, so screens
 * with no venue and advertisers with no type exclude nothing. Keep the two in step.
 */
export function isExcludedForBusinessType(
    screenVenueId: string | null | undefined,
    businessTypeVenueId: string | null | undefined,
): boolean {
    return !!screenVenueId && !!businessTypeVenueId && screenVenueId === businessTypeVenueId;
}
