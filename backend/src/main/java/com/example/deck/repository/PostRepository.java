package com.example.deck.repository;

import com.example.deck.model.Post;
import com.example.deck.model.PostCursor;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class PostRepository {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String POST_SELECT = """
            SELECT p.id, COALESCE(a.display_name, p.author) AS author,
                   a.handle AS author_handle, p.content, p.channel, p.created_at,
                   (SELECT COUNT(*) FROM replies r WHERE r.post_id = p.id) AS reply_count,
                   (SELECT COUNT(*) FROM post_likes pl WHERE pl.post_id = p.id) AS like_count,
                   %s AS liked_by_viewer
            FROM posts p
            LEFT JOIN accounts a ON a.id = p.author_account_id""";
    private static final String ANONYMOUS_LIKED = "0";
    private static final String VIEWER_LIKED = """
            EXISTS(
                SELECT 1 FROM post_likes viewer_like
                WHERE viewer_like.post_id = p.id
                  AND viewer_like.account_id = :viewerAccountId
            )""";

    private final JdbcClient jdbcClient;

    public PostRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Post> findPage(String channel, int fetchLimit, PostCursor before) {
        return findPage(channel, null, fetchLimit, before, null);
    }

    public List<Post> findPage(
            String channel,
            int fetchLimit,
            PostCursor before,
            Long viewerAccountId) {
        return findPage(channel, null, fetchLimit, before, viewerAccountId);
    }

    public List<Post> findPageByAccountId(
            long accountId,
            int fetchLimit,
            PostCursor before) {
        return findPage(null, accountId, fetchLimit, before, null);
    }

    public List<Post> findPageByAccountId(
            long accountId,
            int fetchLimit,
            PostCursor before,
            Long viewerAccountId) {
        return findPage(null, accountId, fetchLimit, before, viewerAccountId);
    }

    private List<Post> findPage(
            String channel,
            Long authorAccountId,
            int fetchLimit,
            PostCursor before,
            Long viewerAccountId) {
        String likedExpression = viewerAccountId == null ? ANONYMOUS_LIKED : VIEWER_LIKED;
        StringBuilder sql = new StringBuilder(POST_SELECT.formatted(likedExpression));
        List<String> predicates = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();

        if (viewerAccountId != null) {
            parameters.put("viewerAccountId", viewerAccountId);
        }

        if (channel != null) {
            predicates.add("p.channel = :channel");
            parameters.put("channel", channel);
        }
        if (authorAccountId != null) {
            predicates.add("p.author_account_id = :accountId");
            parameters.put("accountId", authorAccountId);
        }
        if (before != null) {
            predicates.add("""
                    (p.created_at < :beforeCreatedAt
                        OR (p.created_at = :beforeCreatedAt AND p.id < :beforeId))""");
            parameters.put(
                    "beforeCreatedAt",
                    LocalDateTime.ofInstant(before.createdAt(), ZoneOffset.UTC)
                            .format(SQLITE_DATETIME));
            parameters.put("beforeId", before.id());
        }
        if (!predicates.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", predicates));
        }

        sql.append(" ORDER BY p.created_at DESC, p.id DESC LIMIT :fetchLimit");
        parameters.put("fetchLimit", fetchLimit);

        return jdbcClient
                .sql(sql.toString())
                .params(parameters)
                .query(this::mapPost)
                .list();
    }

    public long count() {
        return jdbcClient
                .sql("SELECT COUNT(*) FROM posts")
                .query(Long.class)
                .single();
    }

    public Post insert(String author, String content, String channel) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient
                .sql("INSERT INTO posts (author, content, channel) VALUES (?, ?, ?)")
                .param(author)
                .param(content)
                .param(channel)
                .update(keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated post id");
        }

        return findById(key.longValue())
                .orElseThrow(() -> new IllegalStateException("Failed to load inserted post"));
    }

    public Post insertOwned(long accountId, String content, String channel) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        int inserted = jdbcClient
                .sql("""
                        INSERT INTO posts (author, content, channel, author_account_id)
                        SELECT display_name, :content, :channel, id
                        FROM accounts
                        WHERE id = :accountId""")
                .param("content", content)
                .param("channel", channel)
                .param("accountId", accountId)
                .update(keyHolder);

        if (inserted != 1) {
            throw new IllegalArgumentException("Account not found");
        }
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated post id");
        }
        return findById(key.longValue())
                .orElseThrow(() -> new IllegalStateException("Failed to load inserted post"));
    }

    public Optional<Post> findById(long id) {
        return jdbcClient
                .sql(POST_SELECT.formatted(ANONYMOUS_LIKED) + " WHERE p.id = :id")
                .param("id", id)
                .query(this::mapPost)
                .optional();
    }

    public boolean existsById(long id) {
        Long count = jdbcClient
                .sql("SELECT COUNT(*) FROM posts WHERE id = :id")
                .param("id", id)
                .query(Long.class)
                .single();
        return count > 0;
    }

    public void insertSeed(String author, String content, String channel) {
        jdbcClient
                .sql("INSERT INTO posts (author, content, channel) VALUES (?, ?, ?)")
                .param(author)
                .param(content)
                .param(channel)
                .update();
    }

    private Post mapPost(ResultSet rs, int rowNum) throws SQLException {
        String createdAt = rs.getString("created_at");
        Instant instant = LocalDateTime.parse(createdAt, SQLITE_DATETIME).toInstant(ZoneOffset.UTC);
        return new Post(
                rs.getLong("id"),
                rs.getString("author"),
                rs.getString("author_handle"),
                rs.getString("content"),
                rs.getString("channel"),
                instant,
                rs.getLong("reply_count"),
                rs.getLong("like_count"),
                rs.getBoolean("liked_by_viewer"));
    }
}
