package com.envisionad.webservice.business.exceptions;

/**
 * Thrown when an admin tries to remove the Media Owner role from a business while a
 * live (ACTIVE/PAST_DUE) bundle subscription includes at least one of that business's
 * screens. Blocks on live commercial commitment, not mere existence — a media owner
 * with only never-sold screens can still drop the role freely.
 * <p>
 * This is the highest-blast-radius case in the role-change feature: the subscription
 * blocking this removal may belong to a *different* business entirely (the advertiser
 * paying to run on this owner's screen), so pulling the role mid-flight would orphan
 * someone else's paid campaign, not just this business's own data.
 */
public class MediaOwnerRoleRemovalBlockedException extends RuntimeException {
    public MediaOwnerRoleRemovalBlockedException(String businessId) {
        super("Business " + businessId + " cannot lose the Media Owner role: at least one of its "
                + "screens is part of a live bundle subscription. Removing the role now would orphan "
                + "that subscription's placement — cancel or wait for the subscription to end first.");
    }
}
