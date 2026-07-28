-- V16 added users.theme with DEFAULT 'light', but the column is mapped as an
-- @Enumerated(EnumType.STRING) whose constants are LIGHT/DARK. Every row that
-- existed before V16 was backfilled with the lowercase default and could no
-- longer be read: loading the user threw on the enum conversion and the
-- account's own /api/me answered 500.
--
-- Rows created through JPA after V16 were already correct, which is why a
-- test suite starting from an empty schema never saw this — only databases
-- with pre-existing users were affected.
-- Written as explicit literals rather than UPPER(theme): UPPER() follows the
-- database's locale, and in a Turkish locale 'light' uppercases to 'LİGHT'
-- (dotted capital I, U+0130) — which is not an enum constant either. Only two
-- values have ever existed, so spelling them out is both shorter and safe
-- everywhere.
UPDATE users SET theme = 'LIGHT' WHERE theme = 'light';
UPDATE users SET theme = 'DARK'  WHERE theme = 'dark';

-- Bring the column default in line with the enum so a future plain-SQL insert
-- cannot reintroduce the mismatch.
ALTER TABLE users ALTER COLUMN theme SET DEFAULT 'LIGHT';
