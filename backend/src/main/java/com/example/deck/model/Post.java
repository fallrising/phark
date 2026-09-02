package com.example.deck.model;

import java.time.Instant;

public record Post(
        long id,
        String author,
        String authorHandle,
        String content,
        String channel,
        Instant createdAt,
        long replyCount,
        long likeCount,
        boolean likedByViewer,
        String timelineEntryId,
        long repostCount,
        boolean repostedByViewer,
        String repostedBy,
        String repostedByHandle,
        Instant repostedAt) {}
