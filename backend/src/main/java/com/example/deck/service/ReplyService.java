package com.example.deck.service;

import com.example.deck.dto.CreateReplyRequest;
import com.example.deck.model.PostCursor;
import com.example.deck.model.Reply;
import com.example.deck.model.ReplyPage;
import com.example.deck.repository.PostRepository;
import com.example.deck.repository.ReplyRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReplyService {

    private final PostRepository postRepository;
    private final ReplyRepository replyRepository;
    private final PostCursorCodec cursorCodec;

    public ReplyService(
            PostRepository postRepository,
            ReplyRepository replyRepository,
            PostCursorCodec cursorCodec) {
        this.postRepository = postRepository;
        this.replyRepository = replyRepository;
        this.cursorCodec = cursorCodec;
    }

    public ReplyPage getReplies(long postId, int limit, String after) {
        validatePost(postId);
        validateLimit(limit);

        PostCursor afterCursor = null;
        if (after != null) {
            try {
                afterCursor = cursorCodec.decode(after);
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST, "Invalid cursor", exception);
            }
        }

        List<Reply> fetchedItems = replyRepository.findPage(postId, limit + 1, afterCursor);
        boolean hasMore = fetchedItems.size() > limit;
        List<Reply> items = hasMore
                ? List.copyOf(fetchedItems.subList(0, limit))
                : List.copyOf(fetchedItems);
        String nextCursor = hasMore
                ? cursorCodec.encode(toCursor(items.get(items.size() - 1)))
                : null;

        return new ReplyPage(items, nextCursor);
    }

    public Reply createReply(long postId, CreateReplyRequest request) {
        validatePost(postId);
        return replyRepository.insert(postId, request.author().trim(), request.content().trim());
    }

    private void validatePost(long postId) {
        if (postId <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Post id must be positive");
        }
        if (!postRepository.existsById(postId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        }
    }

    private void validateLimit(int limit) {
        if (limit < 1 || limit > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Limit must be between 1 and 100");
        }
    }

    private PostCursor toCursor(Reply reply) {
        return new PostCursor(reply.createdAt(), reply.id());
    }
}
