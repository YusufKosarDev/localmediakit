-- Account-level profile so a user can manage their own identity and settings.
--
-- avatar_url is account identity only (dashboard header / settings). It is
-- deliberately NOT wired to media_kits.avatar_url: a kit's avatar is content
-- that belongs to the published snapshot, so the two never overwrite each
-- other and the publish/snapshot path is untouched.
--
-- HTTPS-URL only, same rule the kit avatar already uses — no upload endpoint,
-- so there is no object store, no third-party key, and nothing to lose when
-- the host's ephemeral disk is recycled on redeploy.
ALTER TABLE users ADD COLUMN avatar_url VARCHAR(1000);

-- Dashboard appearance preference. The public media-kit page keeps its own
-- per-kit theme — this never applies there.
ALTER TABLE users ADD COLUMN theme VARCHAR(20) NOT NULL DEFAULT 'light';

-- Last profile/credential change. Backfilled from created_at for existing rows.
ALTER TABLE users ADD COLUMN updated_at TIMESTAMP(6);
UPDATE users SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE users ALTER COLUMN updated_at SET NOT NULL;
