package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.payment.dataaccesslayer.StripeAccount;
import com.stripe.model.Account;

/**
 * The one mapping from a Stripe {@link Account} onto our stored {@link StripeAccount} flags.
 * Both writers go through it — the {@code account.updated} webhook and the live check in
 * {@link StripeServiceImpl#getAccountStatus} — so they can't drift apart.
 */
final class StripeAccountSync {

    private StripeAccountSync() {
    }

    /**
     * Copies Stripe's onboarding flags onto {@code local}.
     *
     * @return whether any flag changed
     */
    static boolean apply(Account remote, StripeAccount local) {
        boolean onboardingComplete = Boolean.TRUE.equals(remote.getDetailsSubmitted());
        boolean chargesEnabled = Boolean.TRUE.equals(remote.getChargesEnabled());
        boolean payoutsEnabled = Boolean.TRUE.equals(remote.getPayoutsEnabled());

        boolean changed = local.isOnboardingComplete() != onboardingComplete
                || local.isChargesEnabled() != chargesEnabled
                || local.isPayoutsEnabled() != payoutsEnabled;

        local.setOnboardingComplete(onboardingComplete);
        local.setChargesEnabled(chargesEnabled);
        local.setPayoutsEnabled(payoutsEnabled);
        return changed;
    }
}
