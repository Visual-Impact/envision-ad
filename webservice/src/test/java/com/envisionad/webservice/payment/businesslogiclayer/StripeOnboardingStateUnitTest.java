package com.envisionad.webservice.payment.businesslogiclayer;

import com.envisionad.webservice.payment.dataaccesslayer.StripeAccount;
import com.stripe.model.Account;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StripeOnboardingStateUnitTest {

    // ========== from(Account): live state from Stripe ==========

    @Test
    void fromAccount_isComplete_whenSubmittedAndChargesAndPayoutsEnabled() {
        assertEquals(StripeOnboardingState.COMPLETE,
                StripeOnboardingState.from(remote(true, true, true, null)));
    }

    @Test
    void fromAccount_requiresAction_whenDetailsNotSubmitted() {
        // The owner left Stripe's form before finishing; return_url still says "success"
        assertEquals(StripeOnboardingState.ACTION_REQUIRED,
                StripeOnboardingState.from(remote(false, false, false, null)));
    }

    @Test
    void fromAccount_requiresAction_whenRequirementsCurrentlyDue() {
        Account.Requirements requirements = requirements(List.of("external_account"), List.of(), null);

        assertEquals(StripeOnboardingState.ACTION_REQUIRED,
                StripeOnboardingState.from(remote(true, false, false, requirements)));
    }

    @Test
    void fromAccount_requiresAction_whenRequirementsPastDue() {
        Account.Requirements requirements = requirements(List.of(), List.of("individual.id_number"), null);

        assertEquals(StripeOnboardingState.ACTION_REQUIRED,
                StripeOnboardingState.from(remote(true, true, false, requirements)));
    }

    @Test
    void fromAccount_requiresAction_whenRejected() {
        Account.Requirements requirements = requirements(List.of(), List.of(), "rejected.fraud");

        assertEquals(StripeOnboardingState.ACTION_REQUIRED,
                StripeOnboardingState.from(remote(true, false, false, requirements)));
    }

    @Test
    void fromAccount_isPendingVerification_whenSubmittedAndNothingDue() {
        Account.Requirements requirements = requirements(List.of(), List.of(), "requirements.pending_verification");

        assertEquals(StripeOnboardingState.PENDING_VERIFICATION,
                StripeOnboardingState.from(remote(true, false, false, requirements)));
    }

    @Test
    void fromAccount_isPendingVerification_whenSubmittedAndRequirementsMissing() {
        assertEquals(StripeOnboardingState.PENDING_VERIFICATION,
                StripeOnboardingState.from(remote(true, true, false, null)));
    }

    @Test
    void fromAccount_treatsNullFlagsAsFalse() {
        Account remote = new Account();

        assertEquals(StripeOnboardingState.ACTION_REQUIRED, StripeOnboardingState.from(remote));
    }

    // ========== from(StripeAccount): fallback from stored flags ==========

    @Test
    void fromStored_isComplete_whenAllFlagsSet() {
        assertEquals(StripeOnboardingState.COMPLETE, StripeOnboardingState.from(stored(true, true, true)));
    }

    @Test
    void fromStored_isPendingVerification_whenSubmittedButNotEnabled() {
        assertEquals(StripeOnboardingState.PENDING_VERIFICATION,
                StripeOnboardingState.from(stored(true, true, false)));
    }

    @Test
    void fromStored_requiresAction_whenNotSubmitted() {
        assertEquals(StripeOnboardingState.ACTION_REQUIRED,
                StripeOnboardingState.from(stored(false, false, false)));
    }

    // ========== StripeAccountSync.apply ==========

    @Test
    void apply_copiesFlagsAndReportsChange() {
        StripeAccount local = stored(false, false, false);

        boolean changed = StripeAccountSync.apply(remote(true, true, false, null), local);

        assertTrue(changed);
        assertTrue(local.isOnboardingComplete());
        assertTrue(local.isChargesEnabled());
        assertFalse(local.isPayoutsEnabled());
    }

    @Test
    void apply_reportsNoChange_whenFlagsAlreadyMatch() {
        StripeAccount local = stored(true, false, true);

        assertFalse(StripeAccountSync.apply(remote(true, false, true, null), local));
    }

    private static Account remote(boolean detailsSubmitted, boolean chargesEnabled, boolean payoutsEnabled,
                                  Account.Requirements requirements) {
        Account account = new Account();
        account.setDetailsSubmitted(detailsSubmitted);
        account.setChargesEnabled(chargesEnabled);
        account.setPayoutsEnabled(payoutsEnabled);
        account.setRequirements(requirements);
        return account;
    }

    private static Account.Requirements requirements(List<String> currentlyDue, List<String> pastDue,
                                                     String disabledReason) {
        Account.Requirements requirements = new Account.Requirements();
        requirements.setCurrentlyDue(currentlyDue);
        requirements.setPastDue(pastDue);
        requirements.setDisabledReason(disabledReason);
        return requirements;
    }

    private static StripeAccount stored(boolean onboardingComplete, boolean chargesEnabled, boolean payoutsEnabled) {
        StripeAccount account = new StripeAccount();
        account.setOnboardingComplete(onboardingComplete);
        account.setChargesEnabled(chargesEnabled);
        account.setPayoutsEnabled(payoutsEnabled);
        return account;
    }
}
