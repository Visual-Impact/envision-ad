package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.payment.dataaccesslayer.StripeAccount;
import com.stripe.model.Account;

import java.util.List;

/**
 * Where a media owner stands in Stripe Connect onboarding, as shown on the Stripe page.
 * <p>
 * "Complete" keeps the definition the media-creation gate already uses (details submitted,
 * charges enabled, payouts enabled), so the page can never say "ready" while media creation
 * still refuses the owner.
 */
public enum StripeOnboardingState {
    /** No Stripe account has been created for the business yet. */
    NOT_CONNECTED,
    /** The owner has to go back to Stripe: the form was never submitted, or Stripe wants more. */
    ACTION_REQUIRED,
    /** Everything is submitted and Stripe is verifying it. Nothing for the owner to do. */
    PENDING_VERIFICATION,
    /** Onboarded. */
    COMPLETE;

    /**
     * The state as Stripe reports it right now. Distinguishes "Stripe needs more from you"
     * from "Stripe is reviewing", which our stored flags alone cannot.
     */
    public static StripeOnboardingState from(Account account) {
        boolean detailsSubmitted = Boolean.TRUE.equals(account.getDetailsSubmitted());
        if (detailsSubmitted
                && Boolean.TRUE.equals(account.getChargesEnabled())
                && Boolean.TRUE.equals(account.getPayoutsEnabled())) {
            return COMPLETE;
        }
        if (!detailsSubmitted) {
            return ACTION_REQUIRED;
        }

        Account.Requirements requirements = account.getRequirements();
        if (requirements != null) {
            if (hasAny(requirements.getCurrentlyDue()) || hasAny(requirements.getPastDue())) {
                return ACTION_REQUIRED;
            }
            // A rejected account has nothing left "due", so it would otherwise read as under
            // review forever. Stripe's hosted onboarding explains the rejection to the owner.
            String disabledReason = requirements.getDisabledReason();
            if (disabledReason != null && disabledReason.startsWith("rejected")) {
                return ACTION_REQUIRED;
            }
        }
        return PENDING_VERIFICATION;
    }

    /** Best guess from our stored flags, for when Stripe can't be reached. */
    public static StripeOnboardingState from(StripeAccount account) {
        if (isComplete(account)) {
            return COMPLETE;
        }
        return account.isOnboardingComplete() ? PENDING_VERIFICATION : ACTION_REQUIRED;
    }

    static boolean isComplete(StripeAccount account) {
        return account.isOnboardingComplete() && account.isChargesEnabled() && account.isPayoutsEnabled();
    }

    private static boolean hasAny(List<String> requirements) {
        return requirements != null && !requirements.isEmpty();
    }
}
