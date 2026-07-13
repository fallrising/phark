package com.example.deck.repository;

import com.example.deck.model.Post;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    public List<Post> findAll() {
        return jdbcClient
                .sql("SELECT id, author, content, channel, created_at FROM posts ORDER BY created_at DESC")
                .query(this::mapPost)
                .list();
    }

    public List<Post> findByChannel(String channel) {
        return jdbcClient
                .sql("SELECT id, author, content, channel, created_at FROM posts WHERE channel = ? ORDER BY created_at DESC")
                .param(channel)
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