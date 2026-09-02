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
import org.flywaydb.core.api.configuration.FluentConfiguration;
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
        runMigration(null);
    }

    private void runMigration(String target) {
        try (HikariDataSource dataSource = new HikariDataSource()) {
            dataSource.setJdbcUrl(url);
            dataSource.setDriverClassName("org.sqlite.JDBC");
            dataSource.setMaximumPoolSize(1);

            FluentConfiguration configuration = Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .baselineVersion("1")
                    .validateOnMigrate(true)
                    .validateMigrationNaming(true)
                    .cleanDisabled(true);
            if (target != null) {
                configuration.target(target);
            }

            new SchemaMigrationConfig().guardedLegacyBaseline().migrate(configuration.load());
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

    private boolean columnExists(String table, String column) throws Exception {
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean columnIsNullable(String table, String column) throws Exception {
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return rs.getInt("notnull") == 0;
                }
            }
        }
        throw new AssertionError("Missing column " + table + "." + column);
    }

    private boolean fkExists(
            String childTable,
            String childColumn,
            String parentTable,
            String onDelete)
            throws Exception {
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "PRAGMA foreign_key_list(" + childTable + ")")) {
            while (rs.next()) {
                if (childColumn.equals(rs.getString("from"))
                        && parentTable.equals(rs.getString("table"))
                        && onDelete.equals(rs.getString("on_delete"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private int primaryKeyOrder(String table, String column) throws Exception {
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
            while (rs.next()) {
                if (column.equals(rs.getString("name"))) {
                    return rs.getInt("pk");
                }
            }
        }
        throw new AssertionError("Missing column " + table + "." + column);
    }

    @Test
    void emptyDatabaseBootstrapsV1ThroughV5() throws Exception {
        runMigration();

        List<String[]> history = queryHistory();
        assertThat(history).hasSize(5);
        assertThat(history.get(0)).containsExactly("1", "SQL", "V1__create_legacy_posts.sql", "1");
        assertThat(history.get(1)).containsExactly("2", "SQL", "V2__add_cursor_timeline_indexes.sql", "1");
        assertThat(history.get(2)).containsExactly("3", "SQL", "V3__add_post_replies.sql", "1");
        assertThat(history.get(3))
                .containsExactly("4", "SQL", "V4__add_accounts_and_ownership.sql", "1");
        assertThat(history.get(4))
                .containsExactly("5", "SQL", "V5__add_post_likes.sql", "1");

        assertThat(tableExists("posts")).isTrue();
        assertThat(tableExists("replies")).isTrue();
        assertThat(tableExists("accounts")).isTrue();
        assertThat(tableExists("post_likes")).isTrue();
        assertThat(tableExists("flyway_schema_history")).isTrue();

        assertThat(columnExists("accounts", "id")).isTrue();
        assertThat(columnExists("accounts", "handle")).isTrue();
        assertThat(columnExists("accounts", "display_name")).isTrue();
        assertThat(columnExists("accounts", "bio")).isTrue();
        assertThat(columnExists("accounts", "password_hash")).isTrue();
        assertThat(columnExists("accounts", "created_at")).isTrue();
        assertThat(columnExists("accounts", "updated_at")).isTrue();
        assertThat(columnExists("posts", "author_account_id")).isTrue();
        assertThat(columnExists("replies", "author_account_id")).isTrue();
        assertThat(columnIsNullable("posts", "author_account_id")).isTrue();
        assertThat(columnIsNullable("replies", "author_account_id")).isTrue();
        assertThat(columnIsNullable("post_likes", "post_id")).isFalse();
        assertThat(columnIsNullable("post_likes", "account_id")).isFalse();
        assertThat(columnExists("post_likes", "created_at")).isTrue();
        assertThat(primaryKeyOrder("post_likes", "post_id")).isEqualTo(1);
        assertThat(primaryKeyOrder("post_likes", "account_id")).isEqualTo(2);
        assertThat(primaryKeyOrder("post_likes", "created_at")).isZero();

        assertThat(indexExists("idx_posts_timeline")).isTrue();
        assertThat(indexExists("idx_posts_channel_timeline")).isTrue();
        assertThat(indexExists("idx_posts_channel")).isFalse();
        assertThat(indexExists("idx_posts_created_at")).isFalse();
        assertThat(indexExists("idx_replies_post_timeline")).isTrue();

        assertThat(fkExists("posts", "author_account_id", "accounts", "SET NULL")).isTrue();
        assertThat(fkExists("replies", "author_account_id", "accounts", "SET NULL")).isTrue();
        assertThat(fkExists("post_likes", "post_id", "posts", "CASCADE")).isTrue();
        assertThat(fkExists("post_likes", "account_id", "accounts", "CASCADE")).isTrue();

        assertThat(indexExists("idx_posts_author_timeline")).isTrue();
        assertThat(indexExists("idx_replies_author")).isTrue();

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
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM accounts")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isZero();
        }
        assertThat(queryLong("SELECT COUNT(*) FROM post_likes")).isZero();
    }

    @Test
    void legacyPostsSchemaBaselinesAndPreservesData() throws Exception {
        execute(
                "CREATE TABLE posts (id INTEGER PRIMARY KEY AUTOINCREMENT, author TEXT NOT NULL, content TEXT NOT NULL, channel TEXT NOT NULL CHECK (channel IN ('home', 'tech', 'ops')), created_at TEXT NOT NULL DEFAULT (datetime('now')))",
                "CREATE INDEX idx_posts_channel ON posts(channel)",
                "CREATE INDEX idx_posts_created_at ON posts(created_at DESC)",
                "INSERT INTO posts (id, author, content, channel, created_at) VALUES (41, 'alice', 'legacy post', 'home', '2024-01-02 03:04:05')");

        runMigration();

        List<String[]> history = queryHistory();
        assertThat(history).hasSize(5);
        assertThat(history.get(0)).containsExactly("1", "BASELINE", "<< Flyway Baseline >>", "1");
        assertThat(history.get(1)).containsExactly("2", "SQL", "V2__add_cursor_timeline_indexes.sql", "1");
        assertThat(history.get(2)).containsExactly("3", "SQL", "V3__add_post_replies.sql", "1");
        assertThat(history.get(3))
                .containsExactly("4", "SQL", "V4__add_accounts_and_ownership.sql", "1");
        assertThat(history.get(4))
                .containsExactly("5", "SQL", "V5__add_post_likes.sql", "1");

        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("""
                        SELECT id, author, content, channel, created_at, author_account_id
                        FROM posts""")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong("id")).isEqualTo(41);
            assertThat(rs.getString("author")).isEqualTo("alice");
            assertThat(rs.getString("content")).isEqualTo("legacy post");
            assertThat(rs.getString("channel")).isEqualTo("home");
            assertThat(rs.getString("created_at")).isEqualTo("2024-01-02 03:04:05");
            assertThat(rs.getString("author_account_id")).isNull();
            assertThat(rs.next()).isFalse();
        }

        assertThat(indexExists("idx_posts_timeline")).isTrue();
        assertThat(indexExists("idx_posts_channel_timeline")).isTrue();
        assertThat(indexExists("idx_posts_channel")).isFalse();
        assertThat(indexExists("idx_posts_created_at")).isFalse();
        assertThat(indexExists("idx_replies_post_timeline")).isTrue();
        assertThat(tableExists("replies")).isTrue();
        assertThat(tableExists("accounts")).isTrue();
        assertThat(tableExists("post_likes")).isTrue();
        assertThat(queryLong("SELECT COUNT(*) FROM post_likes")).isZero();

        execute("INSERT INTO posts (author, content, channel) VALUES ('next', 'after migration', 'home')");
        assertThat(queryLong("SELECT MAX(id) FROM posts")).isEqualTo(42);
    }

    @Test
    void currentPreFlywaySchemaPreservesBothTables() throws Exception {
        execute(
                "CREATE TABLE posts (id INTEGER PRIMARY KEY AUTOINCREMENT, author TEXT NOT NULL, content TEXT NOT NULL, channel TEXT NOT NULL CHECK (channel IN ('home', 'tech', 'ops')), created_at TEXT NOT NULL DEFAULT (datetime('now')))",
                "CREATE INDEX idx_posts_timeline ON posts(created_at DESC, id DESC)",
                "CREATE INDEX idx_posts_channel_timeline ON posts(channel, created_at DESC, id DESC)",
                "INSERT INTO posts (id, author, content, channel, created_at) VALUES (41, 'bob', 'current post', 'tech', '2024-02-03 04:05:06')",
                "CREATE TABLE replies (id INTEGER PRIMARY KEY AUTOINCREMENT, post_id INTEGER NOT NULL REFERENCES posts(id) ON DELETE CASCADE, author TEXT NOT NULL, content TEXT NOT NULL, created_at TEXT NOT NULL DEFAULT (datetime('now')))",
                "CREATE INDEX idx_replies_post_timeline ON replies(post_id, created_at ASC, id ASC)",
                "INSERT INTO replies (id, post_id, author, content, created_at) VALUES (17, 41, 'bob', 'a reply', '2024-02-03 04:06:07')");

        runMigration();

        List<String[]> history = queryHistory();
        assertThat(history).hasSize(5);
        assertThat(history.get(0)).containsExactly("1", "BASELINE", "<< Flyway Baseline >>", "1");
        assertThat(history.get(1)).containsExactly("2", "SQL", "V2__add_cursor_timeline_indexes.sql", "1");
        assertThat(history.get(2)).containsExactly("3", "SQL", "V3__add_post_replies.sql", "1");
        assertThat(history.get(3))
                .containsExactly("4", "SQL", "V4__add_accounts_and_ownership.sql", "1");
        assertThat(history.get(4))
                .containsExactly("5", "SQL", "V5__add_post_likes.sql", "1");

        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("""
                        SELECT id, author, content, channel, created_at, author_account_id
                        FROM posts""")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong("id")).isEqualTo(41);
            assertThat(rs.getString("author")).isEqualTo("bob");
            assertThat(rs.getString("content")).isEqualTo("current post");
            assertThat(rs.getString("channel")).isEqualTo("tech");
            assertThat(rs.getString("created_at")).isEqualTo("2024-02-03 04:05:06");
            assertThat(rs.getString("author_account_id")).isNull();
            assertThat(rs.next()).isFalse();
        }

        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("""
                        SELECT id, post_id, author, content, created_at, author_account_id
                        FROM replies""")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getLong("id")).isEqualTo(17);
            assertThat(rs.getLong("post_id")).isEqualTo(41);
            assertThat(rs.getString("author")).isEqualTo("bob");
            assertThat(rs.getString("content")).isEqualTo("a reply");
            assertThat(rs.getString("created_at")).isEqualTo("2024-02-03 04:06:07");
            assertThat(rs.getString("author_account_id")).isNull();
            assertThat(rs.next()).isFalse();
        }

        assertThat(indexExists("idx_posts_timeline")).isTrue();
        assertThat(indexExists("idx_posts_channel_timeline")).isTrue();
        assertThat(indexExists("idx_replies_post_timeline")).isTrue();
        assertThat(tableExists("accounts")).isTrue();
        assertThat(tableExists("post_likes")).isTrue();
        assertThat(queryLong("SELECT COUNT(*) FROM post_likes")).isZero();

        execute(
                "INSERT INTO posts (author, content, channel) VALUES ('next', 'after migration', 'tech')",
                "INSERT INTO replies (post_id, author, content) VALUES (41, 'next', 'after migration')");
        assertThat(queryLong("SELECT MAX(id) FROM posts")).isEqualTo(42);
        assertThat(queryLong("SELECT MAX(id) FROM replies")).isEqualTo(18);
    }

    @Test
    void v4DatabaseUpgradesToV5PreservingAccountsAndOwnedContent() throws Exception {
        runMigration("4");
        execute(
                "INSERT INTO accounts (id, handle, display_name, password_hash, bio, created_at, updated_at) VALUES (7, 'alice', 'Alice', 'hash', 'bio', '2024-03-04 05:06:07', '2024-03-04 05:06:07')",
                "INSERT INTO posts (id, author, content, channel, created_at, author_account_id) VALUES (41, 'Alice', 'owned post', 'ops', '2024-03-04 05:07:08', 7)",
                "INSERT INTO replies (id, post_id, author, content, created_at, author_account_id) VALUES (17, 41, 'Alice', 'owned reply', '2024-03-04 05:08:09', 7)");

        runMigration();

        List<String[]> history = queryHistory();
        assertThat(history).hasSize(5);
        assertThat(history.get(4))
                .containsExactly("5", "SQL", "V5__add_post_likes.sql", "1");
        assertThat(queryLong("SELECT COUNT(*) FROM accounts WHERE id = 7 AND handle = 'alice'")).isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM posts WHERE id = 41 AND author_account_id = 7")).isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM replies WHERE id = 17 AND author_account_id = 7")).isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM post_likes")).isZero();
    }

    @Test
    void unknownNonEmptySchemaFailsClosed() throws Exception {
        execute("CREATE TABLE foo (id INTEGER PRIMARY KEY)");

        assertThatThrownBy(this::runMigration).isInstanceOf(FlywayException.class);

        assertThat(tableExists("flyway_schema_history")).isFalse();
        assertThat(tableExists("foo")).isTrue();
    }

}
