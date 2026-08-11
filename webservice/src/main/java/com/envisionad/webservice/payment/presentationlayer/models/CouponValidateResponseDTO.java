package com.envisionad.webservice.payment.presentationlayer.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** {@code error} is one of "invalid" / "expired" / "exhausted" (brief §4.6.4), null when valid. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CouponValidateResponseDTO {
    private boolean valid;
    private Long discountAmountCents;
    private Long previewTotalCents;
    private String error;

    public static CouponValidateResponseDTO invalid(String error) {
        return new CouponValidateResponseDTO(false, null, null, error);
    }

    public static CouponValidateResponseDTO valid(long discountAmountCents, long previewTotalCents) {
        return new CouponValidateResponseDTO(true, discountAmountCents, previewTotalCents, null);
    }
}
