-- Password-protected kits (PRO). The hash lives on the draft (media_kits) and
-- is FROZEN into each publish snapshot (media_kit_versions), like the badge:
-- changing the draft password does not affect the live page until republish.
-- Null hash = public kit served statically from the edge (unchanged path).
ALTER TABLE media_kits ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE media_kit_versions ADD COLUMN password_hash VARCHAR(255);
