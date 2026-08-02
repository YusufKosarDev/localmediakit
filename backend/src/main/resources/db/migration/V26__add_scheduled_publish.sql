-- Publishing at a chosen moment instead of at the moment you press the button.
--
-- Two columns on the kit rather than a jobs table. The schedule is a property
-- of the kit -- there can only ever be one pending publish for it, and cancelling
-- is clearing a field. A queue would invite two rows for the same kit and then
-- need a rule for which one wins.
--
-- WHAT THIS DELIBERATELY DOES NOT DO: it does not freeze the draft at scheduling
-- time. When the moment arrives the job runs the ordinary publish, which
-- snapshots whatever the draft is THEN. That is the same rule as pressing the
-- button, moved in time, and it is the one people expect: a correction made in
-- the meantime should go out, not be silently discarded by a decision made
-- yesterday.
ALTER TABLE media_kits ADD COLUMN scheduled_publish_at TIMESTAMP(6);

-- Why the last attempt did not go out. A schedule that quietly failed and left
-- no trace is worse than one that never existed: the creator believes the page
-- went live and has no reason to check.
ALTER TABLE media_kits ADD COLUMN schedule_error VARCHAR(500);

-- The job's read: everything due, across all kits.
CREATE INDEX idx_media_kits_scheduled_publish ON media_kits (scheduled_publish_at);
