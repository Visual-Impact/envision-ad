package com.envisionad.webservice.activecampaign.presentationlayer.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * How many media owners actually heard about a swap or notify, so the advertiser's success
 * toast can say so rather than implying everyone was reached.
 *
 * <p>{@code failed} counts owners who should have been told and were not, whatever the reason —
 * an unresolvable address counts, not just a bounced send. These are the values written to
 * {@code campaign_swap_events}.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResultModel {
    private int notifiedCount;
    private int failedCount;
}
