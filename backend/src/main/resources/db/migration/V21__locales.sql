-- Interface language, in two independent places on purpose.
--
-- users.locale is the language the OWNER administers in: the dashboard, the
-- settings page, and the lead-notification emails they receive.
--
-- media_kits.language is the language a kit is PRESENTED in on its public
-- page. These are not the same question. A creator pitching Turkish brands
-- with one kit and international brands with another needs both at once, and
-- the dashboard language says nothing about which audience a given kit is
-- aimed at. Frozen into the snapshot at publish, like theme/accent/layout, so
-- one public URL always renders in one language and stays a single edge-cache
-- entry.
--
-- Lowercase plain strings, matching theme/accent/layout rather than an enum
-- (V16/V17: an @Enumerated column and a SQL DEFAULT drifted over letter case,
-- and case folding is locale-sensitive).
ALTER TABLE users ADD COLUMN locale VARCHAR(10) NOT NULL DEFAULT 'tr';
ALTER TABLE media_kits ADD COLUMN language VARCHAR(10) NOT NULL DEFAULT 'tr';

-- The notification snapshots the recipient's locale for the same reason it
-- snapshots their address: the message should read in the language they had
-- when the lead arrived, even if they switch before the queue drains.
ALTER TABLE lead_notifications ADD COLUMN locale VARCHAR(10) NOT NULL DEFAULT 'tr';
