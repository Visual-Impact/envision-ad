-- P5 FR 3.2: optional invitee name, captured at invite-creation time. Only used if the
-- invitation still has no matching Auth0 account when accepted — BusinessServiceImpl's
-- addBusinessEmployee then provisions a new Auth0 user with this as the display name,
-- falling back to the invitation's email if it was left blank.
ALTER TABLE invitation
    ADD COLUMN name VARCHAR(255);

COMMENT ON COLUMN invitation.name IS 'Optional invitee display name, used only to provision a new Auth0 user at accept time when no existing account matches the invitation email (P5 FR 3.2).';
