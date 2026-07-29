package com.example.deck.model;

import java.time.Instant;

public record Reply(
        long id,
        long postId,
        String author,
        String content,
        Instant createdAt) {
}
