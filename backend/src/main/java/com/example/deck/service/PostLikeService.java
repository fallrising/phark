package com.example.deck.service;

import com.example.deck.error.ApiErrorCode;
import com.example.deck.error.ApiException;
import com.example.deck.model.LikeState;
import com.example.deck.repository.PostLikeRepository;
import com.example.deck.repository.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PostLikeService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;

    public PostLikeService(
            PostRepository postRepository,
            PostLikeRepository postLikeRepository) {
        this.postRepository = postRepository;
        this.postLikeRepository = postLikeRepository;
    }

    @Transactional
    public LikeState like(long postId, long accountId) {
        validatePost(postId);
        postLikeRepository.like(postId, accountId);
        return postLikeRepository.getState(postId, accountId);
    }

    @Transactional
    public LikeState unlike(long postId, long accountId) {
        validatePost(postId);
        postLikeRepository.unlike(postId, accountId);
        return postLikeRepository.getState(postId, accountId);
    }

    private void validatePost(long postId) {
        if (postId <= 0) {
            throw new ApiException(ApiErrorCode.INVALID_POST_ID);
        }
        if (!postRepository.existsById(postId)) {
            throw new ApiException(ApiErrorCode.POST_NOT_FOUND);
        }
    }
}
