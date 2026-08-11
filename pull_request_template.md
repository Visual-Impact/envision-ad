## Summary
<!-- 2-4 bullets: what changed and WHY. Skip re-describing what's obvious from the diff. -->
-

## Changes
<!-- Group by area. Bold sub-header per area, bullets underneath. Delete areas that don't apply. -->
**Frontend**
-

**Backend**
-

## Before / After
<!-- Required for any UI-impacting PR: screenshot, GIF, or short clip of before and after. Delete this section entirely if there's no UI change. -->

## Testing
<!-- What you actually ran and verified locally -- specific scenarios, not "it works". -->
-

## Follow-up
<!-- Optional. Known gaps, edge cases, or cleanup this PR intentionally defers. Delete if none. -->

## Related
<!-- Optional. Link a knowledge-base page, brief, Slack thread, or GitHub issue if one exists. -->

---

- [ ] Follows FSD (frontend) / 3-tier layering (backend) conventions -- new modules mirror existing structure
- [ ] Tests added/updated; `./gradlew check` and/or `npm run lint && npx steiger src/` pass locally
- [ ] No new dependencies added without prior sign-off
- [ ] If this touches money: `BigDecimal`/`HALF_UP`, recomputed server-side, no client-supplied amount trusted
