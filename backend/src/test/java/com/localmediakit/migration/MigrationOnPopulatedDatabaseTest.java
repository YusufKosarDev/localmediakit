package com.localmediakit.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Applies the migrations to a database that already has rows in it.
 *
 * <p>Everything else in this suite starts from an empty schema, and V17 exists
 * because that is not the same test. V16 added users.theme with a lowercase
 * default and backfilled every existing row with it; the column is mapped to an
 * enum whose constants are LIGHT/DARK, so every account that predated the
 * migration stopped loading and answered 500. Rows created through JPA
 * afterwards were fine, which is exactly why a suite starting from nothing saw
 * none of it -- the comment at the top of V17 says so itself.
 *
 * <p>So this test migrates part of the way, writes the kind of rows a live
 * database would already hold, and then finishes. It is deliberately not a
 * Spring test: no context, no application beans, just the migrations and a
 * database, which is what production applies them with.
 */
class MigrationOnPopulatedDatabaseTest {

    /** The last version before the ones that transform existing rows. */
    private static final String BEFORE_THE_DATA_MIGRATIONS = "14";

    /** Each run gets its own database so nothing here can see another test's rows. */
    private final String url = "jdbc:h2:mem:migration-" + UUID.randomUUID()
            + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1";

    @Test
    void rowsThatPredateTheMigrationsSurviveThemIntact() throws Exception {
        Flyway partial = flyway().target(MigrationVersion.fromVersion(BEFORE_THE_DATA_MIGRATIONS)).load();
        partial.migrate();

        seedRowsAsAnOlderVersionWouldHaveLeftThem();

        // The step this test exists for: finishing the migration on top of data.
        flyway().load().migrate();

        assertThat(column("select plan from users where email = 'veteran@example.com'"))
                .as("V15 pulls existing FREE accounts up to PRO")
                .isEqualTo("PRO");

        assertThat(column("select theme from users where email = 'veteran@example.com'"))
                .as("the incident V17 was written for: a backfilled 'light' is not an enum constant")
                .isEqualTo("LIGHT");

        assertThat(column("select locale from users where email = 'veteran@example.com'"))
                .as("V21 has to give pre-existing rows a locale, not leave them null")
                .isNotNull();

        assertThat(column("select title from media_kits where slug = 'eski-kit'"))
                .as("content written before the migrations must still be there afterwards")
                .isEqualTo("Eski Kit");

        assertThat(column("select count(*) from page_views")).isEqualTo("1");
    }

    @Test
    void theSchemaEndsUpWhereTheApplicationExpectsIt() throws Exception {
        flyway().load().migrate();

        // Spot-checks on the tables the newest migrations add, so a migration
        // that fails to apply cannot pass as "no assertions about it".
        assertThat(tableExists("PAGE_VIEW_DAILY")).isTrue();
        assertThat(tableExists("PAGE_VIEWS")).isTrue();
        assertThat(columnExists("USERS", "LOCALE")).isTrue();
        assertThat(columnExists("MEDIA_KITS", "ACCENT")).isTrue();
    }

    @Test
    void migratingTwiceChangesNothing() {
        // Read through Flyway's own API rather than querying its history table:
        // the table is created with a quoted lowercase name, so selecting from
        // it unquoted is a dialect argument this test has no reason to have.
        flyway().load().migrate();
        int afterFirstRun = flyway().load().info().applied().length;

        flyway().load().migrate();

        assertThat(flyway().load().info().applied().length)
                .as("a redeploy re-runs migrate(); applying anything twice would be a bug")
                .isEqualTo(afterFirstRun);
    }

    /**
     * Rows shaped the way the schema at V14 would have stored them: a FREE plan,
     * no theme column yet, no locale. That is the state the later migrations
     * have to cope with, and the state no other test produces.
     */
    private void seedRowsAsAnOlderVersionWouldHaveLeftThem() throws Exception {
        execute("""
                insert into users (email, password_hash, display_name, plan, created_at)
                values ('veteran@example.com', '$2a$10$notarealhash', 'Eski Kullanici', 'FREE', CURRENT_TIMESTAMP)""");
        execute("""
                insert into media_kits (user_id, slug, title, theme, status, created_at, updated_at)
                select id, 'eski-kit', 'Eski Kit', 'light', 'DRAFT', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                from users where email = 'veteran@example.com'""");
        execute("""
                insert into media_kit_versions (media_kit_id, version_number, slug, content_json, published_at)
                select id, 1, 'eski-kit', '{"slug":"eski-kit","title":"Eski Kit"}', CURRENT_TIMESTAMP
                from media_kits where slug = 'eski-kit'""");
        execute("""
                insert into page_views (media_kit_id, slug, visitor_hash, device, viewed_at)
                select id, 'eski-kit', 'eski-ziyaretci-hash', 'DESKTOP', CURRENT_TIMESTAMP
                from media_kits where slug = 'eski-kit'""");
    }

    /** Same settings the application uses, pointed at this test's own database. */
    private FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .baselineOnMigrate(true);
    }

    private void execute(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String column(String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            return rows.next() ? rows.getString(1) : null;
        }
    }

    private boolean tableExists(String table) throws Exception {
        return !metadata("select table_name from information_schema.tables where table_name = '"
                + table + "'").isEmpty();
    }

    private boolean columnExists(String table, String column) throws Exception {
        return !metadata("select column_name from information_schema.columns where table_name = '"
                + table + "' and column_name = '" + column + "'").isEmpty();
    }

    private List<String> metadata(String sql) throws Exception {
        List<String> values = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                values.add(rows.getString(1));
            }
        }
        return values;
    }
}
