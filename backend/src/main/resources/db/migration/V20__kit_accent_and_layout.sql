-- Public page appearance beyond light/dark.
--
-- Stored as plain lowercase strings, matching the existing `theme` column
-- rather than introducing an enum: an @Enumerated(EnumType.STRING) column and
-- a SQL DEFAULT already drifted apart once (V16/V17), and case folding is
-- locale-sensitive. Keeping the same convention as `theme` avoids repeating
-- that.
--
-- Defaults reproduce exactly what every kit looks like today, so this
-- migration changes no published page's appearance.
ALTER TABLE media_kits ADD COLUMN accent VARCHAR(20) NOT NULL DEFAULT 'violet';
ALTER TABLE media_kits ADD COLUMN layout VARCHAR(20) NOT NULL DEFAULT 'classic';
