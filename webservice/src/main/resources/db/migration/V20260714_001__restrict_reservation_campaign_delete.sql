-- DB-level backstop mirroring AdCampaignServiceImpl#campaignIsTiedToReservation:
-- block deleting a campaign that still has a CONFIRMED/APPROVED/PENDING
-- reservation ending in the future. Campaigns with only past reservations
-- remain deletable, same as today, and reservations.campaign_id keeps its
-- existing ON DELETE SET NULL behavior for that allowed case.
--
-- This exists to catch direct/manual database changes that bypass the
-- application layer (the app-level check already prevents this in normal use).
CREATE OR REPLACE FUNCTION prevent_active_campaign_delete() RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM reservations
        WHERE campaign_id = OLD.campaign_id
          AND status IN ('CONFIRMED', 'APPROVED', 'PENDING')
          AND end_date >= NOW()
    ) THEN
        RAISE EXCEPTION 'Cannot delete campaign %: it is tied to an active reservation', OLD.campaign_id
            USING ERRCODE = '23514';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_prevent_active_campaign_delete ON ad_campaigns;

CREATE TRIGGER trg_prevent_active_campaign_delete
    BEFORE DELETE ON ad_campaigns
    FOR EACH ROW
    EXECUTE FUNCTION prevent_active_campaign_delete();
