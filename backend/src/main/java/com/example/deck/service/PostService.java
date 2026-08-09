package com.example.deck.service;

import com.example.deck.dto.CreatePostRequest;
import com.example.deck.error.ApiErrorCode;
import com.example.deck.error.ApiException;
import com.example.deck.model.Post;
import com.example.deck.model.PostCursor;
import com.example.deck.model.PostPage;
import com.example.deck.repository.PostRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PostService {

    private static final Set<String> VALID_CHANNELS = Set.of("home", "tech", "ops");

    private final PostRepository postRepository;
    private final PostCursorCodec cursorCodec;

    public PostService(PostRepository postRepository, PostCursorCodec cursorCodec) {
        this.postRepository = postRepository;
        this.cursorCodec = cursorCodec;
    }

    public PostPage getPosts(String channel, int limit, String before) {
        return getPosts(channel, limit, before, null);
    }

    public PostPage getPosts(
            String channel,
            int limit,
            String before,
            Long viewerAccountId) {
        validateChannel(channel);
        validateLimit(limit);

        PostCursor beforeCursor = decodeCursor(before);
        return toPage(
                postRepository.findPage(channel, limit + 1, beforeCursor, viewerAccountId),
                limit);
    }

    public PostPage getPostsByAccountId(long accountId, int limit, String before) {
        return getPostsByAccountId(accountId, limit, before, null);
    }

    public PostPage getPostsByAccountId(
            long accountId,
            int limit,
            String before,
            Long viewerAccountId) {
        validateLimit(limit);

        PostCursor beforeCursor = decodeCursor(before);
        return toPage(
                postRepository.findPageByAccountId(
                        accountId, limit + 1, beforeCursor, viewerAccountId),
                limit);
    }

    private PostCursor decodeCursor(String before) {
        PostCursor beforeCursor = null;
        if (before != null) {
            try {
                beforeCursor = cursorCodec.decode(before);
            } catch (IllegalArgumentException exception) {
                throw new ApiException(ApiErrorCode.INVALID_CURSOR, exception);
            }
        }
        return beforeCursor;
    }

    private PostPage toPage(List<Post> fetchedItems, int limit) {
        boolean hasMore = fetchedItems.size() > limit;
        List<Post> items = hasMore
                ? List.copyOf(fetchedItems.subList(0, limit))
                : List.copyOf(fetchedItems);
        String nextCursor = hasMore
                ? cursorCodec.encode(toCursor(items.get(items.size() - 1)))
                : null;

        return new PostPage(items, nextCursor);
    }

    public Post createPost(long accountId, CreatePostRequest request) {
        return postRepository.insertOwned(
                accountId, request.content().trim(), request.channel());
    }

    @PostConstruct
    public void seedData() {
        if (postRepository.count() > 0) {
            return;
        }

        postRepository.insertSeed("Alice", "Welcome to Stream Deck! This is the home feed.", "home");
        postRepository.insertSeed("Bob", "Morning standup notes are posted here.", "home");
        postRepository.insertSeed("Carol", "Deployed the latest release to staging.", "home");

        postRepository.insertSeed("Dave", "Exploring Kotlin coroutines for our next service.", "tech");
        postRepository.insertSeed("Eve", "SQLite WAL mode gives us better concurrent reads.", "tech");
        postRepository.insertSeed("Frank", "Benchmarking JdbcClient vs raw JDBC templates.", "tech");

        postRepository.insertSeed("Grace", "On-call rotation starts tonight at 18:00.", "ops");
        postRepository.insertSeed("Henry", "Disk usage on node-3 is back to normal.", "ops");
        postRepository.insertSeed("Ivy", "Scheduled maintenance window confirmed for Sunday.", "ops");
    }

    private void validateChannel(String channel) {
        if (channel != null && !VALID_CHANNELS.contains(channel)) {
            throw new ApiException(ApiErrorCode.INVALID_CHANNEL);
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new ApiException(ApiErrorCode.INVALID_LIMIT);
        }
    }

    private PostCursor toCursor(Post post) {
        return new PostCursor(post.createdAt(), post.id());
    }
}
