-- Onboarding dismissal. Nullable on purpose: NULL means "not dismissed yet",
-- so existing accounts are simply treated as not having seen it.
--
-- Only the dismissal is stored. The individual steps (has a kit / has stats /
-- has published) are derived from the user's real data at read time, so there
-- is no progress bookkeeping that could drift out of sync with what the
-- account actually contains.
ALTER TABLE users ADD COLUMN onboarding_completed_at TIMESTAMP(6);
