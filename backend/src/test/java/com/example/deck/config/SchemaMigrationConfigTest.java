package com.example.deck.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
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

    private boolean hasUniqueIndex(String table, String... columns) throws Exception {
        List<String> uniqueIndexes = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("PRAGMA index_list(" + table + ")")) {
            while (rs.next()) {
                if (rs.getInt("unique") == 1) {
                    uniqueIndexes.add(rs.getString("name"));
                }
            }
        }
        for (String index : uniqueIndexes) {
            List<String> indexColumns = new ArrayList<>();
            try (Connection conn = DriverManager.getConnection(url);
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("PRAGMA index_info(" + index + ")")) {
                while (rs.next()) {
                    indexColumns.add(rs.getString("name"));
                }
            }
            if (List.of(columns).equals(indexColumns)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasIndex(String table, String... columns) throws Exception {
        List<String> target = List.of(columns);
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("PRAGMA index_list(" + table + ")")) {
            while (rs.next()) {
                String indexName = rs.getString("name");
                List<String> indexColumns = new ArrayList<>();
                try (Connection conn2 = DriverManager.getConnection(url);
                        Statement stmt2 = conn2.createStatement();
                        ResultSet rs2 = stmt2.executeQuery("PRAGMA index_info(" + indexName + ")")) {
                    while (rs2.next()) {
                        indexColumns.add(rs2.getString("name"));
                    }
                }
                if (target.equals(indexColumns)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void assertSqlRejected(String sql) {
        assertThatThrownBy(() -> execute(sql))
                .isInstanceOf(SQLException.class)
                .satisfies(t -> assertThat(t.getMessage()).isNotNull());
    }

    @Test
    void emptyDatabaseBootstrapsV1ThroughV7() throws Exception {
        runMigration();

        List<String[]> history = queryHistory();
        assertThat(history).hasSize(7);
        assertThat(history.get(0)).containsExactly("1", "SQL", "V1__create_legacy_posts.sql", "1");
        assertThat(history.get(1)).containsExactly("2", "SQL", "V2__add_cursor_timeline_indexes.sql", "1");
        assertThat(history.get(2)).containsExactly("3", "SQL", "V3__add_post_replies.sql", "1");
        assertThat(history.get(3))
                .containsExactly("4", "SQL", "V4__add_accounts_and_ownership.sql", "1");
        assertThat(history.get(4))
                .containsExactly("5", "SQL", "V5__add_post_likes.sql", "1");
        assertThat(history.get(5))
                .containsExactly("6", "SQL", "V6__add_post_reposts.sql", "1");
        assertThat(history.get(6))
                .containsExactly("7", "SQL", "V7__add_notifications.sql", "1");

        assertThat(tableExists("posts")).isTrue();
        assertThat(tableExists("replies")).isTrue();
        assertThat(tableExists("accounts")).isTrue();
        assertThat(tableExists("post_likes")).isTrue();
        assertThat(tableExists("post_reposts")).isTrue();
        assertThat(tableExists("notifications")).isTrue();
        assertThat(tableExists("notification_read_state")).isTrue();
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
        assertThat(columnExists("post_reposts", "id")).isTrue();
        assertThat(columnIsNullable("post_reposts", "post_id")).isFalse();
        assertThat(columnIsNullable("post_reposts", "account_id")).isFalse();
        assertThat(columnIsNullable("post_reposts", "created_at")).isFalse();
        assertThat(primaryKeyOrder("post_reposts", "id")).isEqualTo(1);
        assertThat(primaryKeyOrder("post_reposts", "post_id")).isZero();
        assertThat(primaryKeyOrder("post_reposts", "account_id")).isZero();
        assertThat(hasUniqueIndex("post_reposts", "post_id", "account_id")).isTrue();
        assertThat(columnExists("notifications", "id")).isTrue();
        assertThat(columnIsNullable("notifications", "recipient_account_id")).isFalse();
        assertThat(columnIsNullable("notifications", "actor_account_id")).isFalse();
        assertThat(columnIsNullable("notifications", "post_id")).isFalse();
        assertThat(columnIsNullable("notifications", "reply_id")).isTrue();
        assertThat(columnIsNullable("notifications", "type")).isFalse();
        assertThat(columnIsNullable("notifications", "created_at")).isFalse();
        assertThat(primaryKeyOrder("notifications", "id")).isEqualTo(1);
        assertThat(columnExists("notification_read_state", "account_id")).isTrue();
        assertThat(columnIsNullable("notification_read_state", "read_through_id")).isFalse();
        assertThat(columnIsNullable("notification_read_state", "updated_at")).isFalse();
        assertThat(primaryKeyOrder("notification_read_state", "account_id")).isEqualTo(1);

        assertThat(indexExists("idx_posts_timeline")).isTrue();
        assertThat(indexExists("idx_posts_channel_timeline")).isTrue();
        assertThat(indexExists("idx_posts_channel")).isFalse();
        assertThat(indexExists("idx_posts_created_at")).isFalse();
        assertThat(indexExists("idx_replies_post_timeline")).isTrue();
        assertThat(indexExists("idx_post_reposts_timeline")).isTrue();
        assertThat(indexExists("idx_post_reposts_account_timeline")).isTrue();
        assertThat(indexExists("idx_notifications_recipient_page")).isTrue();
        assertThat(hasIndex("notifications", "recipient_account_id", "id")).isTrue();
        assertThat(hasUniqueIndex("notifications", "reply_id")).isTrue();

        assertThat(fkExists("posts", "author_account_id", "accounts", "SET NULL")).isTrue();
        assertThat(fkExists("replies", "author_account_id", "accounts", "SET NULL")).isTrue();
        assertThat(fkExists("post_likes", "post_id", "posts", "CASCADE")).isTrue();
        assertThat(fkExists("post_likes", "account_id", "accounts", "CASCADE")).isTrue();
        assertThat(fkExists("post_reposts", "post_id", "posts", "CASCADE")).isTrue();
        assertThat(fkExists("post_reposts", "account_id", "accounts", "CASCADE")).isTrue();
        assertThat(fkExists("notifications", "recipient_account_id", "accounts", "CASCADE")).isTrue();
        assertThat(fkExists("notifications", "actor_account_id", "accounts", "CASCADE")).isTrue();
        assertThat(fkExists("notifications", "post_id", "posts", "CASCADE")).isTrue();
        assertThat(fkExists("notifications", "reply_id", "replies", "CASCADE")).isTrue();
        assertThat(fkExists("notification_read_state", "account_id", "accounts", "CASCADE")).isTrue();

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
        assertThat(queryLong("SELECT COUNT(*) FROM post_reposts")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM notifications")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM notification_read_state")).isZero();

        execute(
                "INSERT INTO accounts (id, handle, display_name, password_hash) VALUES (1, 'bob', 'Bob', 'hash')",
                "INSERT INTO accounts (id, handle, display_name, password_hash) VALUES (2, 'alice', 'Alice', 'hash')",
                "INSERT INTO posts (id, author, content, channel, author_account_id) VALUES (10, 'Bob', 'post', 'home', 1)",
                "INSERT INTO replies (id, post_id, author, content, author_account_id) VALUES (20, 10, 'Alice', 'reply', 2)",
                "INSERT INTO notifications (recipient_account_id, actor_account_id, post_id, reply_id, type) VALUES (1, 2, 10, 20, 'REPLY')",
                "INSERT INTO notifications (recipient_account_id, actor_account_id, post_id, type) VALUES (1, 2, 10, 'LIKE')",
                "INSERT INTO notifications (recipient_account_id, actor_account_id, post_id, type) VALUES (1, 2, 10, 'REPOST')");
        assertThat(queryLong("SELECT COUNT(*) FROM notifications")).isEqualTo(3);
        assertSqlRejected(
                "INSERT INTO notifications (recipient_account_id, actor_account_id, post_id, type) VALUES (1, 2, 10, 'REPLY')");
        assertSqlRejected(
                "INSERT INTO notifications (recipient_account_id, actor_account_id, post_id, reply_id, type) VALUES (1, 2, 10, 20, 'LIKE')");
        assertSqlRejected(
                "INSERT INTO notifications (recipient_account_id, actor_account_id, post_id, type) VALUES (1, 2, 10, 'UNKNOWN')");
        assertSqlRejected(
                "INSERT INTO notifications (recipient_account_id, actor_account_id, post_id, reply_id, type) VALUES (1, 2, 10, 20, 'REPLY')");
        execute(
                "INSERT INTO notification_read_state (account_id, read_through_id) VALUES (1, 0)",
                "INSERT INTO notification_read_state (account_id, read_through_id) VALUES (2, 5)");
        assertSqlRejected(
                "INSERT INTO notification_read_state (account_id, read_through_id) VALUES (2, -1)");

        execute("PRAGMA foreign_keys = ON", "DELETE FROM accounts WHERE id = 1");
        assertThat(queryLong("SELECT COUNT(*) FROM notifications")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM notification_read_state WHERE account_id = 1"))
                .isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM notification_read_state WHERE account_id = 2"))
                .isEqualTo(1);
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
        assertThat(history).hasSize(7);
        assertThat(history.get(0)).containsExactly("1", "BASELINE", "<< Flyway Baseline >>", "1");
        assertThat(history.get(1)).containsExactly("2", "SQL", "V2__add_cursor_timeline_indexes.sql", "1");
        assertThat(history.get(2)).containsExactly("3", "SQL", "V3__add_post_replies.sql", "1");
        assertThat(history.get(3))
                .containsExactly("4", "SQL", "V4__add_accounts_and_ownership.sql", "1");
        assertThat(history.get(4))
                .containsExactly("5", "SQL", "V5__add_post_likes.sql", "1");
        assertThat(history.get(5))
                .containsExactly("6", "SQL", "V6__add_post_reposts.sql", "1");
        assertThat(history.get(6))
                .containsExactly("7", "SQL", "V7__add_notifications.sql", "1");

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
        assertThat(tableExists("post_reposts")).isTrue();
        assertThat(queryLong("SELECT COUNT(*) FROM post_reposts")).isZero();
        assertThat(tableExists("notifications")).isTrue();
        assertThat(queryLong("SELECT COUNT(*) FROM notifications")).isZero();
        assertThat(tableExists("notification_read_state")).isTrue();

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
        assertThat(history).hasSize(7);
        assertThat(history.get(0)).containsExactly("1", "BASELINE", "<< Flyway Baseline >>", "1");
        assertThat(history.get(1)).containsExactly("2", "SQL", "V2__add_cursor_timeline_indexes.sql", "1");
        assertThat(history.get(2)).containsExactly("3", "SQL", "V3__add_post_replies.sql", "1");
        assertThat(history.get(3))
                .containsExactly("4", "SQL", "V4__add_accounts_and_ownership.sql", "1");
        assertThat(history.get(4))
                .containsExactly("5", "SQL", "V5__add_post_likes.sql", "1");
        assertThat(history.get(5))
                .containsExactly("6", "SQL", "V6__add_post_reposts.sql", "1");
        assertThat(history.get(6))
                .containsExactly("7", "SQL", "V7__add_notifications.sql", "1");

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
        assertThat(tableExists("post_reposts")).isTrue();
        assertThat(queryLong("SELECT COUNT(*) FROM post_reposts")).isZero();
        assertThat(tableExists("notifications")).isTrue();
        assertThat(queryLong("SELECT COUNT(*) FROM notifications")).isZero();
        assertThat(tableExists("notification_read_state")).isTrue();

        execute(
                "INSERT INTO posts (author, content, channel) VALUES ('next', 'after migration', 'tech')",
                "INSERT INTO replies (post_id, author, content) VALUES (41, 'next', 'after migration')");
        assertThat(queryLong("SELECT MAX(id) FROM posts")).isEqualTo(42);
        assertThat(queryLong("SELECT MAX(id) FROM replies")).isEqualTo(18);
    }

    @Test
    void v3DatabaseUpgradesToV7PreservingUnownedContentAndIds() throws Exception {
        runMigration("3");
        execute(
                "INSERT INTO posts (id, author, content, channel, created_at) VALUES (41, 'Alice', 'v3 post', 'ops', '2024-03-01 05:07:08')",
                "INSERT INTO replies (id, post_id, author, content, created_at) VALUES (17, 41, 'Bob', 'v3 reply', '2024-03-01 05:08:09')");

        runMigration();

        List<String[]> history = queryHistory();
        assertThat(history).hasSize(7);
        assertThat(history.get(2))
                .containsExactly("3", "SQL", "V3__add_post_replies.sql", "1");
        assertThat(history.get(3))
                .containsExactly("4", "SQL", "V4__add_accounts_and_ownership.sql", "1");
        assertThat(history.get(4))
                .containsExactly("5", "SQL", "V5__add_post_likes.sql", "1");
        assertThat(history.get(5))
                .containsExactly("6", "SQL", "V6__add_post_reposts.sql", "1");
        assertThat(history.get(6))
                .containsExactly("7", "SQL", "V7__add_notifications.sql", "1");
        assertThat(queryLong(
                        "SELECT COUNT(*) FROM posts WHERE id = 41 AND author_account_id IS NULL"))
                .isEqualTo(1);
        assertThat(queryLong(
                        "SELECT COUNT(*) FROM replies WHERE id = 17 AND author_account_id IS NULL"))
                .isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM post_likes")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM post_reposts")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM notifications")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM notification_read_state")).isZero();

        execute(
                "INSERT INTO posts (author, content, channel) VALUES ('next', 'after migration', 'ops')",
                "INSERT INTO replies (post_id, author, content) VALUES (41, 'next', 'after migration')");
        assertThat(queryLong("SELECT MAX(id) FROM posts")).isEqualTo(42);
        assertThat(queryLong("SELECT MAX(id) FROM replies")).isEqualTo(18);
    }

    @Test
    void v4DatabaseUpgradesToV7PreservingAccountsAndOwnedContent() throws Exception {
        runMigration("4");
        execute(
                "INSERT INTO accounts (id, handle, display_name, password_hash, bio, created_at, updated_at) VALUES (7, 'alice', 'Alice', 'hash', 'bio', '2024-03-04 05:06:07', '2024-03-04 05:06:07')",
                "INSERT INTO posts (id, author, content, channel, created_at, author_account_id) VALUES (41, 'Alice', 'owned post', 'ops', '2024-03-04 05:07:08', 7)",
                "INSERT INTO replies (id, post_id, author, content, created_at, author_account_id) VALUES (17, 41, 'Alice', 'owned reply', '2024-03-04 05:08:09', 7)");

        runMigration();

        List<String[]> history = queryHistory();
        assertThat(history).hasSize(7);
        assertThat(history.get(4))
                .containsExactly("5", "SQL", "V5__add_post_likes.sql", "1");
        assertThat(history.get(5))
                .containsExactly("6", "SQL", "V6__add_post_reposts.sql", "1");
        assertThat(history.get(6))
                .containsExactly("7", "SQL", "V7__add_notifications.sql", "1");
        assertThat(queryLong("SELECT COUNT(*) FROM accounts WHERE id = 7 AND handle = 'alice'")).isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM posts WHERE id = 41 AND author_account_id = 7")).isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM replies WHERE id = 17 AND author_account_id = 7")).isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM post_likes")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM post_reposts")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM notifications")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM notification_read_state")).isZero();
    }

    @Test
    void unknownNonEmptySchemaFailsClosed() throws Exception {
        execute("CREATE TABLE foo (id INTEGER PRIMARY KEY)");

        assertThatThrownBy(this::runMigration).isInstanceOf(FlywayException.class);

        assertThat(tableExists("flyway_schema_history")).isFalse();
        assertThat(tableExists("foo")).isTrue();
    }

    @Test
    void v5DatabaseUpgradesToV7PreservingAccountsPostsRepliesLikesAndIds() throws Exception {
        runMigration("5");
        execute(
                "INSERT INTO accounts (id, handle, display_name, password_hash, bio, created_at, updated_at) VALUES (7, 'alice', 'Alice', 'hash', 'bio', '2024-04-05 06:07:08', '2024-04-05 06:07:08')",
                "INSERT INTO posts (id, author, content, channel, created_at, author_account_id) VALUES (41, 'Alice', 'owned post', 'tech', '2024-04-05 06:08:09', 7)",
                "INSERT INTO replies (id, post_id, author, content, created_at, author_account_id) VALUES (17, 41, 'Bob', 'a reply', '2024-04-05 06:09:10', null)",
                "INSERT INTO post_likes (post_id, account_id, created_at) VALUES (41, 7, '2024-04-05 06:10:11')");

        runMigration();

        List<String[]> history = queryHistory();
        assertThat(history).hasSize(7);
        assertThat(history.get(5))
                .containsExactly("6", "SQL", "V6__add_post_reposts.sql", "1");
        assertThat(history.get(6))
                .containsExactly("7", "SQL", "V7__add_notifications.sql", "1");

        assertThat(queryLong("SELECT COUNT(*) FROM accounts WHERE id = 7 AND handle = 'alice'"))
                .isEqualTo(1);
        assertThat(queryLong(
                        "SELECT COUNT(*) FROM posts WHERE id = 41 AND author_account_id = 7 AND author = 'Alice'"))
                .isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM replies WHERE id = 17 AND post_id = 41"))
                .isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM post_likes WHERE post_id = 41 AND account_id = 7"))
                .isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM post_reposts")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM notifications")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM notification_read_state")).isZero();

        execute(
                "INSERT INTO posts (author, content, channel) VALUES ('next', 'after migration', 'tech')",
                "INSERT INTO replies (post_id, author, content) VALUES (41, 'next', 'after migration')",
                "INSERT INTO post_reposts (post_id, account_id) VALUES (41, 7)");
        assertThat(queryLong("SELECT MAX(id) FROM posts")).isEqualTo(42);
        assertThat(queryLong("SELECT MAX(id) FROM replies")).isEqualTo(18);
        assertThat(queryLong("SELECT MAX(id) FROM post_reposts")).isEqualTo(1);
    }

    @Test
    void v6DatabaseUpgradesToV7PreservingAccountsPostsRepliesLikesRepostsAndNoBackfill()
            throws Exception {
        runMigration("6");
        execute(
                "INSERT INTO accounts (id, handle, display_name, password_hash, bio, created_at, updated_at) VALUES (7, 'alice', 'Alice', 'hash', 'bio', '2024-05-06 07:08:09', '2024-05-06 07:08:09')",
                "INSERT INTO accounts (id, handle, display_name, password_hash, bio, created_at, updated_at) VALUES (8, 'bob', 'Bob', 'hash', 'bio', '2024-05-06 07:08:09', '2024-05-06 07:08:09')",
                "INSERT INTO posts (id, author, content, channel, created_at, author_account_id) VALUES (41, 'Alice', 'owned post', 'tech', '2024-05-06 07:09:10', 7)",
                "INSERT INTO replies (id, post_id, author, content, created_at, author_account_id) VALUES (17, 41, 'Bob', 'a reply', '2024-05-06 07:10:11', 8)",
                "INSERT INTO post_likes (post_id, account_id, created_at) VALUES (41, 8, '2024-05-06 07:11:12')",
                "INSERT INTO post_reposts (post_id, account_id, created_at) VALUES (41, 8, '2024-05-06 07:12:13')");

        runMigration();

        List<String[]> history = queryHistory();
        assertThat(history).hasSize(7);
        assertThat(history.get(6)).containsExactly("7", "SQL", "V7__add_notifications.sql", "1");

        assertThat(queryLong("SELECT COUNT(*) FROM accounts WHERE id IN (7, 8)")).isEqualTo(2);
        assertThat(queryLong("SELECT COUNT(*) FROM posts WHERE id = 41 AND author_account_id = 7"))
                .isEqualTo(1);
        assertThat(
                queryLong("SELECT COUNT(*) FROM replies WHERE id = 17 AND post_id = 41 AND author_account_id = 8"))
                .isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM post_likes WHERE post_id = 41 AND account_id = 8"))
                .isEqualTo(1);
        assertThat(queryLong("SELECT COUNT(*) FROM post_reposts WHERE post_id = 41 AND account_id = 8"))
                .isEqualTo(1);

        assertThat(queryLong("SELECT COUNT(*) FROM notifications")).isZero();
        assertThat(queryLong("SELECT COUNT(*) FROM notification_read_state")).isZero();
    }

}
