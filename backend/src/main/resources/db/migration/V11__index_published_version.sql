-- The public read (findActiveBySlug) and the kit-list projection join
-- media_kit_versions on media_kits.published_version_id; index it so that
-- join is not a table scan as the data grows.
CREATE INDEX idx_media_kits_published_version ON media_kits (published_version_id);
