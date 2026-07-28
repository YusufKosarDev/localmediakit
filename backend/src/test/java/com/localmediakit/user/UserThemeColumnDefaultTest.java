package com.localmediakit.user;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The theme column is mapped as an {@code @Enumerated(EnumType.STRING)}, so
 * whatever the schema writes into it has to be a literal enum constant.
 *
 * <p>This exists because the two drifted apart once: the column was introduced
 * with {@code DEFAULT 'light'} while the constants are {@code LIGHT}/{@code
 * DARK}. Every account that predated the migration got the lowercase value and
 * stopped loading — its own {@code /api/me} answered 500. The rest of the
 * suite missed it because a fresh schema has no pre-existing rows: every user
 * is created through JPA, which writes the correct casing.
 *
 * <p>So this test deliberately does <em>not</em> go through JPA. It inserts a
 * row that relies on the column default, exactly like the backfill did, and
 * then reads it back through the entity.
 */
@SpringBootTest
class UserThemeColumnDefaultTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserRepository userRepository;

    @Test
    @Transactional
    void aRowRelyingOnTheSchemaDefaultIsStillReadableThroughTheEntity() {
        jdbcTemplate.update("""
                INSERT INTO users (email, password_hash, display_name, plan, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                "schema-default@example.com", "irrelevant-hash", "Schema Default",
                "PRO", Timestamp.from(Instant.now()), Timestamp.from(Instant.now()));

        // Would throw on the enum conversion if the default were not a constant.
        User loaded = userRepository.findByEmail("schema-default@example.com").orElseThrow();

        assertThat(loaded.getTheme()).isEqualTo(Theme.LIGHT);
    }

    /**
     * Case folding must not depend on where the server runs. On a Turkish JVM
     * the default-locale overloads map "I" to a dotless "ı" and "i" to a
     * dotted "İ" — the same trap that produced 'LİGHT' in the first attempt at
     * the migration above.
     */
    @Test
    void emailNormalizationIsLocaleIndependent() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertThat(AccountService.normalizeEmail("  IREM@Ornek.COM ")).isEqualTo("irem@ornek.com");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    @Transactional
    void everyStoredThemeValueMatchesAnEnumConstant() {
        // Guards the migrations as a set: any future one that writes a theme
        // in the wrong casing fails here rather than in production.
        jdbcTemplate.queryForList("SELECT DISTINCT theme FROM users", String.class)
                .forEach(stored -> assertThat(Theme.valueOf(stored)).isNotNull());
    }
}
