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

    private final JdbcClient jdbcClient;

    public PostRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Post> findPage(String channel, int fetchLimit, PostCursor before) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, author, content, channel, created_at FROM posts");
        List<String> predicates = new ArrayList<>();
        Map<String, Object> parameters = new HashMap<>();

        if (channel != null) {
            predicates.add("channel = :channel");
            parameters.put("channel", channel);
        }
        if (before != null) {
            predicates.add("""
                    (created_at < :beforeCreatedAt
                        OR (created_at = :beforeCreatedAt AND id < :beforeId))""");
            parameters.put(
                    "beforeCreatedAt",
                    LocalDateTime.ofInstant(before.createdAt(), ZoneOffset.UTC)
                            .format(SQLITE_DATETIME));
            parameters.put("beforeId", before.id());
        }
        if (!predicates.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", predicates));
        }

        sql.append(" ORDER BY created_at DESC, id DESC LIMIT :fetchLimit");
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

    public Optional<Post> findById(long id) {
        return jdbcClient
                .sql("SELECT id, author, content, channel, created_at FROM posts WHERE id = ?")
                .param(id)
                .query(this::mapPost)
                .optional();
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
                rs.getString("content"),
                rs.getString("channel"),
                instant);
    }
}
