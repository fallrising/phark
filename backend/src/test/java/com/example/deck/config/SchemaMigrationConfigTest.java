package com.example.deck.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemaMigrationConfigTest {

    @TempDir
    File tempDir;

    private String url;

    @BeforeEach
    void setUp() {
        url = "jdbc:sqlite:" + new File(tempDir, "test.db").getAbsolutePath();
    }

    private void execute(String... statements) throws Exception {
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                stmt.execute(sql);
            }
        }
    }

    private void runMigration() {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl(url);
            dataSource.setDriverClassName("org.sqlite.JDBC");
            dataSource.setMaximumPoolSize(1);

            Flyway flyway = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineVersion("1")
                    .validateOnMigrate(true)
                    .validateMigrationNaming(true)
                    .cleanDisabled(true)
                    .load();

            new SchemaMigrationConfig().guardedLegacyBaseline().migrate(flyway);
        }
    }

    private List<String[]> queryHistory() throws Exception {
        List<String[]> rows = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT version, type, script, success FROM flyway_schema_history ORDER BY installed_rank")) {
            while (rs.next()) {
                rows.add(new String[] { rs.getString("version"), rs.getString("type"),
                        rs.getString("script"), rs.getString("success") });
            }
        }
        return rows;
    }

    private boolean tableExists(String table) throws Exception {
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + table + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private boolean indexExists(String index) throws Exception {
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='" + index + "'")) {
            return rs.next() && rs.getInt(1) > 0;
        }
    }

    private long queryLong(String sql) throws Exception {
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            assertThat(rs.next()).isTrue();
            return rs.getLong(1);
        }
    }

    @Test
    void emptyDatabaseBootstrapsV1ThroughV3() throws Exception {
        runMigration();

        List<String[]> history = queryHistory();
        assertThat(history).hasSize(3);
        assertThat(history.get(0)).containsExactly("1", "SQL", "V1__create_legacy_posts.sql", "1");
        assertThat(history.get(1)).containsExactly("2", "SQL", "V2__add_cursor_timeline_indexes.sql", "1");
        assertThat(history.get(2)).containsExactly("3", "SQL", "V3__add_post_replies.sql", "1");

        assertThat(tableExists("posts")).isTrue();
        assertThat(tableExists("replies")).isTrue();
        assertThat(tableExists("flyway_schema_history")).isTrue();

        assertThat(indexExists("idx_posts_timeline")).isTrue();
        assertThat(indexExists("idx_posts_channel_timeline")).isTrue();
        assertThat(indexExists("idx_posts_channel")).isFalse();
        assertThat(indexExists("idx_posts_created_at")).isFalse();
        assertThat(indexExists("idx_replies_post_timeline")).isTrue();

        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM posts")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isZero();
        }
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM replies")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isZero();
        }
    }

    @Test
    void legacyPostsSchemaBaselinesAndPreservesData() throws Exception {
        execute(
                "CREATE TABLE posts (id INTEGER PRIMARY KEY AUTOINCREMENT, author TEXT NOT NULL, content TEXT NOT NULL, channel TEXT NOT NULL CHECK (channel IN ('home', 'tech', 'ops')), created_at TEXT NOT NULL DEFAULT (datetime('now')))",
                "CREATE INDEX idx_posts_channel ON posts(channel)",
                "CREATE INDEX idx_posts_created_at ON posts(created_at DESC)",
                "INSERT INTO posts (id, author, content, channel) VALUES (41, 'alice', 'legacy post', 'home')");

        runMigration();

        List<String[]> history = queryHistory();
        assertThat(history).hasSize(3);
        assertThat(history.get(0)).containsExactly("1", "BASELINE", "<< Flyway Baseline >>", "1");
        assertThat(history.get(1)).containsExactly("2", "SQL", "V2__add_cursor_timeline_indexes.sql", "1");
        assertThat(history.get(2)).containsExactly("3", "SQL", "V3__add_post_replies.sql", "1");

        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, author, content, channel FROM posts")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong("id")).isEqualTo(41);
            assertThat(rs.getString("author")).isEqualTo("alice");
            assertThat(rs.getString("content")).isEqualTo("legacy post");
            assertThat(rs.getString("channel")).isEqualTo("home");
            assertThat(rs.next()).isFalse();
        }

        assertThat(indexExists("idx_posts_timeline")).isTrue();
        assertThat(indexExists("idx_posts_channel_timeline")).isTrue();
        assertThat(indexExists("idx_posts_channel")).isFalse();
        assertThat(indexExists("idx_posts_created_at")).isFalse();
        assertThat(indexExists("idx_replies_post_timeline")).isTrue();
        assertThat(tableExists("replies")).isTrue();

        execute("INSERT INTO posts (author, content, channel) VALUES ('next', 'after migration', 'home')");
        assertThat(queryLong("SELECT MAX(id) FROM posts")).isEqualTo(42);
    }

    @Test
    void currentPreFlywaySchemaPreservesBothTables() throws Exception {
        execute(
                "CREATE TABLE posts (id INTEGER PRIMARY KEY AUTOINCREMENT, author TEXT NOT NULL, content TEXT NOT NULL, channel TEXT NOT NULL CHECK (channel IN ('home', 'tech', 'ops')), created_at TEXT NOT NULL DEFAULT (datetime('now')))",
                "CREATE INDEX idx_posts_timeline ON posts(created_at DESC, id DESC)",
                "CREATE INDEX idx_posts_channel_timeline ON posts(channel, created_at DESC, id DESC)",
                "INSERT INTO posts (id, author, content, channel) VALUES (41, 'bob', 'current post', 'tech')",
                "CREATE TABLE replies (id INTEGER PRIMARY KEY AUTOINCREMENT, post_id INTEGER NOT NULL REFERENCES posts(id) ON DELETE CASCADE, author TEXT NOT NULL, content TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT (datetime('now')))",
                "CREATE INDEX idx_replies_post_timeline ON replies(post_id, created_at ASC, id ASC)",
                "INSERT INTO replies (id, post_id, author, content) VALUES (17, 41, 'bob', 'a reply')");

        runMigration();

        List<String[]> history = queryHistory();
        assertThat(history).hasSize(3);
        assertThat(history.get(0)).containsExactly("1", "BASELINE", "<< Flyway Baseline >>", "1");
        assertThat(history.get(1)).containsExactly("2", "SQL", "V2__add_cursor_timeline_indexes.sql", "1");
        assertThat(history.get(2)).containsExactly("3", "SQL", "V3__add_post_replies.sql", "1");

        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, author, content, channel FROM posts")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong("id")).isEqualTo(41);
            assertThat(rs.getString("author")).isEqualTo("bob");
            assertThat(rs.getString("content")).isEqualTo("current post");
            assertThat(rs.getString("channel")).isEqualTo("tech");
            assertThat(rs.next()).isFalse();
        }

        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT id, post_id, author, content FROM replies")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong("id")).isEqualTo(17);
            assertThat(rs.getLong("post_id")).isEqualTo(41);
            assertThat(rs.getString("author")).isEqualTo("bob");
            assertThat(rs.getString("content")).isEqualTo("a reply");
            assertThat(rs.next()).isFalse();
        }

        assertThat(indexExists("idx_posts_timeline")).isTrue();
        assertThat(indexExists("idx_posts_channel_timeline")).isTrue();
        assertThat(indexExists("idx_replies_post_timeline")).isTrue();

        execute(
                "INSERT INTO posts (author, content, channel) VALUES ('next', 'after migration', 'tech')",
                "INSERT INTO replies (post_id, author, content) VALUES (41, 'next', 'after migration')");
        assertThat(queryLong("SELECT MAX(id) FROM posts")).isEqualTo(42);
        assertThat(queryLong("SELECT MAX(id) FROM replies")).isEqualTo(18);
    }

    @Test
    void unknownNonEmptySchemaFailsClosed() throws Exception {
        execute("CREATE TABLE foo (id INTEGER PRIMARY KEY)");

        assertThatThrownBy(this::runMigration).isInstanceOf(FlywayException.class);

        assertThat(tableExists("flyway_schema_history")).isFalse();
        assertThat(tableExists("foo")).isTrue();
    }
}
