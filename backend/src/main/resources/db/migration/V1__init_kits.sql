-- Media kit table (moved under Flyway management; matches the Step 0 schema).
CREATE TABLE kits (
    slug       VARCHAR(255) PRIMARY KEY,
    content    TEXT NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

-- Demo kit so the public page has content on a fresh database.
INSERT INTO kits (slug, content, updated_at)
VALUES ('demo', 'Merhaba - bu ilk yayinlanan icerik (seed).', CURRENT_TIMESTAMP);
