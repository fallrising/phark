package com.example.deck.repository;

import com.example.deck.model.PostCursor;
import com.example.deck.model.Reply;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

@Repository
public class ReplyRepository {

    private static final DateTimeFormatter SQLITE_DATETIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcClient jdbcClient;

    public ReplyRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<Reply> findPage(long postId, int fetchLimit, PostCursor after) {
        StringBuilder sql = new StringBuilder("""
                SELECT id, post_id, author, content, created_at
                FROM replies
                WHERE post_id = :postId""");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("postId", postId);

        if (after != null) {
            sql.append("""
                     AND (
                        created_at > :afterCreatedAt
                        OR (created_at = :afterCreatedAt AND id > :afterId)
                    )""");
            parameters.put(
                    "afterCreatedAt",
                    LocalDateTime.ofInstant(after.createdAt(), ZoneOffset.UTC)
                            .format(SQLITE_DATETIME));
            parameters.put("afterId", after.id());
        }

        sql.append(" ORDER BY created_at ASC, id ASC LIMIT :fetchLimit");
        parameters.put("fetchLimit", fetchLimit);

        return jdbcClient
                .sql(sql.toString())
                .params(parameters)
                .query(this::mapReply)
                .list();
    }

    public Reply insert(long postId, String author, String content) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcClient
                .sql("""
                        INSERT INTO replies (post_id, author, content)
                        VALUES (:postId, :author, :content)""")
                .param("postId", postId)
                .param("author", author)
                .param("content", content)
                .update(keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("Failed to retrieve generated reply id");
        }

        return findById(key.longValue())
                .orElseThrow(() -> new IllegalStateException("Failed to load inserted reply"));
    }

    public Optional<Reply> findById(long id) {
        return jdbcClient
                .sql("""
                        SELECT id, post_id, author, content, created_at
                        FROM replies
                        WHERE id = :id""")
                .param("id", id)
                .query(this::mapReply)
                .optional();
    }

    private Reply mapReply(ResultSet rs, int rowNum) throws SQLException {
        String createdAt = rs.getString("created_at");
        Instant instant = LocalDateTime.parse(createdAt, SQLITE_DATETIME)
                .toInstant(ZoneOffset.UTC);
        return new Reply(
                rs.getLong("id"),
                rs.getLong("post_id"),
                rs.getString("author"),
                rs.getString("content"),
                instant);
    }
}
