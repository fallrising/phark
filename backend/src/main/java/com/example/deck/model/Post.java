package com.example.deck.model;

import java.time.Instant;

public record Post(
        long id,
        String author,
        String content,
        String channel,
        Instant createdAt) {
}